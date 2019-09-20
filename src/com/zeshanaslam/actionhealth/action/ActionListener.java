package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class ActionListener implements Listener {

    private final Main main;

    public ActionListener(Main main) {
        this.main = main;
    }

    @EventHandler(ignoreCancelled = true)
    public void onComabat(EntityDamageByEntityEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player damaged = (Player) event.getEntity();

            main.configStore.actionStore.addTag(damager.getUniqueId(), damaged.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        ActionStore.ActionType actionType = ActionStore.ActionType.CONSUME;
        Player player = event.getPlayer();

        executeTriggers(actionType, player, event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerItemHeldEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        ActionStore.ActionType actionType = ActionStore.ActionType.SWAP;
        Player player = event.getPlayer();

        ItemStack itemStack = player.getInventory().getItem(event.getNewSlot());

        executeTriggers(actionType, player, itemStack);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!main.configStore.actionStore.enabled)
            return;

        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem();

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ActionStore.ActionType actionType = ActionStore.ActionType.RIGHTCLICK;

            executeTriggers(actionType, player, itemStack);
        } else  if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            ActionStore.ActionType actionType = ActionStore.ActionType.LEFTCLICK;
            executeTriggers(actionType, player, itemStack);
        }
    }

    private void executeTriggers(ActionStore.ActionType actionType, Player player, ItemStack itemStack) {
        if (itemStack != null) {
            for (String name: getName(itemStack))
                main.configStore.actionStore.triggerAction(actionType, player, name);
        }
    }

    private List<String> getName(ItemStack itemStack) {
        List<String> possibleMaterials = new ArrayList<>();

        String name = itemStack.getType().name();
        possibleMaterials.add(name);

        if (itemStack.hasItemMeta()) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();

                PotionData potionData = potionMeta.getBasePotionData();
                possibleMaterials.add(potionData.getType().getEffectType().getName() + "_" + name);

                if (potionMeta.hasCustomEffects()) {
                    for (PotionEffect potionEffect : potionMeta.getCustomEffects()) {
                        possibleMaterials.add(potionEffect.getType().getName() + "_" + name);
                    }
                }
            }
        }

        return possibleMaterials;
    }
}
