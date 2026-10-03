package com.zeshanaslam.actionhealth.utils;

import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthFormatTest {

    @Test
    void wholeNumbersCutOffTheDecimals() {
        assertEquals("19", HealthFormat.number(19.99, 0));
        assertEquals("20", HealthFormat.number(20.0, 0));
    }

    @Test
    void decimalsAreCutOffNotRounded() {
        assertEquals("19.5", HealthFormat.number(19.5, 1));
        assertEquals("19.9", HealthFormat.number(19.99, 1));
        assertEquals("20.0", HealthFormat.number(20, 1));
        assertEquals("7.25", HealthFormat.number(7.25, 2));
    }

    @Test
    void theSeparatorIsAlwaysADot() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("1.5", HealthFormat.number(1.5, 1));
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void aLivingEntityNeverShowsZeroHealth() {
        assertEquals("1", HealthFormat.health(0.4, 0, true));
        assertEquals("0.1", HealthFormat.health(0.04, 1, true));
        assertEquals("0.4", HealthFormat.health(0.4, 1, true));
    }

    @Test
    void aDeadEntityShowsZeroHealth() {
        assertEquals("0", HealthFormat.health(0.4, 0, false));
        assertEquals("0", HealthFormat.health(0, 0, true));
    }

    @Test
    void badNumbersShowZero() {
        assertEquals("0", HealthFormat.number(Double.NaN, 1));
        assertEquals("0", HealthFormat.number(Double.POSITIVE_INFINITY, 0));
    }

    @Test
    void percentOfALivingEntityIsAtLeastOne() {
        assertEquals(50, HealthFormat.percent(10, 20, true));
        assertEquals(1, HealthFormat.percent(0.1, 20, true));
        assertEquals(0, HealthFormat.percent(0.1, 20, false));
        assertEquals(0, HealthFormat.percent(5, 0, true));
    }

    @Test
    void theColorIsTheHighestPercentageThatTheHealthReaches() {
        TreeMap<Integer, String> colors = new TreeMap<>();
        colors.put(75, "&a");
        colors.put(50, "&e");
        colors.put(25, "&6");
        colors.put(0, "&c");

        assertEquals("&a", HealthFormat.color(100, colors));
        assertEquals("&a", HealthFormat.color(75, colors));
        assertEquals("&e", HealthFormat.color(74, colors));
        assertEquals("&6", HealthFormat.color(25, colors));
        assertEquals("&c", HealthFormat.color(1, colors));
    }

    @Test
    void noMatchingColorGivesNoColor() {
        TreeMap<Integer, String> colors = new TreeMap<>();
        colors.put(50, "&e");
        assertEquals("", HealthFormat.color(10, colors));
        assertEquals("", HealthFormat.color(10, new TreeMap<>()));
    }
}
