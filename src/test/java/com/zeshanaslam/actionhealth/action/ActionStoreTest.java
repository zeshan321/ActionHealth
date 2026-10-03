package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.action.data.Tagged;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionStoreTest {

    private static final Logger LOGGER = Logger.getLogger("ActionStoreTest");
    private final UUID damager = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    private static YamlConfiguration defaults() throws Exception {
        try (InputStream stream = ActionStoreTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static ActionStore store(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new ActionStore(null, config, LOGGER);
    }

    private List<UUID> targets(ActionStore store) {
        return store.tagged.get(damager).stream().map(tag -> tag.damaged).collect(Collectors.toList());
    }

    @Test
    void theDefaultConfigUsesTheAnyDamageCause() throws Exception {
        assertTrue(new ActionStore(null, defaults(), LOGGER).isUsingAnyDamageCause);
    }

    @Test
    void aMessageThatSaysAnyIsNotTheAnyDamageCause() throws Exception {
        ActionStore store = store("Action:\n  Events:\n    DAMAGE:\n      LAVA: 'any'\n");
        assertFalse(store.isUsingAnyDamageCause);
    }

    @Test
    void unknownEventTypesAreSkipped() throws Exception {
        ActionStore store = store("Action:\n  Events:\n    JUMP:\n      ANY: 'x'\n    consume:\n      GOLDEN_APPLE: 'y'\n");
        assertEquals(1, store.events.size());
        assertEquals("GOLDEN_APPLE", store.events.get(ActionStore.ActionType.CONSUME).get(0).material);
    }

    @Test
    void hittingTheSameTargetAgainKeepsOneTag() throws Exception {
        ActionStore store = store("Action:\n  TagAmount: 2\n");
        store.addTag(damager, first);
        store.addTag(damager, second);
        store.addTag(damager, first);
        store.addTag(damager, first);

        // Both targets stay tagged. The target that was hit last is the newest.
        assertEquals(java.util.Arrays.asList(second, first), targets(store));
    }

    @Test
    void theOldestTargetMakesRoomForANewOne() throws Exception {
        ActionStore store = store("Action:\n  TagAmount: 1\n");
        store.addTag(damager, first);
        store.addTag(damager, second);
        assertEquals(java.util.Collections.singletonList(second), targets(store));
    }

    @Test
    void unlimitedTagsDoNotGrowWithEachHit() throws Exception {
        ActionStore store = store("Action:\n  TagAmount: -1\n");
        for (int i = 0; i < 50; i++) store.addTag(damager, first);
        assertEquals(1, store.tagged.get(damager).size());
    }

    @Test
    void aPlayerCanNotTagThemself() throws Exception {
        ActionStore store = store("Action:\n  TagAmount: 2\n");
        store.addTag(damager, damager);
        assertNull(store.tagged.get(damager));
    }

    @Test
    void tagsExpireAfterTheTagLength() throws Exception {
        ActionStore store = store("Action:\n  TagLength: 20\n  TagAmount: 2\n");
        store.addTag(damager, first);
        Tagged tag = store.tagged.get(damager).get(0);

        store.expire(tag.timestamp + 19_000);
        assertEquals(1, store.tagged.get(damager).size());

        store.expire(tag.timestamp + 21_000);
        assertNull(store.tagged.get(damager), "a player without tags is removed");
    }

    @Test
    void removingADeadTargetRemovesItsTags() throws Exception {
        ActionStore store = store("Action:\n  TagAmount: 2\n");
        store.addTag(damager, first);
        store.addTag(damager, second);
        store.remove(first);
        assertEquals(java.util.Collections.singletonList(second), targets(store));
    }
}
