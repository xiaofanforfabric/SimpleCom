package com.xiaofan.simplecomServerAll1_20_1.fabric;

import com.xiaofan.simplecomServerAll1_20_1.SimpleComServerLogic;
import com.xiaofan.simplecomServerAll1_20_1.SimplecomServerAll1_20_1;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class SimplecomServerAll1_20_1Fabric implements ModInitializer {

    private static SimpleComServerLogic serverLogic;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SimpleComFabricBridge bridge = new SimpleComFabricBridge(server);
            serverLogic = SimplecomServerAll1_20_1.initServer(bridge);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (serverLogic != null) {
                serverLogic.stop();
                serverLogic = null;
            }
        });
    }
}
