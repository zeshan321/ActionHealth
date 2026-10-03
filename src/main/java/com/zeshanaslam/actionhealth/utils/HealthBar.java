package com.zeshanaslam.actionhealth.utils;

/**
 * Builds the health icons for {usestyle}. It uses no server API, so unit tests can call it.
 */
public final class HealthBar {

    private HealthBar() {
    }

    /**
     * @param health         the health to show
     * @param maxHealth      the maximum health of the entity
     * @param icons          the number of icons for full health ("Limit Health")
     * @param dead           true if the entity is dead
     * @param absorption     the absorption health of the entity
     * @param absorptionIcon the icon for each heart of absorption, or "" for none
     */
    public static String build(double health, double maxHealth, int icons, boolean dead,
                               String fullIcon, String halfIcon, String emptyIcon,
                               double absorption, String absorptionIcon) {
        StringBuilder style = new StringBuilder();
        int left = icons;
        double heart = maxHealth / icons;
        double halfHeart = heart / 2;
        double tempHealth = health;

        if (maxHealth != health && health >= 0 && !dead) {
            for (int i = 0; i < icons; i++) {
                if (tempHealth - heart > 0) {
                    tempHealth = tempHealth - heart;

                    style.append(fullIcon);
                    left--;
                } else {
                    break;
                }
            }

            if (tempHealth > halfHeart) {
                style.append(fullIcon);
                left--;
            } else if (tempHealth > 0 && tempHealth <= halfHeart) {
                style.append(halfIcon);
                left--;
            }
        }

        String rest = maxHealth != health ? emptyIcon : fullIcon;
        for (int i = 0; i < left; i++) {
            style.append(rest);
        }

        // Absorption icons after the health icons, one per heart of absorption.
        if (!absorptionIcon.isEmpty()) {
            int count = Math.min((int) Math.ceil(absorption / heart), icons);
            for (int i = 0; i < count; i++) {
                style.append(absorptionIcon);
            }
        }

        return style.toString();
    }
}
