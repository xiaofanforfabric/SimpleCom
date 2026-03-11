package com.xiaofan.simplecom_client_forge;

import com.xiaofan.SimpleComCore;
import net.minecraftforge.fml.common.Mod;

@Mod(Simplecom_client_forge.MODID)
public class Simplecom_client_forge {

    public static final String MODID = "simplecom_client_forge";

    public Simplecom_client_forge() {
        new SimpleComCore();
        System.out.println("SimpleCom Client " + SimpleComCore.VERSION + " initialized!");
        System.out.println("SimpleCom Client " + SimpleComCore.VERSION + " 加载完毕!");

        // 在独立线程中启动 SimpleCom JavaFX GUI
        Thread fxThread = new Thread(
                () -> com.xiaofan.gui.Launcher.main(new String[0]),
                "SimpleCom-FX"
        );
        fxThread.setDaemon(true);
        fxThread.start();
    }
}
