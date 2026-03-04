package com.xiaofan.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 客户端信道配置，范围 0~100，0 静音，默认 1
 */
public final class SimpleComConfig {

    private static final String CONFIG_FILE = "simplecom.properties";
    private static final String KEY_CHANNEL = "channel";

    private static volatile int channel = 1;
    private static volatile Path configDir;

    public static final int CHANNEL_MIN = 0;
    public static final int CHANNEL_MAX = 100;
    public static final int CHANNEL_DEFAULT = 1;

    private SimpleComConfig() {
    }

    /** 初始化配置目录，由 Fabric/Forge 在客户端启动时调用 */
    public static void setConfigDir(Path dir) {
        configDir = dir;
        load();
    }

    public static int getChannel() {
        return channel;
    }

    public static void setChannel(int ch) {
        channel = clampChannel(ch);
        save();
    }

    public static int clampChannel(int ch) {
        if (ch < CHANNEL_MIN) return CHANNEL_MIN;
        if (ch > CHANNEL_MAX) return CHANNEL_MAX;
        return ch;
    }

    public static void load() {
        if (configDir == null) return;
        Path file = configDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(r);
            String v = p.getProperty(KEY_CHANNEL);
            if (v != null) {
                try {
                    channel = clampChannel(Integer.parseInt(v.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        if (configDir == null) return;
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(CONFIG_FILE);
            Properties p = new Properties();
            p.setProperty(KEY_CHANNEL, String.valueOf(channel));
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, "SimpleCom config");
            }
        } catch (IOException ignored) {
        }
    }
}
