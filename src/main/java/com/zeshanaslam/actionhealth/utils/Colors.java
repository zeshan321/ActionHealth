package com.zeshanaslam.actionhealth.utils;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates color codes, including hex colors, for any server version.
 * <p>
 * Hex colors can be written as {@code &#RRGGBB}, {@code {#RRGGBB}} or {@code <#RRGGBB>}.
 * Servers from 1.16 show the exact color. Older servers show the closest legacy color.
 */
public final class Colors {

    private static final Pattern HEX = Pattern.compile(
            "[&\u00A7]#([0-9a-fA-F]{6})|\\{#([0-9a-fA-F]{6})}|<#([0-9a-fA-F]{6})>");
    // The Bukkit format for hex colors (1.16+), for example from a nickname plugin.
    private static final Pattern BUKKIT_HEX = Pattern.compile("\u00A7[xX]((?:\u00A7[0-9a-fA-F]){6})");
    private static final boolean HEX_SUPPORTED = Reflect.findMethod(
            Reflect.findClass("net.md_5.bungee.api.ChatColor"), "of", String.class) != null;

    // The legacy colors and their RGB values, in color code order.
    private static final char[] LEGACY_CODES = "0123456789abcdef".toCharArray();
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private Colors() {
    }

    public static String translate(String message) {
        Matcher matcher = HEX.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(color(hex)));
        }
        matcher.appendTail(buffer);
        message = buffer.toString();

        if (!HEX_SUPPORTED) {
            matcher = BUKKIT_HEX.matcher(message);
            buffer = new StringBuffer();
            while (matcher.find()) {
                String hex = matcher.group(1).replace("\u00A7", "");
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(color(hex)));
            }
            matcher.appendTail(buffer);
            message = buffer.toString();
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static String color(String hex) {
        if (!HEX_SUPPORTED) {
            return "\u00A7" + nearestLegacy(Integer.parseInt(hex, 16));
        }

        StringBuilder builder = new StringBuilder("\u00A7x");
        for (char c : hex.toCharArray()) {
            builder.append('\u00A7').append(c);
        }

        return builder.toString();
    }

    private static char nearestLegacy(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        char nearest = 'f';
        long best = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int dr = r - (LEGACY_RGB[i] >> 16 & 0xFF), dg = g - (LEGACY_RGB[i] >> 8 & 0xFF), db = b - (LEGACY_RGB[i] & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < best) {
                best = distance;
                nearest = LEGACY_CODES[i];
            }
        }

        return nearest;
    }
}
