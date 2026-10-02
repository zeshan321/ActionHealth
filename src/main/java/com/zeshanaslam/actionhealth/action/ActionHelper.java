package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.action.data.Action;
import com.zeshanaslam.actionhealth.utils.Compat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ActionHelper {

    private final Main main;

    public ActionHelper(Main main) {
        this.main = main;
    }

    public void executeTriggers(ActionStore.ActionType actionType, Player player, ItemStack itemStack) {
        if (itemStack != null) {
            for (String name : getName(itemStack))
                executeTriggers(actionType, player, name);
        }
    }

    public void executeTriggers(ActionStore.ActionType actionType, LivingEntity entity, String name) {
        main.configStore.actionStore.triggerAction(actionType, entity, name);
    }

    public void executeTriggers(ActionStore.ActionType actionType, LivingEntity entity, String name, double health) {
        main.configStore.actionStore.triggerAction(actionType, entity, name, Optional.of(health));
    }

    public int getActionTypeEventAmount(ActionStore.ActionType actionType) {
        if (main.configStore.actionStore.events.containsKey(actionType)) {
            List<Action> actions = main.configStore.actionStore.events.get(actionType);
            return actions.size();
        }

        return 0;
    }

    public Player getDamagerFromEntity(Entity entity) {
        Player damager = null;

        if (entity instanceof Projectile) {
            Projectile projectile = (Projectile) entity;
            ProjectileSource projectileSource = projectile.getShooter();

            if (projectileSource instanceof Player) {
                damager = (Player) projectileSource;
            }
        }

        return damager;
    }

    public List<String> getName(ItemStack itemStack) {
        List<String> possibleMaterials = new ArrayList<>();

        String name = itemStack.getType().name();
        possibleMaterials.add(name);

        // Not guarded by hasItemMeta(): 1.8 stores the potion type in the durability, so a plain
        // potion has no item meta there, but getItemMeta() still returns a PotionMeta.
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta instanceof PotionMeta) {
            PotionMeta potionMeta = (PotionMeta) itemMeta;

            PotionEffectType baseEffect = Compat.getBasePotionEffect(potionMeta, itemStack);
            if (baseEffect != null) {
                possibleMaterials.add(Compat.getEffectName(baseEffect) + "_" + name);
            }

            if (potionMeta.hasCustomEffects()) {
                for (PotionEffect potionEffect : potionMeta.getCustomEffects()) {
                    possibleMaterials.add(Compat.getEffectName(potionEffect.getType()) + "_" + name);
                }
            }
        }

        return possibleMaterials;
    }
}
