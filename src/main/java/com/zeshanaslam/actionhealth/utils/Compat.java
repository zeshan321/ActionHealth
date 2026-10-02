package com.zeshanaslam.actionhealth.utils;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Bukkit API calls that changed between server versions. Each method uses the newest API
 * that exists and falls back to older ones, so no version check is needed.
 */
public final class Compat {

    // Attribute API (1.9+). The constant is GENERIC_MAX_HEALTH up to 1.21.1 and MAX_HEALTH after.
    private static final Class<?> ATTRIBUTE = Reflect.findClass("org.bukkit.attribute.Attribute");
    private static final Object MAX_HEALTH_ATTRIBUTE = findMaxHealthAttribute();
    private static final Method GET_ATTRIBUTE = Reflect.findMethod(LivingEntity.class, "getAttribute", ATTRIBUTE);
    private static final Method GET_ATTRIBUTE_VALUE = Reflect.findMethod(Reflect.findClass("org.bukkit.attribute.AttributeInstance"), "getValue");

    // Base potion of a potion item: getBasePotionType (1.20.2+), getBasePotionData (1.9+), Potion.fromItemStack (1.8).
    private static final Method GET_BASE_POTION_TYPE = Reflect.findMethod(PotionMeta.class, "getBasePotionType");
    private static final Method GET_BASE_POTION_DATA = Reflect.findMethod(PotionMeta.class, "getBasePotionData");
    private static final Method POTION_FROM_ITEM = Reflect.findMethod(Reflect.findClass("org.bukkit.potion.Potion"), "fromItemStack", ItemStack.class);

    private Compat() {
    }

    private static Object findMaxHealthAttribute() {
        Object attribute = Reflect.getStaticField(ATTRIBUTE, "MAX_HEALTH");
        return attribute != null ? attribute : Reflect.getStaticField(ATTRIBUTE, "GENERIC_MAX_HEALTH");
    }

    @SuppressWarnings("deprecation")
    public static double getMaxHealth(LivingEntity entity) {
        if (MAX_HEALTH_ATTRIBUTE != null && GET_ATTRIBUTE != null && GET_ATTRIBUTE_VALUE != null) {
            try {
                Object instance = GET_ATTRIBUTE.invoke(entity, MAX_HEALTH_ATTRIBUTE);
                if (instance != null) {
                    return (double) GET_ATTRIBUTE_VALUE.invoke(instance);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        return entity.getMaxHealth();
    }

    /**
     * Returns the effect type of the base potion (for example REGENERATION), or null if the
     * potion has no base effect (for example a water bottle).
     */
    public static PotionEffectType getBasePotionEffect(PotionMeta meta, ItemStack itemStack) {
        Object potionType = null;
        try {
            if (GET_BASE_POTION_TYPE != null) {
                potionType = GET_BASE_POTION_TYPE.invoke(meta);
            } else if (GET_BASE_POTION_DATA != null) {
                potionType = Reflect.call(GET_BASE_POTION_DATA.invoke(meta), "getType");
            } else if (POTION_FROM_ITEM != null) {
                potionType = Reflect.call(POTION_FROM_ITEM.invoke(null, itemStack), "getType");
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        if (potionType == null) return null;

        Object effectType = Reflect.call(potionType, "getEffectType");
        if (effectType == null) {
            // Newer APIs describe a potion type as a list of effects.
            Object effects = Reflect.call(potionType, "getPotionEffects");
            if (effects instanceof List && !((List<?>) effects).isEmpty()) {
                effectType = Reflect.call(((List<?>) effects).get(0), "getType");
            }
        }

        return effectType instanceof PotionEffectType ? (PotionEffectType) effectType : null;
    }

    /**
     * Returns the legacy upper case name of an effect (for example REGENERATION), as used in the config.
     */
    @SuppressWarnings("deprecation")
    public static String getEffectName(PotionEffectType effectType) {
        try {
            return effectType.getName();
        } catch (IncompatibleClassChangeError e) {
            // Removed method (NoSuchMethodError) or a class that became an interface.
            Object key = Reflect.call(Reflect.call(effectType, "getKey"), "getKey");
            return key == null ? effectType.toString() : key.toString().toUpperCase(Locale.ROOT);
        }
    }
}
