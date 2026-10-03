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
import java.nio.file.StandardOpenOption;
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
 * <p>
 * The new text is appended as bytes, and the existing bytes are never written again. Bukkit 1.8 reads
 * the file in the charset of the system (for example windows-1252), so reading and writing the whole
 * file as UTF-8 would damage accented letters. The default config is ASCII, which is the same in
 * every such charset.
 * <p>
 * Only top level options are added. A new option inside an existing section (for example
 * LookValues) does not reach old configs: add new options at the top level.
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

    /**
     * Options whose added value changes what the plugin does. The header of the added options names them.
     */
    static final List<String> NEW_BEHAVIOR = Collections.singletonList("Update Check");

    private ConfigUpdater() {
    }

    /**
     * Adds the missing options to plugins/ActionHealth/config.yml. Returns the names of the added options.
     */
    public static List<String> update(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null || !file.exists()) return Collections.emptyList();

            return update(file, new String(readAll(stream), StandardCharsets.UTF_8), plugin.getDescription().getVersion());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not add new options to config.yml", e);
            return Collections.emptyList();
        }
    }

    /**
     * Appends the options of the default config that the file does not have. Returns their names.
     */
    static List<String> update(File file, String defaults, String version) throws IOException {
        // Only to find the options and the line endings. Letters that are not UTF-8 become
        // replacement characters here, but this text is never written back.
        String current = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(current);
        } catch (InvalidConfigurationException e) {
            // Bukkit logs the YAML error when it loads the config. Do not add to a broken file.
            return Collections.emptyList();
        }

        List<String> added = new ArrayList<>();
        String addition = addition(current, defaults, yaml.getKeys(false), version, added);
        if (!added.isEmpty()) {
            Files.write(file.toPath(), addition.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        }

        return added;
    }

    /**
     * Returns the current config text with the missing top level options of the default config
     * added at the end. Adds the names of the added options to the added list.
     */
    static String addMissing(String current, String defaults, Set<String> present, String version, List<String> added) {
        return current + addition(current, defaults, present, version, added);
    }

    /**
     * Returns the text to append to the current config: the missing top level options of the
     * default config, or "" if none is missing. Adds the names of the added options to the added list.
     */
    static String addition(String current, String defaults, Set<String> present, String version, List<String> added) {
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

        if (added.isEmpty()) return "";

        List<String> changed = new ArrayList<>();
        for (String key : added) {
            if (NEW_BEHAVIOR.contains(key)) changed.add(key);
        }

        StringBuilder result = new StringBuilder();
        if (current.length() > 0 && !current.endsWith("\n")) result.append(newline);
        result.append(newline)
                .append("# Options added by ActionHealth ").append(version)
                .append(changed.isEmpty() ? ". Their values give the same result as before the update."
                        : ". Their values give the same result as before the update, except " + String.join(", ", changed) + ".")
                .append(newline)
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
