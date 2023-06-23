package com.zeshanaslam.actionhealth.utils;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.api.HealthSendEvent;
import com.zeshanaslam.actionhealth.support.*;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.region.IWrappedRegion;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class HealthUtil {

    private Main plugin;

    public HealthUtil(Main plugin) {
        this.plugin = plugin;
    }

    public void sendHealth(Player receiver, LivingEntity entity, double health) {
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

            new BukkitRunnable() {
                public void run() {
                    String output = getOutput(entity.getHealth(), plugin.configStore.healthMessage, receiver, entity);

                    if (output != null)
                        sendActionBar(receiver, output);
                }
            }.runTaskLater(plugin, plugin.configStore.delayTick);
        } else {
            String output = getOutput(health, plugin.configStore.healthMessage, receiver, entity);

            if (output != null)
                sendActionBar(receiver, output);
        }
    }

    public String getOutput(double health, String output, Player receiver, LivingEntity entity) {
        double maxHealth = entity.getMaxHealth();

        if (health < 0.0 || entity.isDead()) health = 0.0;

        String name = getName(entity, receiver);
        if (plugin.healthUtil.isBlacklisted(entity, name)) return null;
        if (plugin.configStore.stripName) name = ChatColor.stripColor(name);
        if (plugin.configStore.isUsingWhiteList()) {
            if (!plugin.healthUtil.isWhiteListed(entity, name)) {
                return null;
            }
        }

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
                output = be.maximvdw.placeholderapi.PlaceholderAPI.replacePlaceholders(player, output);
            }

            if (plugin.configStore.hasPlaceholderAPI) {
                output = replacePlaceholders(output, "ATTACKEDPLAYER_", "");
                output = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, output);
            }
        } else {
            if (!plugin.configStore.healthMessageOther.isEmpty()) {
                output = plugin.configStore.healthMessageOther;
            }

            output = replacePlaceholders(output, "displayname", name);
        }

        // Set placeholders as receiver
        if (plugin.configStore.hasMVdWPlaceholderAPI) {
            output = be.maximvdw.placeholderapi.PlaceholderAPI.replacePlaceholders(receiver, output);
        }

        if (plugin.configStore.hasPlaceholderAPI) {
            output = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(receiver, output);
        }

        output = replacePlaceholders(output, "name", name);
        output = replacePlaceholders(output, "health", String.valueOf((int) health));
        output = replacePlaceholders(output, "maxhealth", String.valueOf((int) maxHealth));
        output = replacePlaceholders(output, "percenthealth", String.valueOf((int) ((health / maxHealth) * 100.0)));
        output = replacePlaceholders(output, "opponentlastdamage", String.valueOf((int) entity.getLastDamage()));

        if (output.contains("usestyle")) {
            StringBuilder style = new StringBuilder();
            int left = getLimitHealth(maxHealth);
            double heart = maxHealth / getLimitHealth(maxHealth);
            double halfHeart = heart / 2;
            double tempHealth = health;

            if (maxHealth != health && health >= 0 && !entity.isDead()) {
                for (int i = 0; i < getLimitHealth(maxHealth); i++) {
                    if (tempHealth - heart > 0) {
                        tempHealth = tempHealth - heart;

                        style.append(plugin.configStore.filledHeartIcon);
                        left--;
                    } else {
                        break;
                    }
                }

                if (tempHealth > halfHeart) {
                    style.append(plugin.configStore.filledHeartIcon);
                    left--;
                } else if (tempHealth > 0 && tempHealth <= halfHeart) {
                    style.append(plugin.configStore.halfHeartIcon);
                    left--;
                }
            }

            if (maxHealth != health) {
                for (int i = 0; i < left; i++) {
                    style.append(plugin.configStore.emptyHeartIcon);
                }
            } else {
                for (int i = 0; i < left; i++) {
                    style.append(plugin.configStore.filledHeartIcon);
                }
            }

            output = replacePlaceholders(output, "usestyle", style.toString());
        }

        HealthSendEvent healthSendEvent = new HealthSendEvent(receiver, entity, output);
        Bukkit.getPluginManager().callEvent(healthSendEvent);
        if (healthSendEvent.isCancelled())
            output = null;
        else
            output = healthSendEvent.getMessage();

        return output;
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
        try {
            if (entity.getCustomName() == null)
                getName = entity.getClass().getMethod("getName", (Class<?>[]) null);
        } catch (NoSuchMethodException | SecurityException ignored) {
        }

        if (getName != null) {
            try {
                name = (String) getName.invoke(entity, (Object[]) null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                name = capitalizeFully(entity.getType().name().replace("_", ""));
            }
        } else {
            name = capitalizeFully(entity.getType().name().replace("_", ""));
        }

        return name;
    }

    private String capitalizeFully(String words) {
        words = words.toLowerCase();
        return WordUtils.capitalizeFully(words);
    }

    public void sendActionBar(Player player, String message) {
        message = ChatColor.translateAlternateColorCodes('&', message);

        try {
            if (plugin.configStore.mcVersion.contains("v1_17") || plugin.configStore.mcVersion.contains("v1_18") || plugin.configStore.mcVersion.contains("v1_19") || plugin.configStore.mcVersion.contains("v1_20")) {
                new NewAction(player, message);
            } else if (plugin.configStore.mcVersion.contains("v1_16")) {
                new PreAction(player, message);
            } else if (plugin.configStore.mcVersion.equals("v1_12_R1") || plugin.configStore.mcVersion.startsWith("v1_13") || plugin.configStore.mcVersion.startsWith("v1_14_") || plugin.configStore.mcVersion.startsWith("v1_15_")) {
                new LegacyPreAction(player, message);
            } else if (!(plugin.configStore.mcVersion.equalsIgnoreCase("v1_8_R1") || plugin.configStore.mcVersion.contains("v1_7_"))) {
                Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + plugin.configStore.mcVersion + ".entity.CraftPlayer");
                Object p = c1.cast(player);
                Object ppoc;
                Class<?> c4 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".PacketPlayOutChat");
                Class<?> c5 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".Packet");

                Class<?> c2 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".ChatComponentText");
                Class<?> c3 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".IChatBaseComponent");
                Object o = c2.getConstructor(new Class<?>[]{String.class}).newInstance(message);
                ppoc = c4.getConstructor(new Class<?>[]{c3, byte.class}).newInstance(o, (byte) 2);

                Method getHandle = c1.getDeclaredMethod("getHandle");
                Object handle = getHandle.invoke(p);

                Field fieldConnection = handle.getClass().getDeclaredField("playerConnection");
                Object playerConnection = fieldConnection.get(handle);

                Method sendPacket = playerConnection.getClass().getDeclaredMethod("sendPacket", c5);
                sendPacket.invoke(playerConnection, ppoc);
            } else {
                Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + plugin.configStore.mcVersion + ".entity.CraftPlayer");
                Object p = c1.cast(player);
                Object ppoc;
                Class<?> c4 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".PacketPlayOutChat");
                Class<?> c5 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".Packet");

                Class<?> c2 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".ChatSerializer");
                Class<?> c3 = Class.forName("net.minecraft.server." + plugin.configStore.mcVersion + ".IChatBaseComponent");
                Method m3 = c2.getDeclaredMethod("a", String.class);
                Object cbc = c3.cast(m3.invoke(c2, "{\"text\": \"" + message + "\"}"));
                ppoc = c4.getConstructor(new Class<?>[]{c3, byte.class}).newInstance(cbc, (byte) 2);

                Method getHandle = c1.getDeclaredMethod("getHandle");
                Object handle = getHandle.invoke(p);

                Field fieldConnection = handle.getClass().getDeclaredField("playerConnection");
                Object playerConnection = fieldConnection.get(handle);

                Method sendPacket = playerConnection.getClass().getDeclaredMethod("sendPacket", c5);
                sendPacket.invoke(playerConnection, ppoc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isDisabled(Location location) {
        if (!plugin.worldGuardEnabled) {
            return false;
        }

        for (IWrappedRegion region : WorldGuardWrapper.getInstance().getRegions(location)) {
            if (plugin.configStore.regions.contains(region.getId())) {
                return true;
            }
        }

        return false;
    }

    public boolean matchesRequirements(Player player, Entity damaged) {
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

        if (player.getUniqueId() == damaged.getUniqueId())
            return false;

        if (plugin.toggle.contains(player.getUniqueId())) {
            sendMessage(player);
            return false;
        }

        return true;
    }

    private void sendMessage(Player player) {
        if (plugin.configStore.toggleMessage != null && !plugin.configStore.toggleMessage.equals("")) {
            plugin.healthUtil.sendActionBar(player, replacePlaceholders(plugin.configStore.toggleMessage, "name", player.getName()));
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
        if (disable) {
            plugin.toggle.add(uuid);
        } else {
            plugin.toggle.remove(uuid);
        }
    }

    public boolean isActionDisabled(UUID uuid) {
        return plugin.toggle.contains(uuid);
    }
}
