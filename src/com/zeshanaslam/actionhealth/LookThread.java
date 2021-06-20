package com.zeshanaslam.actionhealth;

import com.zeshanaslam.actionhealth.utils.TargetHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class LookThread extends BukkitRunnable {

    private Main plugin;
    private TargetHelper targetHelper;

    public LookThread(Main plugin) {
        this.plugin = plugin;
        this.targetHelper = new TargetHelper(plugin);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.toggle.contains(player.getUniqueId())) {
                if (plugin.configStore.toggleMessage != null && !plugin.configStore.toggleMessage.equals("")) {
                    plugin.healthUtil.sendActionBar(player, plugin.healthUtil.replacePlaceholders(plugin.configStore.toggleMessage, "name", player.getName()));
                }
                continue;
            }

            List<LivingEntity> entities = targetHelper.getLivingTargets(player, plugin.configStore.lookDistance);
            if (!entities.isEmpty()) {
                for (LivingEntity livingEntity : entities) {
                    if (!plugin.healthUtil.matchesRequirements(player, livingEntity)) continue;

                    String name = plugin.healthUtil.getName(livingEntity, player);

                    if (targetHelper.canSee(player, livingEntity.getLocation()) && !plugin.healthUtil.isBlacklisted(livingEntity, name)) {
                        if (plugin.configStore.isUsingWhiteList()) {
                            if (!plugin.healthUtil.isWhiteListed(livingEntity, name)) {
                                continue;
                            }
                        }

                        plugin.healthUtil.sendHealth(player, livingEntity, livingEntity.getHealth());
                        break;
                    }
                }
            }
        }
    }
}
