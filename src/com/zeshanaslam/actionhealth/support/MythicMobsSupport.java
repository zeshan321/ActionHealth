package com.zeshanaslam.actionhealth.support;

import io.lumine.xikage.mythicmobs.MythicMobs;
import org.bukkit.entity.Entity;

public class MythicMobsSupport {
    public String getMythicName(Entity entity) {
        if (MythicMobs.inst().getAPIHelper().isMythicMob(entity)) {
            return MythicMobs.inst().getAPIHelper().getMythicMobInstance(entity).getType().getInternalName();
        }

        return null;
    }
}
