package com.zeshanaslam.actionhealth.utils;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.api.HealthSendEvent;
import com.zeshanaslam.actionhealth.support.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class HealthUtil {

    private Main plugin;
    private final ActionBar actionBar;
    private final PlaceholderSupport placeholderSupport = new PlaceholderSupport();
    private final WorldGuardSupport worldGuard;
    // Thread safe, because Folia runs events and entity tasks on many threads.
    private final Map<UUID, Scheduler.Task> clearTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Sent> lastSent = new ConcurrentHashMap<>();
    // Weak keys: an entry goes away when the server removes the entity.
    private final Map<Entity, Double> lastDamage = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Class<?>, Optional<Method>> getNameMethods = new ConcurrentHashMap<>();

    /**
     * The client shows an action bar for about 3 seconds and fades it in the last second.
     * The look check sends the same message again only after this time, so the bar does not fade.
     */
    private static final long RESEND_MILLIS = 1000;

    public HealthUtil(Main plugin) {
        this.plugin = plugin;
        this.actionBar = new ActionBar(plugin.getLogger());
        this.worldGuard = new WorldGuardSupport(plugin.getLogger());
    }

    public void sendHealth(Player receiver, LivingEntity entity, double health) {
        sendHealth(receiver, entity, health, false);
    }

    /**
     * @param repeated true for the look check, which sends the same message many times a second
     */
    public void sendHealth(Player receiver, LivingEntity entity, double health, boolean repeated) {
        if (plugin.configStore.canSee) {

            if (entity instanceof Player) {
                Player player = (Player) entity;

                if (!receiver.canSee(player)) {
                    return;
                }
            }
        }

        if (plugin.configStore.spectatorMode) {

            if (entity instanceof Player) {
                Player player = (Player) entity;

                // Using string version for older versions. Checking for gamemode is null because of fake player npcs.
                if (player.getGameMode() != null && player.getGameMode().name().equals("SPECTATOR")) {
                    return;
                }
            }
        }

        if (plugin.configStore.invisiblePotion) {
            if (entity.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                return;
            }
        }

        if (plugin.configStore.delay) {
            // On the thread of the entity, because it reads the health of the entity.
            Scheduler.runLaterFor(plugin, entity, () -> {
                String output = getOutput(entity.getHealth(), plugin.configStore.healthMessage, receiver, entity);

                if (output != null)
                    sendActionBar(receiver, output, repeated);
            }, plugin.configStore.delayTick);
        } else {
            String output = getOutput(health, plugin.configStore.healthMessage, receiver, entity);

            if (output != null)
                sendActionBar(receiver, output, repeated);
        }
    }

    public String getOutput(double health, String output, Player receiver, LivingEntity entity) {
        double maxHealth = Compat.getMaxHealth(entity);

        if (health < 0.0 || entity.isDead()) health = 0.0;

        String name = getName(entity, receiver);
        if (plugin.healthUtil.isBlacklisted(entity, name)) return null;
        if (plugin.configStore.stripName) name = ChatColor.stripColor(name);
        if (plugin.configStore.isUsingWhiteList()) {
            if (!plugin.healthUtil.isWhiteListed(entity, name)) {
                return null;
            }
        }

        if (!(entity instanceof Player) && !plugin.configStore.healthMessageOther.isEmpty()) {
            output = plugin.configStore.healthMessageOther;
        }

        // Before PlaceholderAPI, so placeholders in the health icons are replaced too.
        output = replaceStyle(output, health, maxHealth, entity);

        if (entity instanceof Player) {
            String displayName;
            Player player = (Player) entity;

            if (player.getDisplayName() == null) {
                displayName = name;
            } else {
                displayName = player.getDisplayName();
            }

            output = replacePlaceholders(output, "displayname", displayName);

            // Set placeholders as attacker
            if (plugin.configStore.hasMVdWPlaceholderAPI) {
                output = replacePlaceholders(output, "ATTACKEDPLAYER_", "");
                output = placeholderSupport.setMVdWPlaceholderAPI(player, output);
            }

            if (plugin.configStore.hasPlaceholderAPI) {
                output = replacePlaceholders(output, "ATTACKEDPLAYER_", "");
                output = placeholderSupport.setPlaceholderAPI(player, output);
            }
        } else {
            output = replacePlaceholders(output, "displayname", name);
        }

        // Set placeholders as receiver
        if (plugin.configStore.hasMVdWPlaceholderAPI) {
            output = placeholderSupport.setMVdWPlaceholderAPI(receiver, output);
        }

        if (plugin.configStore.hasPlaceholderAPI) {
            output = placeholderSupport.setPlaceholderAPI(receiver, output);
        }

        output = replacePlaceholders(output, "name", name);
        output = replacePlaceholders(output, "health", String.valueOf((int) health));
        output = replacePlaceholders(output, "maxhealth", String.valueOf((int) maxHealth));
        output = replacePlaceholders(output, "percenthealth", String.valueOf((int) ((health / maxHealth) * 100.0)));
        output = replacePlaceholders(output, "opponentlastdamage", String.valueOf((int) getLastDamage(entity)));
        output = replacePlaceholders(output, "absorption", String.valueOf((int) Compat.getAbsorption(entity)));

        HealthSendEvent healthSendEvent = new HealthSendEvent(receiver, entity, output);
        Bukkit.getPluginManager().callEvent(healthSendEvent);
        if (healthSendEvent.isCancelled())
            output = null;
        else
            output = healthSendEvent.getMessage();

        return output;
    }

    private String replaceStyle(String output, double health, double maxHealth, LivingEntity entity) {
        if (!output.contains("usestyle")) return output;

        String absorptionIcon = plugin.configStore.absorptionIcon;
        String style = HealthBar.build(health, maxHealth, getLimitHealth(maxHealth), entity.isDead(),
                plugin.configStore.filledHeartIcon, plugin.configStore.halfHeartIcon, plugin.configStore.emptyHeartIcon,
                absorptionIcon.isEmpty() ? 0 : Compat.getAbsorption(entity), absorptionIcon);
        return replacePlaceholders(output, "usestyle", style);
    }

    public int getLimitHealth(double maxHealth) {
        if (plugin.configStore.limitHealth == -1) {
            int health = (int) maxHealth;
            if (plugin.configStore.upperLimit != null) {
                return (health >= plugin.configStore.upperLimitStart) ? plugin.configStore.upperLimitLength : health;
            }

            return health;
        }

        return plugin.configStore.limitHealth;
    }

    public String getName(LivingEntity entity, Player receiver) {
        String name;

        // Supporting mcmmo health bar to get to display correct name.
        List<MetadataValue> metadataValues = entity.getMetadata("mcMMO_oldName");
        List<MetadataValue> metadataValuesOld = entity.getMetadata("mcMMO: Custom Name");

        String mcMMOName = null;
        if (plugin.mcMMOEnabled && entity.getCustomName() != null && (!metadataValues.isEmpty() || !metadataValuesOld.isEmpty())) {
            mcMMOName = new McMMOSupport().getName(metadataValues.isEmpty() ? metadataValuesOld.get(0) : metadataValues.get(0));
        }

        if (mcMMOName == null) {
            if (entity.getCustomName() != null) {
                name = entity.getCustomName();
            } else if (plugin.langUtilsEnabled && plugin.configStore.useClientLanguage && receiver != null) {
                name = new LangUtilsSupport().getName(entity, receiver);
                if (name == null) name = getNameReflection(entity);
            } else {
                name = getNameReflection(entity);
            }
        } else if (mcMMOName.equals("")) {
            name = getNameReflection(entity);
        } else {
            name = mcMMOName;
        }

        if (plugin.configStore.translate.containsKey(name))
            name = plugin.configStore.translate.get(name);

        return name;
    }

    public String replacePlaceholders(String s, String key, String value) {
        return s.replace("{" + key + "}", value).replace("{ah" + key + "}", value);
    }

    private String getNameReflection(LivingEntity entity) {
        String name;
        Method getName = null;
        if (entity.getCustomName() == null) {
            // Cached for each class, because the look check runs this many times a second.
            getName = getNameMethods.computeIfAbsent(entity.getClass(), type -> {
                try {
                    // No null parameter array: Paper's reflection remapper throws on it (1.20.5 to early 1.21).
                    return Optional.of(type.getMethod("getName"));
                } catch (NoSuchMethodException | SecurityException e) {
                    return Optional.empty();
                }
            }).orElse(null);
        }

        if (getName != null) {
            try {
                name = (String) getName.invoke(entity);
            } catch (IllegalAccessException | InvocationTargetException e) {
                name = capitalizeFully(entity.getType().name().replace("_", ""));
            }
        } else {
            name = capitalizeFully(entity.getType().name().replace("_", ""));
        }

        return name;
    }

    private String capitalizeFully(String words) {
        StringBuilder builder = new StringBuilder(words.length());
        boolean capitalizeNext = true;
        for (char c : words.toLowerCase().toCharArray()) {
            builder.append(capitalizeNext ? Character.toTitleCase(c) : c);
            capitalizeNext = Character.isWhitespace(c);
        }

        return builder.toString();
    }

    public void sendActionBar(Player player, String message) {
        sendActionBar(player, message, false);
    }

    /**
     * @param repeated true for messages that the look check sends many times a second. The same
     *                 message is then sent again only after {@link #RESEND_MILLIS}.
     */
    public void sendActionBar(Player player, String message, boolean repeated) {
        // NPC players (for example Citizens) have no client to show the message.
        if (player.hasMetadata("NPC"))
            return;

        String translated = Colors.translate(message);
        long now = System.currentTimeMillis();
        Sent sent = lastSent.get(player.getUniqueId());
        if (!repeated || sent == null || !sent.message.equals(translated) || now - sent.time >= RESEND_MILLIS) {
            actionBar.send(player, translated);
            lastSent.put(player.getUniqueId(), new Sent(translated, now));
        }

        // Restart the timer even when the message was not sent again: the player still looks at the entity.
        scheduleClear(player);
    }

    /**
     * Clears the action bar after "Display Time" ticks. The client otherwise shows it for about 3 seconds.
     * Each new message restarts the timer, so the bar stays while the player keeps looking at an entity.
     */
    private void scheduleClear(Player player) {
        UUID uuid = player.getUniqueId();
        if (plugin.configStore.displayTime <= 0) {
            cancelClear(uuid);
            return;
        }

        // Replaced in one atomic step, because on Folia two threads can send to the same player.
        Scheduler.Task[] task = new Scheduler.Task[1];
        clearTasks.compute(uuid, (key, previous) -> {
            if (previous != null) previous.cancel();
            task[0] = Scheduler.runLaterFor(plugin, player, () -> {
                // A newer message replaced this task: do not clear the bar.
                if (!clearTasks.remove(uuid, task[0])) return;
                lastSent.remove(uuid);
                // A space, not an empty message: some clients reject an empty text component.
                if (player.isOnline()) actionBar.send(player, " ");
            }, plugin.configStore.displayTime);
            return task[0];
        });
    }

    public void cancelClear(UUID uuid) {
        Scheduler.Task task = clearTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    /**
     * Removes the data of a player who left.
     */
    public void forget(UUID uuid) {
        cancelClear(uuid);
        lastSent.remove(uuid);
    }

    public void setLastDamage(LivingEntity entity, double damage) {
        lastDamage.put(entity, damage);
    }

    /**
     * The last damage that the entity took, for {opponentlastdamage}.
     */
    public double getLastDamage(LivingEntity entity) {
        Double damage = lastDamage.get(entity);
        return damage != null ? damage : entity.getLastDamage();
    }

    public boolean isDisabled(Location location) {
        if (!plugin.worldGuardEnabled) {
            return false;
        }

        for (String region : worldGuard.getRegionIds(location)) {
            if (plugin.configStore.regions.contains(region)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the player may see the health of the entity. If the player turned ActionHealth
     * off, sends the toggle message and returns false.
     */
    public boolean matchesRequirements(Player player, Entity damaged) {
        if (!matchesFilters(player, damaged))
            return false;

        if (plugin.toggles.isToggled(player.getUniqueId())) {
            sendToggleMessage(player, false);
            return false;
        }

        return true;
    }

    /**
     * Returns true if the config lets the player see the health of the entity. Does not check
     * whether the player turned ActionHealth off.
     */
    public boolean matchesFilters(Player player, Entity damaged) {
        if (damaged.getType().name().equals("ARMOR_STAND"))
            return false;

        if (player.getWorld() != damaged.getWorld())
            return false;

        if (damaged instanceof Player) {
            if (!plugin.configStore.showPlayers && !damaged.hasMetadata("NPC"))
                return false;
        } else {
            if (!plugin.configStore.showMobs)
                return false;
        }

        if (!plugin.configStore.showNPC && damaged.hasMetadata("NPC"))
            return false;

        if (!plugin.configStore.showMiniaturePets && damaged.hasMetadata("MiniaturePet"))
            return false;

        if (plugin.healthUtil.isDisabled(player.getLocation()))
            return false;

        if (plugin.configStore.worlds.contains(player.getWorld().getName()))
            return false;

        if (plugin.configStore.usePerms && !player.hasPermission("ActionHealth.Health"))
            return false;

        return !player.getUniqueId().equals(damaged.getUniqueId());
    }

    public boolean hasToggleMessage() {
        return plugin.configStore.toggleMessage != null && !plugin.configStore.toggleMessage.isEmpty();
    }

    /**
     * Sends the "Toggle Message" to a player who turned ActionHealth off, if the config has one.
     */
    public void sendToggleMessage(Player player, boolean repeated) {
        if (hasToggleMessage()) {
            sendActionBar(player, replacePlaceholders(plugin.configStore.toggleMessage, "name", player.getName()), repeated);
        }
    }

    public boolean isBlacklisted(Entity entity, String name) {
        if (plugin.mythicMobsEnabled) {
            String mythicName = new MythicMobsSupport().getMythicName(entity);
            if (mythicName != null && plugin.configStore.blacklist.contains(mythicName)) {
                return true;
            }
        }

        return plugin.configStore.blacklist.contains(name);
    }

    public boolean isWhiteListed(Entity entity, String name) {
        if (plugin.mythicMobsEnabled) {
            String mythicName = new MythicMobsSupport().getMythicName(entity);
            if (mythicName != null && plugin.configStore.whitelist.contains(mythicName)) {
                return true;
            }
        }

        return plugin.configStore.whitelist.contains(name);
    }

    public void setActionToggle(UUID uuid, boolean disable) {
        plugin.toggles.setToggled(uuid, disable, plugin.configStore.rememberToggle);
    }

    public boolean isActionDisabled(UUID uuid) {
        return plugin.toggles.isToggled(uuid);
    }

    private static final class Sent {
        final String message;
        final long time;

        Sent(String message, long time) {
            this.message = message;
            this.time = time;
        }
    }
}
