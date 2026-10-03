package com.zeshanaslam.actionhealth.events;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.action.ActionStore;
import com.zeshanaslam.actionhealth.utils.Scheduler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

        // The DAMAGE ANY action sends the health message for this hit instead.
        ActionStore actionStore = plugin.configStore.actionStore;
        if (actionStore.enabled && actionStore.isUsingAnyDamageCause) {
            return;
        }

        Player player = null;
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();

            if (projectile.getShooter() instanceof Player) {
                player = (Player) projectile.getShooter();
            }
        }

        if (event.getDamager() instanceof Player)
            player = (Player) event.getDamager();

        if (player == null || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        // A cancelled hit does no damage. This happens only when 'Region PvP' is false.
        double health = livingEntity.getHealth();
        if (!event.isCancelled()) {
            health -= event.getFinalDamage();
            // Set here too, because the order of MONITOR listeners for one event is not fixed.
            plugin.healthUtil.setLastDamage(livingEntity, event.getFinalDamage());
        }

        // On Folia, an arrow can hit an entity far from the shooter, in a region that another thread
        // owns. The checks and the message run on the thread of the shooter, because they call other
        // plugins (permissions, WorldGuard, PlaceholderAPI) for the shooter.
        Player attacker = player;
        double healthAfterHit = health;
        Scheduler.runFor(plugin, attacker, () -> {
            if (plugin.healthUtil.matchesRequirements(attacker, livingEntity)) {
                plugin.healthUtil.sendHealth(attacker, livingEntity, healthAfterHit);
            }
        });
    }

    // Keeps the damage for {opponentlastdamage}. The plugin does not write it to the entity, because
    // the server uses the last damage of an entity to calculate the damage of the next hit.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            plugin.healthUtil.setLastDamage((LivingEntity) event.getEntity(), event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!plugin.configStore.rememberToggle) {
            plugin.toggles.forget(player.getUniqueId());
        }
        plugin.healthUtil.forget(player.getUniqueId());
    }
}
