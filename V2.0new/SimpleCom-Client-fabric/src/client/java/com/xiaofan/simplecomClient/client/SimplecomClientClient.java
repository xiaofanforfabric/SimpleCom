package com.xiaofan.simplecomClient.client;

import net.fabricmc.api.ClientModInitializer;
import com.xiaofan.SimpleComCore;
import com.xiaofan.gui.Launcher;

public class SimplecomClientClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        new SimpleComCore();
        System.out.println("SimpleCom Client " + SimpleComCore.VERSION + " initialized!");
        System.out.println("SimpleCom Client " + SimpleComCore.VERSION + " 加载完毕!");

        // 在独立守护线程中启动 JavaFX，不阻塞 Minecraft 主线程
        // Launch JavaFX in a separate daemon thread to avoid blocking MC main thread
        Thread fxThread = new Thread(
                () -> Launcher.main(new String[0]),
                "SimpleCom-FX"
        );
        fxThread.setDaemon(true);
        fxThread.start();
    }
}
