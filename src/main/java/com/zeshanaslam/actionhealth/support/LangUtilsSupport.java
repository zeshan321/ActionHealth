package com.zeshanaslam.actionhealth.support;

import com.zeshanaslam.actionhealth.utils.Reflect;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class LangUtilsSupport {

    private static final Method GET_ENTITY_NAME = Reflect.findMethod(
            Reflect.findClass("com.meowj.langutils.lang.LanguageHelper"), "getEntityName", Entity.class, Player.class);

    /**
     * Returns the entity name in the player's client language, or null if LangUtils can not provide it.
     */
    public String getName(Entity entity, Player player) {
        if (GET_ENTITY_NAME == null) return null;

        try {
            Object name = GET_ENTITY_NAME.invoke(null, entity, player);
            return name == null ? null : name.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
