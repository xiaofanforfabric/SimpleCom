package com.xiaofan.textapi;

/**
 * TEXTAPI - 跨版本玩家事件和聊天消息抽象 API。
 * 
 * 提供：
 * - 玩家事件监听（加入/退出）
 * - 聊天消息发送
 * 
 * 版本：1.16.5
 * 支持平台：Fabric、Forge、NeoForge
 * 
 * 仅依赖 Minecraft 原生类，不依赖任何第三方库。
 */
public final class Textapi {
    public static final String MOD_ID = "textapi";
    public static final String VERSION = "1.0";

    /**
     * 初始化 TEXTAPI。
     * 需要在模组初始化时调用。
     */
    public static void init() {
        System.out.println("[TEXTAPI] 抽象 API 已初始化 (1.16.5) | Abstract API initialized (1.16.5)");
    }
}
