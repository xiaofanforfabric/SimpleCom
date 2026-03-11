package com.xiaofan.textapiFabric1_16_5.api.chat;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/**
 * 聊天消息发送抽象 API - 1.18.2 版本。
 */
public final class ChatMessageAPI_1_18_2 {

    private ChatMessageAPI_1_18_2() {}

    public static void sendMessage(ServerPlayerEntity player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }

        ITextComponent component = new StringTextComponent(message);
        player.sendMessage(component);
    }

    public static void sendMessages(ServerPlayerEntity player, String... messages) {
        if (player == null || messages == null) return;
        for (String msg : messages) {
            sendMessage(player, msg);
        }
    }

    public static void sendMessage(ServerPlayerEntity player, ITextComponent component) {
        if (player == null || component == null) return;
        player.sendMessage(component);
    }
}
