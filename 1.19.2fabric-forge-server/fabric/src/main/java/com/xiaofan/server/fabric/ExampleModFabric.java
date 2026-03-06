package com.xiaofan.server.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import com.xiaofan.server.ExampleMod;

public final class ExampleModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ExampleMod.init();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerPayloadHandlerFabric handler = new ServerPayloadHandlerFabric(server);
            handler.register();
        });
    }
}
