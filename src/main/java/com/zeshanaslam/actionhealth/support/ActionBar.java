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
 *     <li>Adventure API {@code Audience#sendActionBar(Component)} (Paper 1.16.5 and later). Paper deprecated
 *     the BungeeCord chat API that the Spigot method uses, so this method is ready if Paper removes it.</li>
 *     <li>Paper API {@code Player#sendActionBar(String)}.</li>
 *     <li>A chat packet sent through NMS reflection (1.8 to 1.9, before the Spigot API existed).</li>
 * </ol>
 * If a method fails before it has ever worked, it is logged and the next method is used.
 * Later failures (for example another plugin throwing for one player) are logged at most
 * once a minute and the method stays in use.
 * <p>
 * The tests start with a later method through the system property {@value #METHOD_PROPERTY}
 * (spigot, adventure, paper or packet), to check a method that the server would not use first.
 */
public class ActionBar {

    static final String METHOD_PROPERTY = "actionhealth.actionbar";
    private static final String[] METHODS = {"spigot", "adventure", "paper", "packet"};

    private final Logger logger;
    // Set once a method has worked. Volatile, so Folia threads can send without the lock.
    private volatile Sender working;
    private Sender sender;
    private int nextMethod;
    private boolean noMethodLogged;
    private volatile long lastFailureLog;

    public ActionBar(Logger logger) {
        this.logger = logger;
        String method = System.getProperty(METHOD_PROPERTY);
        if (method != null) {
            int index = java.util.Arrays.asList(METHODS).indexOf(method.toLowerCase(java.util.Locale.ROOT));
            if (index >= 0) {
                nextMethod = index;
            } else {
                logger.warning(METHOD_PROPERTY + "=" + method + " is not a method. Use one of " + String.join(", ", METHODS) + ".");
            }
        }
    }

    public void send(Player player, String message) {
        Sender current = working;
        if (current == null) {
            findAndSend(player, message);
            return;
        }

        try {
            current.send(player, message);
        } catch (Throwable e) {
            logFailure(player, e);
        }
    }

    private void logFailure(Player player, Throwable e) {
        Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        long now = System.currentTimeMillis();
        if (now - lastFailureLog > 60000) {
            lastFailureLog = now;
            logger.log(Level.WARNING, "Could not send action bar to " + player.getName(), cause);
        }
    }

    // Synchronized, because Folia sends messages from many threads and the first send picks the method.
    private synchronized void findAndSend(Player player, String message) {
        if (working != null) {
            send(player, message);
            return;
        }

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
                working = sender;
                logger.info("Sending action bars with the " + sender.name() + ".");
                return;
            } catch (Throwable e) {
                Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
                logger.log(Level.WARNING, "Action bar method '" + sender.name() + "' does not work here, trying the next one", cause);
                sender = null;
            }
        }
    }

    private Sender findSender(Player player) {
        while (nextMethod < METHODS.length) {
            Sender found = null;
            switch (nextMethod++) {
                case 0:
                    found = SpigotSender.create();
                    break;
                case 1:
                    found = AdventureSender.create(player);
                    break;
                case 2:
                    found = PaperSender.create();
                    break;
                case 3:
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

    /**
     * Paper's own chat API. The legacy text keeps its colors, including the Bukkit format for hex
     * colors that {@link com.zeshanaslam.actionhealth.utils.Colors} writes.
     */
    private static class AdventureSender implements Sender {
        private final Object serializer;
        private final Method deserialize;
        private final Method sendActionBar;

        private AdventureSender(Object serializer, Method deserialize, Method sendActionBar) {
            this.serializer = serializer;
            this.deserialize = deserialize;
            this.sendActionBar = sendActionBar;
        }

        static Sender create(Player player) {
            Class<?> audience = Reflect.findClass("net.kyori.adventure.audience.Audience");
            Class<?> component = Reflect.findClass("net.kyori.adventure.text.Component");
            Class<?> legacy = Reflect.findClass("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Class<?> builder = Reflect.findClass("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer$Builder");
            // Another plugin can include its own copy of Adventure. Only the server's copy works with its players.
            if (audience == null || component == null || legacy == null || builder == null || !audience.isInstance(player)) return null;

            try {
                Object build = legacy.getMethod("builder").invoke(null);
                build = builder.getMethod("character", char.class).invoke(build, '\u00A7');
                build = builder.getMethod("hexColors").invoke(build);
                build = builder.getMethod("useUnusualXRepeatedCharacterHexFormat").invoke(build);
                Object serializer = builder.getMethod("build").invoke(build);
                Method deserialize = legacy.getMethod("deserialize", String.class);
                Method sendActionBar = audience.getMethod("sendActionBar", component);
                return new AdventureSender(serializer, deserialize, sendActionBar);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }

        @Override
        public String name() {
            return "Adventure API";
        }

        @Override
        public void send(Player player, String message) throws Exception {
            sendActionBar.invoke(player, deserialize.invoke(serializer, message));
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
