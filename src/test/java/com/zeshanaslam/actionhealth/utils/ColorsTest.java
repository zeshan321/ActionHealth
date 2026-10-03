package com.zeshanaslam.actionhealth.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The tests run against the 1.8.8 API, which has no hex colors. So they check the closest legacy color.
class ColorsTest {

    @Test
    void translatesLegacyCodes() {
        assertEquals("§aGreen §lbold", Colors.translate("&aGreen &lbold"));
    }

    @Test
    void hexColorUsesClosestLegacyColor() {
        // The color of the e2e test message is closest to aqua.
        assertEquals("§bText", Colors.translate("&#4fdfc4Text"));
        assertEquals("§4Red", Colors.translate("&#FF0000Red"));
        assertEquals("§0Black", Colors.translate("&#000000Black"));
    }

    @Test
    void acceptsAllHexFormats() {
        assertEquals("§fa§fb§fc", Colors.translate("{#FFFFFF}a<#ffffff>b&#FfFfFfc"));
    }

    @Test
    void translatesBukkitHexFormat() {
        // For example a nickname from a plugin that writes 1.16 hex colors.
        assertEquals("§bNick", Colors.translate("§x§4§f§d§f§c§4Nick"));
    }

    @Test
    void leavesTextWithoutColorsUnchanged() {
        assertEquals("Cow: 10/10 #123456", Colors.translate("Cow: 10/10 #123456"));
    }
}
