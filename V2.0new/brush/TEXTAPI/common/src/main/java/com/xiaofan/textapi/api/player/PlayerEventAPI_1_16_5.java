package com.xiaofan.textapi.api.player;

import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 玩家事件抽象 API - 1.16.5 版本。
 * 直接使用 1.16.5 的 Minecraft API，不依赖反射。
 * 仅依赖 Minecraft 原生类，不依赖任何第三方库。
 */
public final class PlayerEventAPI_1_16_5 {

    private static final List<Consumer<ServerPlayerEntity>> joinListeners = new ArrayList<>();
    private static final List<Consumer<ServerPlayerEntity>> quitListeners = new ArrayList<>();

    private PlayerEventAPI_1_16_5() {}

    /**
     * 注册玩家加入事件监听器。
     * 
     * @param listener 监听器，接收 ServerPlayerEntity
     */
    public static void onPlayerJoin(Consumer<ServerPlayerEntity> listener) {
        if (listener != null) {
            joinListeners.add(listener);
        }
    }

    /**
     * 注册玩家退出事件监听器。
     * 
     * @param listener 监听器，接收 ServerPlayerEntity
     */
    public static void onPlayerQuit(Consumer<ServerPlayerEntity> listener) {
        if (listener != null) {
            quitListeners.add(listener);
        }
    }

    /**
     * 触发玩家加入事件（由平台特定代码调用）。
     * 
     * @param player 玩家对象
     */
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

    /**
     * 触发玩家退出事件（由平台特定代码调用）。
     * 
     * @param player 玩家对象
     */
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
