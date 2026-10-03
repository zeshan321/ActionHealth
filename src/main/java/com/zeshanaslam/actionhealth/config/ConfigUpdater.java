package com.zeshanaslam.actionhealth.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Adds options from new versions to an existing config.yml, so server owners can find them.
 * <p>
 * Each missing top level option is added at the end of the file, with its comment from the
 * default config. The rest of the file does not change, so the comments of the server owner stay.
 * Bukkit cannot save comments before 1.18, which is why the file is changed as text.
 */
public final class ConfigUpdater {

    /**
     * Options that get a different value than the default config, so the plugin works as before.
     * Before 3.7.0 there were no absorption icons.
     */
    static final Map<String, String> KEEP_OLD_BEHAVIOR;

    static {
        Map<String, String> values = new HashMap<>();
        values.put("Absorption Icon", "\"\"");
        KEEP_OLD_BEHAVIOR = Collections.unmodifiableMap(values);
    }

    private ConfigUpdater() {
    }

    /**
     * Adds the missing options to plugins/ActionHealth/config.yml. Returns the names of the added options.
     */
    public static List<String> update(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null || !file.exists()) return Collections.emptyList();

            String defaults = new String(readAll(stream), StandardCharsets.UTF_8);
            String current = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.loadFromString(current);
            } catch (InvalidConfigurationException e) {
                // Bukkit logs the YAML error when it loads the config. Do not add to a broken file.
                return Collections.emptyList();
            }

            List<String> added = new ArrayList<>();
            String updated = addMissing(current, defaults, yaml.getKeys(false), plugin.getDescription().getVersion(), added);
            if (!added.isEmpty()) {
                Files.write(file.toPath(), updated.getBytes(StandardCharsets.UTF_8));
            }

            return added;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not add new options to config.yml", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns the current config text with the missing top level options of the default config
     * added at the end. Adds the names of the added options to the added list.
     */
    static String addMissing(String current, String defaults, Set<String> present, String version, List<String> added) {
        String newline = current.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder text = new StringBuilder();
        for (Block block : blocks(defaults)) {
            if (present.contains(block.key)) continue;

            added.add(block.key);
            text.append(newline);
            for (String line : block.lines) {
                text.append(line).append(newline);
            }
        }

        if (added.isEmpty()) return current;

        StringBuilder result = new StringBuilder(current);
        if (current.length() > 0 && !current.endsWith("\n")) result.append(newline);
        result.append(newline)
                .append("# Options added by ActionHealth ").append(version)
                .append(". Their values give the same result as before the update.").append(newline)
                .append(text);
        return result.toString();
    }

    /**
     * Splits the default config into its top level options. Each block has the comment lines
     * directly above the option, the option line and the indented lines below it.
     */
    static List<Block> blocks(String defaults) {
        List<Block> blocks = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        Block block = null;

        for (String raw : defaults.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;

            if (line.trim().isEmpty()) {
                block = null;
                comments.clear();
            } else if (line.startsWith("#")) {
                block = null;
                comments.add(line);
            } else if (Character.isWhitespace(line.charAt(0)) || line.startsWith("-")) {
                if (block != null) block.lines.add(line);
            } else {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;

                String key = unquote(line.substring(0, colon).trim());
                String keep = KEEP_OLD_BEHAVIOR.get(key);
                block = new Block(key);
                block.lines.addAll(comments);
                block.lines.add(keep == null ? line : line.substring(0, colon) + ": " + keep);
                blocks.add(block);
                comments.clear();
            }
        }

        return blocks;
    }

    private static String unquote(String key) {
        if (key.length() >= 2 && (key.charAt(0) == '\'' || key.charAt(0) == '"') && key.charAt(key.length() - 1) == key.charAt(0)) {
            return key.substring(1, key.length() - 1);
        }

        return key;
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    static final class Block {
        final String key;
        final List<String> lines = new ArrayList<>();

        Block(String key) {
            this.key = key;
        }
    }
}
