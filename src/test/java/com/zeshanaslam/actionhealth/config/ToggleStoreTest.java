package com.zeshanaslam.actionhealth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleStoreTest {

    private static final Logger LOGGER = Logger.getLogger("ToggleStoreTest");
    private static final UUID PLAYER = UUID.fromString("5b9c2e5a-0e0e-4c55-9d3b-2f0c4d1a7e11");
    private static final UUID OTHER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @TempDir
    File folder;

    @Test
    void remembersTheChoiceAfterARestart() {
        new ToggleStore(folder, LOGGER).setToggled(PLAYER, true, true);

        ToggleStore restarted = new ToggleStore(folder, LOGGER);
        restarted.load(true);
        assertTrue(restarted.isToggled(PLAYER));
        assertFalse(restarted.isToggled(OTHER));
    }

    @Test
    void doesNotSaveWithoutRememberToggle() {
        new ToggleStore(folder, LOGGER).setToggled(PLAYER, true, false);
        assertFalse(new File(folder, "toggles.yml").exists());
    }

    @Test
    void doesNotLoadWithoutRememberToggle() {
        new ToggleStore(folder, LOGGER).setToggled(PLAYER, true, true);

        ToggleStore restarted = new ToggleStore(folder, LOGGER);
        restarted.load(false);
        assertFalse(restarted.isToggled(PLAYER));
    }

    @Test
    void turningBackOnRemovesTheSavedChoice() {
        ToggleStore store = new ToggleStore(folder, LOGGER);
        store.setToggled(PLAYER, true, true);
        store.setToggled(PLAYER, false, true);

        ToggleStore restarted = new ToggleStore(folder, LOGGER);
        restarted.load(true);
        assertFalse(restarted.isToggled(PLAYER));
    }

    @Test
    void movesTheOldPlayersFolder() throws IOException {
        File players = new File(folder, "players");
        players.mkdirs();
        write(new File(players, PLAYER + ".yml"), "toggle: true\n");
        write(new File(players, OTHER + ".yml"), "toggle: false\n");
        write(new File(players, "not-a-uuid.yml"), "toggle: true\n");

        ToggleStore store = new ToggleStore(folder, LOGGER);
        store.load(true);

        assertTrue(store.isToggled(PLAYER));
        assertFalse(store.isToggled(OTHER));
        assertFalse(players.exists());
        assertTrue(new File(folder, "players-old/" + PLAYER + ".yml").exists());
        assertTrue(new String(Files.readAllBytes(new File(folder, "toggles.yml").toPath()), StandardCharsets.UTF_8).contains(PLAYER.toString()));
    }

    @Test
    void ignoresAnOldPlayersFolderWhenTogglesYmlExists() throws IOException {
        // The player turned ActionHealth on again after the move. An old folder must not undo that.
        write(new File(folder, "toggles.yml"), "toggled: []\n");
        File players = new File(folder, "players");
        players.mkdirs();
        write(new File(players, PLAYER + ".yml"), "toggle: true\n");

        ToggleStore store = new ToggleStore(folder, LOGGER);
        store.load(true);
        assertFalse(store.isToggled(PLAYER));
    }

    @Test
    void skipsLinesThatAreNotUuids() throws IOException {
        write(new File(folder, "toggles.yml"), "toggled:\n- nonsense\n- " + PLAYER + "\n");

        ToggleStore store = new ToggleStore(folder, LOGGER);
        store.load(true);
        assertTrue(store.isToggled(PLAYER));
    }

    private static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
