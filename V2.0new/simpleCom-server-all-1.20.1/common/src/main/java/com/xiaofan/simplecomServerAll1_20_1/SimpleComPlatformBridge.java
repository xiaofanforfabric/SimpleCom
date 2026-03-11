package com.xiaofan.simplecomServerAll1_20_1;

import java.io.File;
import java.util.List;

/**
 * 平台桥接：由 Fabric/Forge 实现，供 Common 获取配置目录、发消息、注册玩家事件。
 * 仅在服务端使用。
 */
public interface SimpleComPlatformBridge {

    /** 配置根目录（与 Bukkit 的 plugins 目录等价，配置在 SimpleComConfig/config.yml 下） */
    File getConfigDir();

    /** 向指定玩家发送多行聊天消息 */
    void sendMessageToPlayer(String playerName, List<String> lines);

    /** 注册玩家登录/进入/退出监听，内部会调用 logic 的 onPlayerLogin/onPlayerJoin/onPlayerQuit */
    void registerListeners(SimpleComServerLogic logic);

    /**
     * 查询玩家是否当前在 MC 服务器上在线（大小写不敏感）。
     * Check if a player is currently online in the MC server (case-insensitive).
     */
    boolean isPlayerOnline(String playerName);

    void logInfo(String message);

    void logWarning(String message);
}
