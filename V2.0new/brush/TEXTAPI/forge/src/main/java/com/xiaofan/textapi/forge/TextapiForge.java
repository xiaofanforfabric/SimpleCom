package com.xiaofan.textapi.forge;

import com.xiaofan.textapi.Textapi;
import com.xiaofan.textapi.api.player.PlayerEventAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Textapi.MOD_ID)
public final class TextapiForge {
    
    public TextapiForge() {
        // 初始化 TEXTAPI 抽象 API
        Textapi.init();
        
        // 注册事件监听器
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            PlayerEventAPI_1_16_5.firePlayerJoin((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            PlayerEventAPI_1_16_5.firePlayerQuit((ServerPlayerEntity) event.getPlayer());
        }
    }
}
