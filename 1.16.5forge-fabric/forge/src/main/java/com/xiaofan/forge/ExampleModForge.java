package com.xiaofan.forge;

import me.shedaniel.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.xiaofan.ExampleMod;
import com.xiaofan.client.SimpleComVoiceClient;
import com.xiaofan.forge.client.HandshakePayloadReceiver;
import com.xiaofan.payload.HandshakePayloadAdapter;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModForge {
    public ExampleModForge() {
        EventBuses.registerModEventBus(ExampleMod.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        ExampleMod.init();
        HandshakePayloadReceiver.register();
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
            HandshakePayloadReceiver.scheduleHandshakeTimeout();
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
            HandshakePayloadReceiver.cancelHandshakeTimeout();
            HandshakePayloadReceiver.clearAssembler();
            HandshakePayloadAdapter.reset();
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            HandshakePayloadReceiver.tickVoiceKey();
        }
    }
}
