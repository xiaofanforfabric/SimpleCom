package com.xiaofan.textapiFabric1_16_5.api.chat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Component;

/**
 * 聊天消息发送抽象 API - 1.21.9 版本。
 */
public final class ChatMessageAPI_1_21_9 {

    private ChatMessageAPI_1_21_9() {}

    public static void sendMessage(ServerPlayerEntity player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }

        Component component = Component.literal(message);
        player.sendMessage(component);
    }

    public static void sendMessages(ServerPlayerEntity player, String... messages) {
        if (player == null || messages == null) return;
        for (String msg : messages) {
            sendMessage(player, msg);
        }
    }

    public static void sendMessage(ServerPlayerEntity player, Component component) {
        if (player == null || component == null) return;
        player.sendMessage(component);
    }
}
