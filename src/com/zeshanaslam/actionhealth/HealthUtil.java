package com.zeshanaslam.actionhealth;

import be.maximvdw.placeholderapi.PlaceholderAPI;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HealthUtil {

    private Main plugin;

    public HealthUtil(Main plugin) {
        this.plugin = plugin;
    }

    public void sendHealth(Player receiver, LivingEntity entity, double health) {
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
        output = output.replace("{name}", name);
        output = output.replace("{health}", String.valueOf((int) health));
        output = output.replace("{maxhealth}", String.valueOf((int) maxHealth));

        if (entity instanceof Player) {
            String displayName;
            Player player = (Player) entity;

            if (player.getDisplayName() == null) {
                displayName = name;
            } else {
                displayName = player.getDisplayName();
            }

            output = output.replace("{displayname}", displayName);
        }

        if (output.contains("{usestyle}")) {
            String style = "";
            int left = 10;
            double heart = maxHealth / 10;
            double halfHeart = heart / 2;
            double tempHealth = health;

            if (maxHealth != health && health >= 0 && !entity.isDead()) {
                for (int i = 0; i < 10; i++) {
                    if (tempHealth - heart > 0) {
                        tempHealth = tempHealth - heart;

                        style = style + plugin.settingsManager.filledHeartIcon;
                        left--;
                    } else {
                        break;
                    }
                }

                if (tempHealth > halfHeart) {
                    style = style + plugin.settingsManager.filledHeartIcon;
                    left--;
                } else if (tempHealth > 0 && tempHealth <= halfHeart) {
                    style = style + plugin.settingsManager.halfHeartIcon;
                    left--;
                }
            }

            if (maxHealth != health) {
                for (int i = 0; i < left; i++) {
                    style = style + plugin.settingsManager.emptyHeartIcon;
                }
            } else {
                for (int i = 0; i < left; i++) {
                    style = style + plugin.settingsManager.filledHeartIcon;
                }
            }

            output = output.replace("{usestyle}", style);
        }

        if (plugin.settingsManager.placeholderAPI) {
            output = PlaceholderAPI.replacePlaceholders(receiver, output);
        }

        return output;
    }

    public void sendActionBar(Player player, String message) {
        message = ChatColor.translateAlternateColorCodes('&', message);

        try {
            Class<?> c1 = Class.forName("org.bukkit.craftbukkit." + plugin.settingsManager.mcVersion + ".entity.CraftPlayer");
            Object p = c1.cast(player);
            Object ppoc;
            Class<?> c4 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".PacketPlayOutChat");
            Class<?> c5 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".Packet");

            if (plugin.settingsManager.useOldMethods) {
                Class<?> c2 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".ChatSerializer");
                Class<?> c3 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".IChatBaseComponent");
                Method m3 = c2.getDeclaredMethod("a", String.class);
                Object cbc = c3.cast(m3.invoke(c2, "{\"text\": \"" + message + "\"}"));
                ppoc = c4.getConstructor(new Class<?>[]{c3, byte.class}).newInstance(cbc, (byte) 2);
            } else {
                Class<?> c2 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".ChatComponentText");
                Class<?> c3 = Class.forName("net.minecraft.server." + plugin.settingsManager.mcVersion + ".IChatBaseComponent");
                Object o = c2.getConstructor(new Class<?>[]{String.class}).newInstance(message);
                ppoc = c4.getConstructor(new Class<?>[]{c3, byte.class}).newInstance(o, (byte) 2);
            }

            Method getHandle = c1.getDeclaredMethod("getHandle");
            Object handle = getHandle.invoke(p);

            Field fieldConnection = handle.getClass().getDeclaredField("playerConnection");
            Object playerConnection = fieldConnection.get(handle);

            Method sendPacket = playerConnection.getClass().getDeclaredMethod("sendPacket", c5);
            sendPacket.invoke(playerConnection, ppoc);
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
