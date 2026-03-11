package com.xiaofan.textapiFabric1_16_5.api.player;

import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 玩家事件抽象 API - 1.18.2 版本。
 */
public final class PlayerEventAPI_1_18_2 {

    private static final List<Consumer<ServerPlayerEntity>> joinListeners = new ArrayList<>();
    private static final List<Consumer<ServerPlayerEntity>> quitListeners = new ArrayList<>();

    private PlayerEventAPI_1_18_2() {}

    public static void onPlayerJoin(Consumer<ServerPlayerEntity> listener) {
        if (listener != null) {
            joinListeners.add(listener);
        }
    }

    public static void onPlayerQuit(Consumer<ServerPlayerEntity> listener) {
        if (listener != null) {
            quitListeners.add(listener);
        }
    }

    public static void firePlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        for (Consumer<ServerPlayerEntity> listener : joinListeners) {
            try {
                listener.accept(player);
            } catch (Throwable t) {
                System.err.println("[TEXTAPI] 玩家加入事件监听器执行失败: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    public static void firePlayerQuit(ServerPlayerEntity player) {
        if (player == null) return;
        for (Consumer<ServerPlayerEntity> listener : quitListeners) {
            try {
                listener.accept(player);
            } catch (Throwable t) {
                System.err.println("[TEXTAPI] 玩家退出事件监听器执行失败: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }
}
