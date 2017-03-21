package com.zeshanaslam.actionhealth;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class HealthListeners implements Listener {

    private Main plugin;

    public HealthListeners(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (plugin.settingsManager.checkPvP && event.isCancelled()) {
            return;
        }

        if (plugin.healthUtil.isDisabled(event.getDamager().getLocation())) {
            return;
        }

        if (plugin.settingsManager.worlds.contains(event.getDamager().getWorld().getName())) {
            return;
        }

        if (plugin.settingsManager.usePerms && !event.getDamager().hasPermission("ActionHealth.Health")) {
            return;
        }


        Entity damaged = event.getEntity();

        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();

            if (projectile.getShooter() instanceof Player) {
                Player player = (Player) projectile.getShooter();

                // Check if the setting 'Show Player' is enabled
                if (event.getEntity() instanceof Player) {
                    if (!plugin.settingsManager.showPlayers) {
                        return;
                    }
                }

                if (!plugin.settingsManager.showMobs) {
                    return;
                }

                if (plugin.toggle.contains(player.getUniqueId())) {
                    return;
                }

                // Send health
                if (damaged instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) damaged;
                    plugin.healthUtil.sendHealth(player, (LivingEntity) damaged, (int) (livingEntity.getHealth() - event.getFinalDamage()));
                }
            }
        }

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            // Check if the setting 'Show Player' is enabled
            if (event.getEntity() instanceof Player) {
                if (!plugin.settingsManager.showPlayers) {
                    return;
                }

                if (player.hasMetadata("NPC")) {
                    return;
                }
            }

            if (!plugin.settingsManager.showMobs) {
                return;
            }

            if (plugin.toggle.contains(player.getUniqueId())) {
                return;
            }

            // Send health
            if (damaged instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) damaged;
                plugin.healthUtil.sendHealth(player, (LivingEntity) damaged, (int) (livingEntity.getHealth() - event.getFinalDamage()));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.settingsManager.rememberToggle) {
            FileHandler fileHandler = new FileHandler("plugins/ActionHealth/players/" + player.getUniqueId() + ".yml");

            if (fileHandler.getBoolean("toggle")) {
                plugin.toggle.add(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.toggle.contains(player.getUniqueId())) {
            plugin.toggle.remove(player.getUniqueId());
        }
    }
}
