package com.xiaofan.textapi.fabric;

import com.xiaofan.textapi.Textapi;
import com.xiaofan.textapi.api.player.PlayerEventAPI_1_16_5;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TextapiFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 初始化 TEXTAPI 抽象 API
        Textapi.init();
        
        // 注册 Fabric 事件监听器
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player != null) {
                PlayerEventAPI_1_16_5.firePlayerJoin(player);
            }
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player != null) {
                PlayerEventAPI_1_16_5.firePlayerQuit(player);
            }
        });
    }
}
