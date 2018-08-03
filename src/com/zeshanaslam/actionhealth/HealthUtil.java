package com.zeshanaslam.actionhealth;

import be.maximvdw.placeholderapi.PlaceholderAPI;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HealthUtil {

    private Main plugin;

    public HealthUtil(Main plugin) {
        this.plugin = plugin;
    }

    public void sendHealth(Player receiver, LivingEntity entity, double health) {
        if (plugin.settingsManager.canSee) {

            if (entity instanceof Player) {
                Player player = (Player) entity;

                if (!receiver.canSee(player)) {
                    return;
                }
            }
        }

        if (plugin.settingsManager.spectatorMode) {

            if (entity instanceof Player) {
                Player player = (Player) entity;

                // Using string version for older versions. Checking for gamemode is null because of fake player npcs.
                if (player.getGameMode() != null && player.getGameMode().name().equals("SPECTATOR")) {
                    return;
                }
            }
        }

        if (plugin.settingsManager.invisiblePotion) {
            if (entity.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                return;
            }
        }

        if (plugin.settingsManager.delay) {

            new BukkitRunnable() {
                public void run() {
                    String output = getOutput(entity.getHealth(), receiver, entity);

                    if (output != null)
                        sendActionBar(receiver, output);
                }
            }.runTaskLater(plugin, 1L);
        } else {
            String output = getOutput(health, receiver, entity);

            if (output != null)
                sendActionBar(receiver, output);
        }
    }

    private String getOutput(double health, Player receiver, LivingEntity entity) {
        String name;
        double maxHealth = entity.getMaxHealth();

        if (health < 0.0 || entity.isDead()) health = 0.0;

        if (entity.getCustomName() == null) {
            name = entity.getName();
        } else {
            name = entity.getCustomName();
        }

        if (plugin.settingsManager.blacklist.contains(name)) return null;

        if (plugin.settingsManager.stripName) name = ChatColor.stripColor(name);
        if (plugin.settingsManager.translate.containsKey(entity.getName()))
            name = plugin.settingsManager.translate.get(entity.getName());

        String output = plugin.settingsManager.healthMessage;

        if (entity instanceof Player) {
            String displayName;
            Player player = (Player) entity;

            if (player.getDisplayName() == null) {
                displayName = name;
            } else {
                displayName = player.getDisplayName();
            }

            output = output.replace("{displayname}", displayName);

            // Placeholder apis
            if (plugin.settingsManager.hasMVdWPlaceholderAPI) {
                output = be.maximvdw.placeholderapi.PlaceholderAPI.replacePlaceholders(player, output);
            }

            if (plugin.settingsManager.hasPlaceholderAPI) {
                output = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, output);
                output = me.clip.placeholderapi.PlaceholderAPI.setRelationalPlaceholders(receiver, player, output);
            }
        } else {
            if (!plugin.settingsManager.healthMessageOther.isEmpty()) {
                output = plugin.settingsManager.healthMessageOther;
            }

            output = output.replace("{displayname}", name);
        }

        output = output.replace("{name}", name);
        output = output.replace("{health}", String.valueOf((int) health));
        output = output.replace("{maxhealth}", String.valueOf((int) maxHealth));

        if (output.contains("{usestyle}")) {
            StringBuilder style = new StringBuilder();
            int left = plugin.settingsManager.limitHealth;
            double heart = maxHealth / plugin.settingsManager.limitHealth;
            double halfHeart = heart / 2;
            double tempHealth = health;

            if (maxHealth != health && health >= 0 && !entity.isDead()) {
                for (int i = 0; i < plugin.settingsManager.limitHealth; i++) {
                    if (tempHealth - heart > 0) {
                        tempHealth = tempHealth - heart;

                        style.append(plugin.settingsManager.filledHeartIcon);
                        left--;
                    } else {
                        break;
                    }
                }

                if (tempHealth > halfHeart) {
                    style.append(plugin.settingsManager.filledHeartIcon);
                    left--;
                } else if (tempHealth > 0 && tempHealth <= halfHeart) {
                    style.append(plugin.settingsManager.halfHeartIcon);
                    left--;
                }
            }

            if (maxHealth != health) {
                for (int i = 0; i < left; i++) {
                    style.append(plugin.settingsManager.emptyHeartIcon);
                }
            } else {
                for (int i = 0; i < left; i++) {
                    style.append(plugin.settingsManager.filledHeartIcon);
                }
            }

            output = output.replace("{usestyle}", style.toString());
        }

        return output;
    }

    public void sendActionBar(Player player, String message) {
        message = ChatColor.translateAlternateColorCodes('&', message);

        try {
            if (plugin.settingsManager.mcVersion.equals("v1_12_R1") || plugin.settingsManager.mcVersion.equals("v1_13_R1")) {
                new PreAction(player, message);
            } else if (!(plugin.settingsManager.mcVersion.equalsIgnoreCase("v1_8_R1") || (plugin.settingsManager.mcVersion.contains("v1_7_")))) {
                Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + plugin.settingsManager.mcVersion + ".entity.CraftPlayer");
                Object p = c1.cast(player);
                Object ppoc;
                Class<?> c4 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".PacketPlayOutChat");
                Class<?> c5 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".Packet");

                Class<?> c2 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".ChatComponentText");
                Class<?> c3 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".IChatBaseComponent");
                Object o = c2.getConstructor(new Class<?>[]{String.class}).newInstance(message);
                ppoc = c4.getConstructor(new Class<?>[]{c3, byte.class}).newInstance(o, (byte) 2);

                Method getHandle = c1.getDeclaredMethod("getHandle");
                Object handle = getHandle.invoke(p);

                Field fieldConnection = handle.getClass().getDeclaredField("playerConnection");
                Object playerConnection = fieldConnection.get(handle);

                Method sendPacket = playerConnection.getClass().getDeclaredMethod("sendPacket", c5);
                sendPacket.invoke(playerConnection, ppoc);
            } else {
                Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + plugin.settingsManager.mcVersion + ".entity.CraftPlayer");
                Object p = c1.cast(player);
                Object ppoc;
                Class<?> c4 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".PacketPlayOutChat");
                Class<?> c5 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".Packet");

                Class<?> c2 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".ChatSerializer");
                Class<?> c3 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".IChatBaseComponent");
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
        if (plugin.worldGuardPlugin == null) {
            return false;
        }

        ApplicableRegionSet applicableRegions = plugin.worldGuardPlugin.getRegionManager(location.getWorld()).getApplicableRegions(location);
        for (ProtectedRegion region : applicableRegions) {
            if (plugin.settingsManager.regions.contains(region.getId())) {
                return true;
            }
        }

        return false;
    }
}
