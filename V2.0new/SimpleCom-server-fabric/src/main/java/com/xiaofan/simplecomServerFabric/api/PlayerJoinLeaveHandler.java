package com.xiaofan.simplecomServerFabric.api;

import com.xiaofan.ws.VerificationCodeStore;
import com.xiaofan.ws.WsConnectionRegistry;
import dev.neuralnexus.taterlib.event.api.PlayerEvents;
import dev.neuralnexus.taterlib.player.SimplePlayer;

/**
 * 玩家加入/退出：使用 TaterLib 跨版本 API（PlayerEvents.LOGIN / LOGOUT），不直接依赖 MC 类。
 */
public final class PlayerJoinLeaveHandler {

    private PlayerJoinLeaveHandler() {}

    public static void register() {
        PlayerEvents.LOGIN.register(event -> {
            SimplePlayer player = event.player();
            if (player == null) return;
            String name = player.name();
            String code = VerificationCodeStore.getInstance().generate(name);
            if (code == null) return;

            player.sendMessage("§6━━━━━━━ §bSimpleCom§6 ━━━━━━━");
            player.sendMessage("§eVoice code | 语音验证码: §a§l" + code);
            player.sendMessage("§7Enter in voice client. Invalid once you connect or leave.");
            player.sendMessage("§7在语音客户端中输入。连接语音或退出游戏后立即失效。");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            System.out.println("[SimpleCom] 已为 " + name + " 生成语音验证码 | Voice code generated for: " + name);
        });

        PlayerEvents.LOGOUT.register(event -> {
            SimplePlayer player = event.player();
            if (player == null) return;
            String name = player.name();
            VerificationCodeStore.getInstance().remove(name);
            WsConnectionRegistry.getInstance().closeForUser(name);
        });
    }
}
