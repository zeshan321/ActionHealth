package com.zeshanaslam.actionhealth;

import net.minecraft.server.v1_12_R1.ChatMessageType;
import net.minecraft.server.v1_12_R1.IChatBaseComponent;
import net.minecraft.server.v1_12_R1.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class PreAction {

    public PreAction(Player player, String message) {
        IChatBaseComponent cbc = IChatBaseComponent.ChatSerializer.a("{\"text\": \"" + message + "\"}");
        PacketPlayOutChat packetPlayOutChat = new PacketPlayOutChat(cbc, ChatMessageType.GAME_INFO);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetPlayOutChat);
    }
}
