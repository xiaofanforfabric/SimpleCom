package com.xiaofan.ws;

import java.io.IOException;

/**
 * 向已认证玩家的 WebSocket 连接推送文本消息的 API。
 * API for pushing text messages to authenticated players via their WebSocket connection.
 *
 * <pre>
 * // 示例用法 | Example usage:
 * SeedMessageResult result = SeedMessageApi.seedMessageToPlayer("Hello!", "Steve");
 * switch (result) {
 *     case DONE:  // 发送成功 | Message sent
 *     case NULL:  // 玩家未连接 WS | Player not connected via WS
 *     case ERROR: // 发送失败（IO 异常）| Send failed (IO exception)
 * }
 * </pre>
 */
public final class SeedMessageApi {

    private SeedMessageApi() {}

    /**
     * 向指定玩家的 WebSocket 连接发送文本消息。
     * Send a text message to a specific player's WebSocket connection.
     *
     * @param text     消息文本（不可为 null）| Message text (must not be null)
     * @param username MC 用户名，大小写不敏感 | MC username, case-insensitive
     * @return {@link SeedMessageResult#DONE} 成功，
     *         {@link SeedMessageResult#NULL} 玩家未连接 WS，
     *         {@link SeedMessageResult#ERROR} 参数非法或 IO 异常
     */
    public static SeedMessageResult seedMessageToPlayer(String text, String username) {
        if (text == null || username == null || username.isEmpty()) {
            return SeedMessageResult.ERROR;
        }

        WsConnectionRegistry registry = WsConnectionRegistry.getInstance();
        WsSender sender = registry.getSender(username);

        if (sender == null || !sender.isOpen()) {
            if (sender != null) registry.unregister(username);
            return SeedMessageResult.NULL;
        }

        try {
            sender.sendText(text);
            return SeedMessageResult.DONE;
        } catch (IOException e) {
            registry.unregister(username);
            return SeedMessageResult.ERROR;
        }
    }
}
