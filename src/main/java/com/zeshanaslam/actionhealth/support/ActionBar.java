package com.zeshanaslam.actionhealth.support;

import com.zeshanaslam.actionhealth.utils.Reflect;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends action bar messages on any server version without a version check.
 * <p>
 * Methods are tried in this order and the first one that exists is used:
 * <ol>
 *     <li>Spigot API {@code Player.Spigot#sendMessage(ChatMessageType, BaseComponent...)} (1.9.2 and later).</li>
 *     <li>Paper API {@code Player#sendActionBar(String)}.</li>
 *     <li>A chat packet sent through NMS reflection (1.8 to 1.9, before the Spigot API existed).</li>
 * </ol>
 * If a method fails before it has ever worked, it is logged and the next method is used.
 * Later failures (for example another plugin throwing for one player) are logged at most
 * once a minute and the method stays in use.
 */
public class ActionBar {

    private final Logger logger;
    private Sender sender;
    private boolean senderWorked;
    private int nextMethod;
    private boolean noMethodLogged;
    private long lastFailureLog;

    public ActionBar(Logger logger) {
        this.logger = logger;
    }

    // Synchronized, because Folia sends messages from many threads and the first send picks the method.
    public synchronized void send(Player player, String message) {
        while (true) {
            if (sender == null) {
                sender = findSender(player);
                if (sender == null) {
                    if (!noMethodLogged) {
                        noMethodLogged = true;
                        logger.warning("Could not find a way to send action bar messages on " + Bukkit.getVersion()
                                + ". Please report this at https://github.com/zeshan321/ActionHealth/issues");
                    }
                    return;
                }
            }

            try {
                sender.send(player, message);
                senderWorked = true;
                return;
            } catch (Throwable e) {
                Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
                if (senderWorked) {
                    long now = System.currentTimeMillis();
                    if (now - lastFailureLog > 60000) {
                        lastFailureLog = now;
                        logger.log(Level.WARNING, "Could not send action bar to " + player.getName(), cause);
                    }
                    return;
                }

                logger.log(Level.WARNING, "Action bar method '" + sender.name() + "' does not work here, trying the next one", cause);
                sender = null;
            }
        }
    }

    private Sender findSender(Player player) {
        while (nextMethod < 3) {
            Sender found = null;
            switch (nextMethod++) {
                case 0:
                    found = SpigotSender.create();
                    break;
                case 1:
                    found = PaperSender.create();
                    break;
                case 2:
                    found = PacketSender.create(player);
                    break;
            }

            if (found != null) return found;
        }

        return null;
    }

    private interface Sender {
        String name();

        void send(Player player, String message) throws Exception;
    }

    private static class SpigotSender implements Sender {
        private final Method sendMessage;
        private final Object actionBarType;

        private SpigotSender(Method sendMessage, Object actionBarType) {
            this.sendMessage = sendMessage;
            this.actionBarType = actionBarType;
        }

        static Sender create() {
            Class<?> messageType = Reflect.findClass("net.md_5.bungee.api.ChatMessageType");
            Object actionBarType = Reflect.getStaticField(messageType, "ACTION_BAR");
            Method sendMessage = Reflect.findMethod(Player.Spigot.class, "sendMessage", messageType, BaseComponent[].class);

            if (actionBarType == null || sendMessage == null) return null;
            return new SpigotSender(sendMessage, actionBarType);
        }

        @Override
        public String name() {
            return "Spigot API";
        }

        @Override
        public void send(Player player, String message) throws Exception {
            sendMessage.invoke(player.spigot(), actionBarType, TextComponent.fromLegacyText(message));
        }
    }

    private static class PaperSender implements Sender {
        private final Method sendActionBar;

        private PaperSender(Method sendActionBar) {
            this.sendActionBar = sendActionBar;
        }

        static Sender create() {
            Method sendActionBar = Reflect.findMethod(Player.class, "sendActionBar", String.class);
            return sendActionBar == null ? null : new PaperSender(sendActionBar);
        }

        @Override
        public String name() {
            return "Paper API";
        }

        @Override
        public void send(Player player, String message) throws Exception {
            sendActionBar.invoke(player, message);
        }
    }

    /**
     * For 1.8 to 1.9, before the Spigot action bar API existed. The NMS package is read from the
     * player handle class, so no version string is parsed.
     */
    private static class PacketSender implements Sender {
        private final Method getHandle;
        private final Field playerConnection;
        private final Method sendPacket;
        private final Constructor<?> componentText;
        private final Constructor<?> chatPacket;

        private PacketSender(Method getHandle, Field playerConnection, Method sendPacket,
                             Constructor<?> componentText, Constructor<?> chatPacket) {
            this.getHandle = getHandle;
            this.playerConnection = playerConnection;
            this.sendPacket = sendPacket;
            this.componentText = componentText;
            this.chatPacket = chatPacket;
        }

        static Sender create(Player player) {
            try {
                Method getHandle = Reflect.findMethod(player.getClass(), "getHandle");
                if (getHandle == null) return null;

                Object handle = getHandle.invoke(player);
                String nms = handle.getClass().getPackage().getName();

                Class<?> component = Reflect.findClass(nms + ".IChatBaseComponent");
                Class<?> text = Reflect.findClass(nms + ".ChatComponentText");
                Class<?> packet = Reflect.findClass(nms + ".Packet");
                Class<?> chat = Reflect.findClass(nms + ".PacketPlayOutChat");
                if (component == null || text == null || packet == null || chat == null) return null;

                Field playerConnection = handle.getClass().getField("playerConnection");
                Method sendPacket = Reflect.findMethod(playerConnection.getType(), "sendPacket", packet);
                if (sendPacket == null) return null;

                // Position 2 is the action bar.
                return new PacketSender(getHandle, playerConnection, sendPacket,
                        text.getConstructor(String.class), chat.getConstructor(component, byte.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }

        @Override
        public String name() {
            return "NMS packet";
        }

        @Override
        public void send(Player player, String message) throws Exception {
            Object packet = chatPacket.newInstance(componentText.newInstance(message), (byte) 2);
            Object connection = playerConnection.get(getHandle.invoke(player));
            sendPacket.invoke(connection, packet);
        }
    }
}
