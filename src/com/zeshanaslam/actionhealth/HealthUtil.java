package com.zeshanaslam.actionhealth;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HealthUtil {

    private Main plugin;

    public HealthUtil(Main plugin) {
        this.plugin = plugin;
    }

    private void sendActionBar(Player player, String message) {
        if (player.hasMetadata("NPC")) {
            return;
        }

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
}
