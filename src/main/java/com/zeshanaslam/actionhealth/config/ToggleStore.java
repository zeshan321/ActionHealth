package com.zeshanaslam.actionhealth.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The players who turned ActionHealth off with /actionhealth toggle.
 * <p>
 * With "Remember Toggle", the list is saved in toggles.yml and read once at startup.
 * Versions before 3.8.0 saved one file per player in the players folder. That folder is
 * moved into toggles.yml the first time this version starts.
 */
public class ToggleStore {

    private static final String KEY = "toggled";

    private final File file;
    private final File legacyFolder;
    private final Logger logger;
    // Thread safe, because Folia runs commands and events on many threads.
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public ToggleStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "toggles.yml");
        this.legacyFolder = new File(dataFolder, "players");
        this.logger = logger;
    }

    public boolean isToggled(UUID uuid) {
        return toggled.contains(uuid);
    }

    /**
     * Turns ActionHealth off (true) or on (false) for a player. Saves the list if remember is true.
     */
    public void setToggled(UUID uuid, boolean off, boolean remember) {
        boolean changed = off ? toggled.add(uuid) : toggled.remove(uuid);
        if (changed && remember) save();
    }

    /**
     * Forgets the choice of a player who left, when "Remember Toggle" is off.
     */
    public void forget(UUID uuid) {
        toggled.remove(uuid);
    }

    /**
     * Moves the old players folder into toggles.yml, then reads the saved list if remember is true.
     */
    public synchronized void load(boolean remember) {
        migrateLegacyFolder();
        if (!remember || !file.exists()) return;

        toggled.addAll(readFile());
    }

    public synchronized void save() {
        write(toggled);
    }

    private void migrateLegacyFolder() {
        // Only once: after that, toggles.yml has the current choices. An old folder that comes
        // back (for example after a downgrade) must not turn ActionHealth off again for a player.
        if (file.exists()) return;

        File[] files = legacyFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        Set<UUID> saved = new HashSet<>();
        int moved = 0;
        for (File playerFile : files) {
            UUID uuid = parse(playerFile.getName().substring(0, playerFile.getName().length() - ".yml".length()));
            if (uuid != null && YamlConfiguration.loadConfiguration(playerFile).getBoolean("toggle")) {
                saved.add(uuid);
                moved++;
            }
        }

        if (!write(saved)) return;
        File renamed = new File(legacyFolder.getParentFile(), "players-old");
        if (legacyFolder.renameTo(renamed)) {
            logger.info("Moved " + moved + " saved toggle choices from the players folder to toggles.yml. "
                    + "The old folder is now players-old and can be deleted.");
        } else {
            logger.warning("Moved the saved toggle choices to toggles.yml, but could not rename the players folder.");
        }
    }

    private Set<UUID> readFile() {
        Set<UUID> result = new HashSet<>();
        if (!file.exists()) return result;

        for (String value : YamlConfiguration.loadConfiguration(file).getStringList(KEY)) {
            UUID uuid = parse(value);
            if (uuid != null) {
                result.add(uuid);
            } else {
                logger.warning("toggles.yml: '" + value + "' is not a player UUID. It was skipped.");
            }
        }

        return result;
    }

    private boolean write(Set<UUID> uuids) {
        List<String> values = new ArrayList<>();
        for (UUID uuid : uuids) values.add(uuid.toString());
        Collections.sort(values);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Players who turned ActionHealth off with /actionhealth toggle. "
                + "Used when 'Remember Toggle' is true.");
        yaml.set(KEY, values);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file.getName(), e);
            return false;
        }
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
