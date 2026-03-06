package com.xiaofan.server.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.xiaofan.server.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModForge {
    private static final ServerPayloadHandlerForge payloadHandler = new ServerPayloadHandlerForge();

    public ExampleModForge() {
        EventBuses.registerModEventBus(ExampleMod.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        ExampleMod.init();

        // 游戏事件 (PlayerLoggedInEvent, ServerTickEvent 等) 在 Forge 事件总线上
        MinecraftForge.EVENT_BUS.register(payloadHandler);
        payloadHandler.register();
    }
}
