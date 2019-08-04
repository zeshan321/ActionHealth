package com.zeshanaslam.actionhealth.events;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.utils.FileHandler;
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
        if (plugin.configStore.checkPvP && event.isCancelled()) {
            return;
        }

        if (plugin.healthUtil.isDisabled(event.getDamager().getLocation())) {
            return;
        }

        if (plugin.configStore.worlds.contains(event.getDamager().getWorld().getName())) {
            return;
        }

        if (plugin.configStore.usePerms && !event.getDamager().hasPermission("ActionHealth.Health")) {
            return;
        }

        // Check if the setting 'Show Player' is enabled
        if (event.getEntity() instanceof Player) {
            if (!plugin.configStore.showPlayers) {
                return;
            }
        }

        Entity damaged = event.getEntity();
        if (damaged.getType().name().equals("ARMOR_STAND")) return;

        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();

            if (projectile.getShooter() instanceof Player) {
                Player player = (Player) projectile.getShooter();

                if (!plugin.configStore.showMobs) {
                    return;
                }

                if (player.getUniqueId() == damaged.getUniqueId()) {
                    return;
                }

                if (plugin.toggle.contains(player.getUniqueId())) {
                    sendMessage(player);
                    return;
                }

                // Send health
                if (damaged instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) damaged;
                    plugin.healthUtil.sendHealth(player, livingEntity, livingEntity.getHealth() - event.getFinalDamage());
                }
            }
        }

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            if (player.getUniqueId() == damaged.getUniqueId()) {
                return;
            }

            // Check if the setting 'Show Player' is enabled
            if (event.getEntity() instanceof Player) {
                if (!plugin.configStore.showPlayers) {
                    return;
                }

                if (player.hasMetadata("NPC")) {
                    return;
                }
            }

            if (!plugin.configStore.showMobs) {
                return;
            }

            if (plugin.toggle.contains(player.getUniqueId())) {
                sendMessage(player);
                return;
            }

            // Send health
            if (damaged instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) damaged;
                plugin.healthUtil.sendHealth(player, livingEntity, livingEntity.getHealth() - event.getFinalDamage());
            }
        }
    }

    private void sendMessage(Player player) {
        if (plugin.configStore.toggleMessage != null && !plugin.configStore.toggleMessage.equals("")) {
            plugin.healthUtil.sendActionBar(player, plugin.configStore.toggleMessage.replace("{name}", player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.configStore.rememberToggle) {
            FileHandler fileHandler = new FileHandler("plugins/ActionHealth/players/" + player.getUniqueId() + ".yml");

            if (fileHandler.getBoolean("toggle")) {
                plugin.toggle.add(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.toggle.remove(player.getUniqueId());
    }
}
