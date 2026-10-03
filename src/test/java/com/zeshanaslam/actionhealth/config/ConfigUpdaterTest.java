package com.zeshanaslam.actionhealth.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    private static String defaults() throws IOException {
        try (InputStream stream = ConfigUpdaterTest.class.getClassLoader().getResourceAsStream("config.yml");
             Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    private static YamlConfiguration parse(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    // Removes a top level option and the indented lines below it, as in a config from an older version.
    private static String without(String text, String key) {
        List<String> kept = new ArrayList<>();
        boolean skipping = false;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(key + ":")) {
                skipping = true;
                continue;
            }
            if (skipping && (line.startsWith(" ") || line.startsWith("-"))) continue;
            skipping = false;
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    @Test
    void blocksMatchTheTopLevelOptionsOfTheDefaultConfig() throws Exception {
        String defaults = defaults();
        Set<String> blockKeys = ConfigUpdater.blocks(defaults).stream().map(block -> block.key).collect(Collectors.toSet());
        assertEquals(parse(defaults).getKeys(false), blockKeys);
    }

    @Test
    void addsNothingToACompleteConfig() throws Exception {
        String defaults = defaults();
        List<String> added = new ArrayList<>();
        assertEquals(defaults, ConfigUpdater.addMissing(defaults, defaults, parse(defaults).getKeys(false), "3.8.0", added));
        assertTrue(added.isEmpty());
    }

    @Test
    void addsMissingOptionsWithTheirCommentsAndKeepsTheRest() throws Exception {
        String defaults = defaults();
        String old = without(without(defaults, "Display Time"), "LookValues");
        List<String> added = new ArrayList<>();

        String updated = ConfigUpdater.addMissing(old, defaults, parse(old).getKeys(false), "3.8.0", added);

        assertEquals(java.util.Arrays.asList("Display Time", "LookValues"), added);
        assertTrue(updated.startsWith(old), "the existing text must not change");
        assertTrue(updated.contains("# Hides the action bar sooner"), "the comment of the option must be added");
        YamlConfiguration yaml = parse(updated);
        assertEquals(-1, yaml.getInt("Display Time"));
        assertEquals(2, yaml.getInt("LookValues.CheckTicks"));
        assertEquals(parse(defaults).getKeys(true), yaml.getKeys(true));
    }

    // Bukkit uses the config.yml in the jar as defaults. So a missing option already reads the default
    // value, and adding that value to the file does not change how the plugin works. The only
    // exceptions are options that the plugin reads with its own default: see KEEP_OLD_BEHAVIOR.
    @Test
    void addedValuesAreTheValuesThatTheServerAlreadyUsed() throws Exception {
        String defaults = defaults();
        YamlConfiguration jarDefaults = parse(defaults);
        for (String key : jarDefaults.getKeys(false)) {
            String old = without(defaults, key);
            YamlConfiguration before = parse(old);
            before.setDefaults(jarDefaults);
            YamlConfiguration after = parse(ConfigUpdater.addMissing(old, defaults, parse(old).getKeys(false), "3.8.0", new ArrayList<>()));

            if (ConfigUpdater.KEEP_OLD_BEHAVIOR.containsKey(key)) continue;
            assertTrue(before.contains(key), key);
            assertEquals(value(before, key), value(after, key), key);
        }
    }

    private static Object value(YamlConfiguration yaml, String key) {
        Object value = yaml.get(key);
        if (!(value instanceof ConfigurationSection)) return value;

        // Only the leaf values: nested sections are objects that are never equal.
        java.util.Map<String, Object> values = new java.util.TreeMap<>(((ConfigurationSection) value).getValues(true));
        values.values().removeIf(v -> v instanceof ConfigurationSection);
        return values;
    }

    @Test
    void absorptionIconIsAddedEmptySoTheLookDoesNotChange() throws Exception {
        String defaults = defaults();
        String old = without(defaults, "Absorption Icon");
        List<String> added = new ArrayList<>();

        String updated = ConfigUpdater.addMissing(old, defaults, parse(old).getKeys(false), "3.8.0", added);

        assertEquals("", parse(updated).getString("Absorption Icon"));
    }

    @Test
    void keepsWindowsLineEndings() throws Exception {
        String defaults = defaults();
        String old = without(defaults, "No Permission").replace("\n", "\r\n");
        List<String> added = new ArrayList<>();

        String updated = ConfigUpdater.addMissing(old, defaults, parse(old).getKeys(false), "3.8.0", added);

        assertEquals(java.util.Collections.singletonList("No Permission"), added);
        assertFalse(updated.replace("\r\n", "").contains("\n"), "every new line must use \\r\\n");
    }

    @Test
    void addsAFileWithoutAFinalNewlineOnANewLine() throws Exception {
        String defaults = defaults();
        String old = "Health Message: 'x'";
        Set<String> present = new HashSet<>(parse(old).getKeys(false));
        String updated = ConfigUpdater.addMissing(old, defaults, present, "3.8.0", new ArrayList<>());
        assertEquals("x", parse(updated).getString("Health Message"));
    }
}
