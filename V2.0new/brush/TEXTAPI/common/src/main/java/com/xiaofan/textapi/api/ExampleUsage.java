package com.xiaofan.textapi.api;

import com.xiaofan.textapi.api.chat.ChatMessageAPI_1_16_5;
import com.xiaofan.textapi.api.player.PlayerEventAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;

/**
 * TEXTAPI 使用示例 - 1.16.5 版本。
 * 展示如何使用玩家事件和聊天消息 API。
 */
public final class ExampleUsage {

    private ExampleUsage() {}

    /**
     * 示例：注册玩家事件监听器并发送欢迎消息。
     */
    public static void registerExampleListeners() {
        // 注册玩家加入事件
        PlayerEventAPI_1_16_5.onPlayerJoin(player -> {
            String playerName = player.getName().getString();

            // 发送欢迎消息
            ChatMessageAPI_1_16_5.sendMessages(player,
                "§6━━━━━━━ §b欢迎§6 ━━━━━━━",
                "§e欢迎加入服务器，§a" + playerName + "§e！",
                "§7祝你游戏愉快！",
                "§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );

            System.out.println("[示例] 玩家加入: " + playerName);
        });

        // 注册玩家退出事件
        PlayerEventAPI_1_16_5.onPlayerQuit(player -> {
            String playerName = player.getName().getString();
            System.out.println("[示例] 玩家退出: " + playerName);
        });
    }
}
