package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

public class ActionListener implements Listener {

    private final Main main;
    private ActionHelper actionHelper;

    public ActionListener(Main main, ActionHelper actionHelper) {
        this.main = main;
        this.actionHelper = actionHelper;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        ActionStore.ActionType actionType = ActionStore.ActionType.DAMAGE;
        Player damager = actionHelper.getDamagerFromEntity(event.getDamager());
        if (damager == null && event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        }

        if (damager != null && event.getEntity() instanceof Player) {
            Player damaged = (Player) event.getEntity();

            main.configStore.actionStore.addTag(damager.getUniqueId(), damaged.getUniqueId());
        } else if (damager != null && main.configStore.actionStore.events.containsKey(actionType)) {
            main.configStore.actionStore.addTag(damager.getUniqueId(), event.getEntity().getUniqueId());
        }

        if (!main.configStore.actionStore.isUsingAnyDamageCause) {
            EntityDamageEvent.DamageCause damageCause = event.getCause();
            if (event.getEntity() instanceof LivingEntity)
                actionHelper.executeTriggers(actionType, (LivingEntity) event.getEntity(), damageCause.name());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        Entity entity = event.getEntity();
        ActionStore.ActionType actionType = ActionStore.ActionType.DAMAGE;

        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            actionHelper.executeTriggers(actionType, livingEntity, "ANY", livingEntity.getHealth() - event.getFinalDamage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        ActionStore.ActionType actionType = ActionStore.ActionType.CONSUME;
        Player player = event.getPlayer();

        actionHelper.executeTriggers(actionType, player, event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerItemHeldEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        ActionStore.ActionType actionType = ActionStore.ActionType.SWAP;
        Player player = event.getPlayer();

        ItemStack itemStack = player.getInventory().getItem(event.getNewSlot());

        actionHelper.executeTriggers(actionType, player, itemStack);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem();
        if (itemStack == null)
            return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ActionStore.ActionType actionType = ActionStore.ActionType.RIGHTCLICK;

            actionHelper.executeTriggers(actionType, player, itemStack);
        } else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            ActionStore.ActionType actionType = ActionStore.ActionType.LEFTCLICK;
            actionHelper.executeTriggers(actionType, player, itemStack);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        main.configStore.actionStore.remove(entity.getUniqueId());
    }
}
