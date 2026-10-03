package com.zeshanaslam.actionhealth.utils;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetHelperTest {

    private static double entry(Vector origin, Vector direction) {
        // The block from (5, 0, 0) to (6, 1, 1).
        return TargetHelper.rayBoxEntry(origin, direction.normalize(), 5, 0, 0, 6, 1, 1);
    }

    @Test
    void straightRayEntersAtTheNearSide() {
        assertEquals(4.5, entry(new Vector(0.5, 0.5, 0.5), new Vector(1, 0, 0)), 1e-9);
    }

    @Test
    void downwardRayEntersAtTheTopSide() {
        // Straight down onto the top side of the block.
        Vector origin = new Vector(5.5, 3.5, 0.5);
        double distance = entry(origin, new Vector(0, -1, 0));
        assertEquals(2.5, distance, 1e-9);
    }

    @Test
    void diagonalRayEntersAtTheCorrectDistance() {
        // It reaches x = 5 after 4.5 blocks along x, at z = 0.75, which is on the near side of the block.
        double expected = Math.sqrt(4.5 * 4.5 + 0.25 * 0.25);
        assertEquals(expected, entry(new Vector(0.5, 0.5, 0.5), new Vector(4.5, 0, 0.25)), 1e-9);
    }

    @Test
    void rayThatPassesNextToTheBlockMisses() {
        assertEquals(-1, entry(new Vector(0.5, 1.5, 0.5), new Vector(1, 0, 0)), 1e-9);
        assertEquals(-1, entry(new Vector(0.5, 0.5, 0.5), new Vector(1, 1, 0)), 1e-9);
    }

    @Test
    void rayThatPointsAwayMisses() {
        assertEquals(-1, entry(new Vector(0.5, 0.5, 0.5), new Vector(-1, 0, 0)), 1e-9);
    }

    @Test
    void rayThatStartsInsideReturnsZero() {
        assertEquals(0, entry(new Vector(5.5, 0.5, 0.5), new Vector(1, 0, 0)), 1e-9);
    }
}
