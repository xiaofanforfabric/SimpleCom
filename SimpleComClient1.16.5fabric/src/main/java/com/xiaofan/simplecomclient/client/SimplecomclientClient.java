package com.xiaofan.simplecomclient.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class SimplecomclientClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandshakePayloadReceiver.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HandshakePayloadReceiver.reset());
    }
}
