package com.zeshanaslam.actionhealth.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

/**
 * Formats the numbers in the health message and picks the color for {healthcolor}. It uses no
 * server API, so unit tests can call it.
 */
public final class HealthFormat {

    private HealthFormat() {
    }

    /**
     * Returns the value with the given number of decimals, for example 19.5 with 1 decimal.
     * Extra decimals are cut off, not rounded, so 19.99 health shows as 19 and not as full health (20).
     * The decimal separator is always a dot.
     */
    public static String number(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return BigDecimal.valueOf(value).setScale(Math.max(0, decimals), RoundingMode.DOWN).toPlainString();
    }

    /**
     * Returns the health with the given number of decimals. A living entity with health above 0
     * never shows 0: it shows the smallest step instead, for example 1 or 0.1.
     */
    public static String health(double health, int decimals, boolean alive) {
        String text = number(health, decimals);
        if (alive && health > 0 && BigDecimal.ZERO.compareTo(new BigDecimal(text)) == 0) {
            return BigDecimal.ONE.movePointLeft(Math.max(0, decimals)).toPlainString();
        }
        return text;
    }

    /**
     * Returns the health left as a whole percentage of the max health. A living entity with
     * health above 0 shows at least 1.
     */
    public static int percent(double health, double maxHealth, boolean alive) {
        if (maxHealth <= 0) return 0;
        int percent = (int) (health / maxHealth * 100.0);
        return alive && health > 0 && percent < 1 ? 1 : Math.max(0, percent);
    }

    /**
     * Returns the color for the percentage of health left: the color of the highest percentage
     * that the health is at or above. Returns "" if no percentage matches.
     *
     * @param colors each percentage and its color, for example 50 and "&e"
     */
    public static String color(int percent, TreeMap<Integer, String> colors) {
        Map.Entry<Integer, String> entry = colors.floorEntry(percent);
        return entry != null ? entry.getValue() : "";
    }
}
