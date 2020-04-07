package com.zeshanaslam.actionhealth.events;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.utils.FileHandler;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
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

        if (plugin.configStore.actionStore.isUsingAnyDamageCause) {
            return;
        }

        Entity damaged = event.getEntity();
        Player player = null;
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();

            if (projectile.getShooter() instanceof Player) {
                player = (Player) projectile.getShooter();
            }
        }

        if (event.getDamager() instanceof Player)
            player = (Player) event.getDamager();

        if (player != null) {
            if (!plugin.healthUtil.matchesRequirements(player, damaged)) {
                return;
            }

            // Send health
            if (damaged instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) damaged;

                livingEntity.setLastDamage(event.getFinalDamage());
                plugin.healthUtil.sendHealth(player, livingEntity, livingEntity.getHealth() - event.getFinalDamage());
            }
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
