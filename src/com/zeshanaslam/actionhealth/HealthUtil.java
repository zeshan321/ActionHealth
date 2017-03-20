package com.zeshanaslam.actionhealth;

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

    public void sendHealth(Player player, LivingEntity entity, double health) {
        String name;
        double maxHealth = entity.getMaxHealth();

        if (entity.getCustomName() == null) {
            name = entity.getName();
        } else {
            name = entity.getCustomName();
        }

        if (plugin.settingsManager.stripName) name = ChatColor.stripColor(name);
        if (plugin.settingsManager.translate.containsKey(entity.getName()))
            name = plugin.settingsManager.translate.get(entity.getName());

        String output = plugin.settingsManager.healthMessage;
        output = output.replace("{name}", name);
        output = output.replace("{health}", String.valueOf(health));
        output = output.replace("{maxhealth}", String.valueOf(maxHealth));

        if (output.contains("{usestyle}")) {
            String style = "";
            int left = 10;
            double heart = maxHealth / 10;
            double tempHealth = health;

            if (maxHealth != health && health >= 0 && !entity.isDead()) {
                for (int i = 0; i < 10; i++) {
                    if (tempHealth - heart > 0) {
                        tempHealth = tempHealth - heart;

                        style = style + plugin.settingsManager.filledHeartIcon;
                        left--;
                    }
                }

                if (tempHealth >= 0) {
                    style = style + plugin.settingsManager.halfHeartIcon;
                    left--;
                }
            }

            for (int i = 0; i < left; i++) {
                style = style + plugin.settingsManager.emptyHeartIcon;
            }

            output = output.replace("{usestyle}", style);
        }

        if (plugin.settingsManager.delay) {
            String finalOutput = output;

            new BukkitRunnable() {
                public void run() {
                    sendActionBar(player, finalOutput);
                }
            }.runTaskLater(plugin, 1L);
        } else {
            sendActionBar(player, output);
        }
    }

    private void sendActionBar(Player player, String message) {
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

            Method m1 = c1.getDeclaredMethod("getHandle");
            Object h = m1.invoke(p);
            Field f1 = h.getClass().getDeclaredField("playerConnection");
            Object pc = f1.get(h);
            Method m5 = pc.getClass().getDeclaredMethod("sendPacket", c5);
            m5.invoke(pc, ppoc);
        } catch (Exception ex) {
            ex.printStackTrace();
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
