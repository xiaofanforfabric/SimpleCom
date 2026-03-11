package com.xiaofan.gui;

import javafx.application.Application;

/**
 * 应用程序入口（不直接继承 Application，解决 Java 11+ 模块检测问题）。
 * Application entry point. Does NOT extend Application to avoid Java 11+ module detection issues with fat JARs.
 */
public final class Launcher {

    public static void main(String[] args) {
        Application.launch(SimpleComApp.class, args);
    }
}
