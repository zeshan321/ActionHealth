package com.zeshanaslam.actionhealth.support;

import com.zeshanaslam.actionhealth.utils.Reflect;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Calls PlaceholderAPI and MVdWPlaceholderAPI through reflection, so any version of either
 * plugin works and neither is needed to compile.
 */
public class PlaceholderSupport {

    // Looked up on first use, so a placeholder plugin that loads after ActionHealth still works.
    private Method placeholderAPI;
    private Method mvdwPlaceholderAPI;

    private static Method findReplaceMethod(String className, String methodName) {
        Class<?> type = Reflect.findClass(className);
        Method method = Reflect.findMethod(type, methodName, Player.class, String.class);
        return method != null ? method : Reflect.findMethod(type, methodName, OfflinePlayer.class, String.class);
    }

    public String setPlaceholderAPI(Player player, String text) {
        if (placeholderAPI == null) {
            placeholderAPI = findReplaceMethod("me.clip.placeholderapi.PlaceholderAPI", "setPlaceholders");
        }

        return replace(placeholderAPI, player, text);
    }

    public String setMVdWPlaceholderAPI(Player player, String text) {
        if (mvdwPlaceholderAPI == null) {
            mvdwPlaceholderAPI = findReplaceMethod("be.maximvdw.placeholderapi.PlaceholderAPI", "replacePlaceholders");
        }

        return replace(mvdwPlaceholderAPI, player, text);
    }

    private String replace(Method method, Player player, String text) {
        if (method == null) return text;

        try {
            Object result = method.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return text;
        }
    }
}
