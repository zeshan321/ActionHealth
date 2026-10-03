package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    // One handler for all damage, so the tag of this hit exists before the DAMAGE message is sent.
    // MONITOR, because it only reads the event and the final damage.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        ActionStore actionStore = main.configStore.actionStore;
        if (!actionStore.enabled)
            return;

        if (event instanceof EntityDamageByEntityEvent) {
            tag((EntityDamageByEntityEvent) event);
        }

        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) event.getEntity();
            // ANY covers every damage cause. Otherwise the message of the cause is used, for example LAVA.
            String name = actionStore.isUsingAnyDamageCause ? "ANY" : event.getCause().name();
            actionHelper.executeTriggers(ActionStore.ActionType.DAMAGE, livingEntity, name, livingEntity.getHealth() - event.getFinalDamage());
        }
    }

    private void tag(EntityDamageByEntityEvent event) {
        ActionStore actionStore = main.configStore.actionStore;
        Player damager = actionHelper.getDamagerFromEntity(event.getDamager());
        if (damager == null && event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        }

        if (damager != null && event.getEntity() instanceof Player) {
            actionStore.addTag(damager.getUniqueId(), event.getEntity().getUniqueId());
        } else if (damager != null && actionStore.events.containsKey(ActionStore.ActionType.DAMAGE)) {
            actionStore.addTag(damager.getUniqueId(), event.getEntity().getUniqueId());
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
