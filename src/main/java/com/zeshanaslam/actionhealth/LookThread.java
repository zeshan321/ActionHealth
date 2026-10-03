package com.zeshanaslam.actionhealth;

import com.zeshanaslam.actionhealth.utils.Scheduler;
import com.zeshanaslam.actionhealth.utils.TargetHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Shows the health of the entity that each player looks at ("Show On Look").
 * On Folia, the check for each player runs on the region thread of that player.
 */
public class LookThread implements Runnable {

    private Main plugin;
    private TargetHelper targetHelper;

    public LookThread(Main plugin) {
        this.plugin = plugin;
        this.targetHelper = new TargetHelper(plugin);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scheduler.runFor(plugin, player, () -> look(player));
        }
    }

    private void look(Player player) {
        if (!player.isOnline()) return;

        // A player who turned ActionHealth off sees the toggle message instead of the health, and
        // only while looking at an entity. Without a toggle message there is nothing to show.
        boolean toggled = plugin.toggles.isToggled(player.getUniqueId());
        if (toggled && !plugin.healthUtil.hasToggleMessage()) return;

        List<LivingEntity> entities = targetHelper.getLivingTargets(player, plugin.configStore.lookDistance);
        for (LivingEntity livingEntity : entities) {
            if (!plugin.healthUtil.matchesFilters(player, livingEntity)) continue;

            String name = plugin.healthUtil.getName(livingEntity, player);

            if (targetHelper.canSee(player, livingEntity) && !plugin.healthUtil.isBlacklisted(livingEntity, name)) {
                if (plugin.configStore.isUsingWhiteList()) {
                    if (!plugin.healthUtil.isWhiteListed(livingEntity, name)) {
                        continue;
                    }
                }

                if (toggled) {
                    plugin.healthUtil.sendToggleMessage(player, true);
                } else {
                    plugin.healthUtil.sendHealth(player, livingEntity, livingEntity.getHealth(), true);
                }
                break;
            }
        }
    }
}
