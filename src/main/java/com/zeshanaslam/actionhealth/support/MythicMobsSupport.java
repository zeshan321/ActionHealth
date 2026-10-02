package com.zeshanaslam.actionhealth.support;

import com.zeshanaslam.actionhealth.utils.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Reads the MythicMobs internal name through reflection. MythicMobs 4 and 5 use different
 * packages but the same method names. Methods are cached, because this runs for every
 * entity a player looks at.
 */
public class MythicMobsSupport {

    private static Plugin plugin;
    private static Object apiHelper;
    private static Method isMythicMob;
    private static Method getMythicMobInstance;

    public String getMythicName(Entity entity) {
        Plugin current = Bukkit.getServer().getPluginManager().getPlugin("MythicMobs");
        if (current == null) {
            return null;
        }

        if (current != plugin) {
            plugin = current;
            apiHelper = Reflect.call(current, "getAPIHelper");
            Class<?> helperClass = apiHelper == null ? null : apiHelper.getClass();
            isMythicMob = Reflect.findMethod(helperClass, "isMythicMob", Entity.class);
            getMythicMobInstance = Reflect.findMethod(helperClass, "getMythicMobInstance", Entity.class);
        }

        if (isMythicMob == null || getMythicMobInstance == null) {
            return null;
        }

        try {
            if (!(boolean) isMythicMob.invoke(apiHelper, entity)) {
                return null;
            }

            Object activeMob = getMythicMobInstance.invoke(apiHelper, entity);
            Object name = Reflect.call(Reflect.call(activeMob, "getType"), "getInternalName");
            return name == null ? null : name.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
