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
 * The players who turned ActionHealth off or on with /actionhealth toggle.
 * <p>
 * Players who made no choice get "Enabled By Default". Both choices are kept, not only the ones
 * that differ from the default, so a change of the default does not turn a saved choice around.
 * <p>
 * With "Remember Toggle", the lists are saved in toggles.yml and read once at startup.
 * Versions before 3.8.0 saved one file per player in the players folder. That folder is
 * moved into toggles.yml the first time this version starts.
 */
public class ToggleStore {

    private static final String KEY = "toggled";
    private static final String ENABLED_KEY = "enabled";

    private final File file;
    private final File legacyFolder;
    private final Logger logger;
    // Thread safe, because Folia runs commands and events on many threads.
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private volatile boolean enabledByDefault = true;

    public ToggleStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "toggles.yml");
        this.legacyFolder = new File(dataFolder, "players");
        this.logger = logger;
    }

    /**
     * Returns true if ActionHealth is off for the player.
     */
    public boolean isToggled(UUID uuid) {
        if (toggled.contains(uuid)) return true;
        if (enabled.contains(uuid)) return false;
        return !enabledByDefault;
    }

    /**
     * Sets whether players who made no choice see health messages ("Enabled By Default").
     */
    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    /**
     * Turns ActionHealth off (true) or on (false) for a player. Saves the lists if remember is true.
     */
    public void setToggled(UUID uuid, boolean off, boolean remember) {
        boolean changed;
        if (off) {
            changed = toggled.add(uuid) | enabled.remove(uuid);
        } else {
            changed = toggled.remove(uuid) | enabled.add(uuid);
        }
        if (changed && remember) save();
    }

    /**
     * Forgets the choice of a player who left, when "Remember Toggle" is off.
     */
    public void forget(UUID uuid) {
        toggled.remove(uuid);
        enabled.remove(uuid);
    }

    /**
     * Moves the old players folder into toggles.yml, then reads the saved list if remember is true.
     */
    public synchronized void load(boolean remember) {
        migrateLegacyFolder();
        if (!remember || !file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        toggled.addAll(read(yaml, KEY));
        for (UUID uuid : read(yaml, ENABLED_KEY)) {
            // A player in both lists (edited by hand) counts as off.
            if (!toggled.contains(uuid)) enabled.add(uuid);
        }
    }

    public synchronized void save() {
        write(toggled, enabled);
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

        if (!write(saved, Collections.<UUID>emptySet())) return;
        File renamed = new File(legacyFolder.getParentFile(), "players-old");
        if (legacyFolder.renameTo(renamed)) {
            logger.info("Moved " + moved + " saved toggle choices from the players folder to toggles.yml. "
                    + "The old folder is now players-old and can be deleted.");
        } else {
            logger.warning("Moved the saved toggle choices to toggles.yml, but could not rename the players folder.");
        }
    }

    private Set<UUID> read(YamlConfiguration yaml, String key) {
        Set<UUID> result = new HashSet<>();
        for (String value : yaml.getStringList(key)) {
            UUID uuid = parse(value);
            if (uuid != null) {
                result.add(uuid);
            } else {
                logger.warning("toggles.yml: '" + value + "' is not a player UUID. It was skipped.");
            }
        }

        return result;
    }

    private boolean write(Set<UUID> off, Set<UUID> on) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Players who turned ActionHealth off (toggled) or on (enabled) with /actionhealth toggle. "
                + "Used when 'Remember Toggle' is true.");
        yaml.set(KEY, sorted(off));
        yaml.set(ENABLED_KEY, sorted(on));
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file.getName(), e);
            return false;
        }
    }

    private static List<String> sorted(Set<UUID> uuids) {
        List<String> values = new ArrayList<>();
        for (UUID uuid : uuids) values.add(uuid.toString());
        Collections.sort(values);
        return values;
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
