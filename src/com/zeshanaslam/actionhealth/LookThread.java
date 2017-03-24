package com.zeshanaslam.actionhealth;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class LookThread extends BukkitRunnable {

    private Main plugin;

    public LookThread(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.toggle.contains(player.getUniqueId())) {
                if (plugin.settingsManager.toggleMessage != null && !plugin.settingsManager.toggleMessage.equals("")) {
                    plugin.healthUtil.sendActionBar(player, plugin.settingsManager.toggleMessage.replace("{name}", player.getName()));
                }
                continue;
            }

            List<LivingEntity> entities = TargetHelper.getLivingTargets(player, plugin.settingsManager.lookDistance);

            if (!entities.isEmpty()) {
                LivingEntity livingEntity = entities.get(0);
                String name;

                if (livingEntity.getCustomName() == null) {
                    name = livingEntity.getName();
                } else {
                    name = livingEntity.getCustomName();
                }

                if (!plugin.settingsManager.blacklist.contains(name) && !livingEntity.hasMetadata("NPC")) {
                    plugin.healthUtil.sendHealth(player, livingEntity, livingEntity.getHealth());
                }
            }
        }
    }
}
