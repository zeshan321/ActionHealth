package com.zeshanaslam.actionhealth.support;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.meowj.langutils.lang.LanguageHelper;

public class LangUtilsSupport {

    public String getName(Entity entity, Player player) {
        return LanguageHelper.getEntityName(entity, player);
    }
    
    public String getName(Entity entity, String locale) {
        return LanguageHelper.getEntityName(entity, locale);
    }
}
