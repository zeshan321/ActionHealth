package com.zeshanaslam.actionhealth.support;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

public class MythicMobsSupport {
    private static final MythicBukkit plugin = (MythicBukkit) Bukkit.getServer().getPluginManager().getPlugin("MythicMobs");

    public String getMythicName(Entity entity) {
        if (plugin == null) {
            return null;
        }

        BukkitAPIHelper bucketApiHelper = plugin.getAPIHelper();
        if (bucketApiHelper.isMythicMob(entity)) {
            return bucketApiHelper.getMythicMobInstance(entity).getType().getInternalName();
        }
        return null;
    }
}
