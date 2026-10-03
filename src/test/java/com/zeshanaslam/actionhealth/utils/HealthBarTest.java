package com.zeshanaslam.actionhealth.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthBarTest {

    // F = full, H = half, E = empty, A = absorption.
    private static String bar(double health, double maxHealth, int icons, boolean dead, double absorption, String absorptionIcon) {
        return HealthBar.build(health, maxHealth, icons, dead, "F", "H", "E", absorption, absorptionIcon);
    }

    private static String bar(double health, double maxHealth, int icons) {
        return bar(health, maxHealth, icons, false, 0, "");
    }

    @Test
    void fullHealthShowsOnlyFullIcons() {
        assertEquals("FFFFFFFFFF", bar(20, 20, 10));
    }

    @Test
    void oddHealthEndsWithHalfIcon() {
        assertEquals("FFFFFFFFFH", bar(19, 20, 10));
    }

    @Test
    void missingHeartsAreEmpty() {
        assertEquals("FFFFFFFFFE", bar(18, 20, 10));
        assertEquals("FFFFFEEEEE", bar(10, 20, 10));
        assertEquals("HEEEEEEEEE", bar(1, 20, 10));
    }

    @Test
    void noHealthOrDeadShowsOnlyEmptyIcons() {
        assertEquals("EEEEEEEEEE", bar(0, 20, 10));
        assertEquals("EEEEEEEEEE", bar(5, 20, 10, true, 0, ""));
    }

    @Test
    void limitHealthScalesLargeHealth() {
        // 100 max health in 10 icons: each icon is 10 health.
        assertEquals("FFFFFEEEEE", bar(50, 100, 10));
        assertEquals("FFFFFHEEEE", bar(55, 100, 10));
    }

    @Test
    void absorptionAddsOneIconPerHeart() {
        // A cow: 10 health in 10 icons, so each icon is 1 health. 8 absorption is 8 icons.
        assertEquals("FFFFFFFFFFAAAAAAAA", bar(10, 10, 10, false, 8, "A"));
        // A player: each icon is 2 health. 3 absorption rounds up to 2 icons.
        assertEquals("FFFFFFFFFFAA", bar(20, 20, 10, false, 3, "A"));
    }

    @Test
    void absorptionIconsStopAtTheIconLimit() {
        assertEquals("FFFFFFFFFFAAAAAAAAAA", bar(10, 10, 10, false, 50, "A"));
    }

    @Test
    void noAbsorptionIconWhenIconIsEmpty() {
        assertEquals("FFFFFFFFFF", bar(10, 10, 10, false, 8, ""));
    }
}
