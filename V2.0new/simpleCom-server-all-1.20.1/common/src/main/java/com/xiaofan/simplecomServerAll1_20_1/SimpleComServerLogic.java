package com.xiaofan.simplecomServerAll1_20_1;

import com.xiaofan.SimpleComServerCore;
import com.xiaofan.config.ProxyConfig;
import com.xiaofan.config.SimpleComConfigLoader;
import com.xiaofan.proxy.PortMultiplexProxy;
import com.xiaofan.proxy.SimpleComWsServer;
import com.xiaofan.proxy.VoiceApiWsServer;
import com.xiaofan.ws.VerificationCodeStore;
import com.xiaofan.ws.WsConnectionRegistry;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * SimpleCom 服务端逻辑（移植自 Bukkit 插件，使用 core 模块）。
 * 由平台在服务端启动时创建并启动代理/WS，在玩家事件中调用本类方法。
 */
public final class SimpleComServerLogic {

    private final SimpleComPlatformBridge bridge;
    private volatile ProxyConfig proxyConfig;
    private volatile PortMultiplexProxy mcProxy;
    private volatile SimpleComWsServer wsServer;
    private volatile VoiceApiWsServer voiceApiWsServer;

    public SimpleComServerLogic(SimpleComPlatformBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * 加载配置并启动代理与 WebSocket 服务（建议在服务端已绑定 25565 后延迟约 2 秒调用）。
     */
    public void start() {
        new SimpleComServerCore();
        SimpleComConfigLoader.setConfigBaseDir(bridge.getConfigDir());

        try {
            proxyConfig = SimpleComConfigLoader.load();
            bridge.logInfo("配置已加载 | Config loaded: " + SimpleComConfigLoader.getConfigDir());
        } catch (IOException e) {
            bridge.logWarning("配置加载失败，使用默认配置 | Config load failed, using defaults: " + e.getMessage());
            proxyConfig = new ProxyConfig(
                    "0.0.0.0", 25566, 25567,
                    "127.0.0.1", 25565,
                    true,
                    Arrays.asList("127.0.0.1", "::1"),
                    true, false,
                    false, "", 25500, "127.0.0.1", true
            );
        }

        mcProxy = new PortMultiplexProxy(proxyConfig);
        wsServer = new SimpleComWsServer(proxyConfig);
        voiceApiWsServer = new VoiceApiWsServer(proxyConfig);

        // 注册平台玩家在线检查器和重连通知器
        WsConnectionRegistry.getInstance().setPlayerOnlineChecker(
                username -> bridge.isPlayerOnline(username)
        );
        WsConnectionRegistry.getInstance().setReconnectNotifier((username, newCode) -> {
            if (!bridge.isPlayerOnline(username)) return;
            bridge.sendMessageToPlayer(username, buildReconnectMessage(newCode));
        });

        try {
            mcProxy.start();
        } catch (Exception e) {
            bridge.logWarning("MC 代理启动失败 | MC proxy failed to start: " + e.getMessage());
            bridge.logWarning("请确认端口 " + proxyConfig.getListenPort() + " 未被占用 | Check port " + proxyConfig.getListenPort() + " is free");
        }
        try {
            wsServer.start();
        } catch (Exception e) {
            bridge.logWarning("WebSocket 服务器启动失败 | WS server failed to start: " + e.getMessage());
            bridge.logWarning("请确认端口 " + proxyConfig.getWsPort() + " 未被占用 | Check port " + proxyConfig.getWsPort() + " is free");
        }
        try {
            voiceApiWsServer.start();
        } catch (Exception e) {
            bridge.logWarning("Voice API WS 启动失败 | Voice API WS failed to start: " + e.getMessage());
            bridge.logWarning("请确认端口 " + proxyConfig.getVoiceApiPort() + " 未被占用 | Check port " + proxyConfig.getVoiceApiPort() + " is free");
        }

        bridge.logInfo("简单的通讯器模组加载完毕 | SimpleCom mod loaded.");
        logServerReady();
    }

    private void logServerReady() {
        if (proxyConfig == null) return;
        int mcPort = proxyConfig.getMcBackendPort();
        int proxyPort = proxyConfig.getListenPort();
        int wsPort = proxyConfig.getWsPort();
        bridge.logInfo("========== SimpleCom ==========");
        bridge.logInfo("服务器启动完毕 | Server started.");
        bridge.logInfo("端口规划（对外只需开放代理端口！）| Port layout (only proxy port needs to be public!):");
        bridge.logInfo("  ┌ 代理（对外）  | Proxy  (external): " + proxyPort + "  ← MC 和 WS 共用此端口 | both MC & WS use this");
        bridge.logInfo("  ├─→ MC 服务端（内部）| MC server (internal): " + mcPort);
        bridge.logInfo("  └─→ WS 服务器 （内部）| WS server (internal): " + wsPort);
        bridge.logInfo("MC 玩家和语音客户端都连接代理端口 " + proxyPort + " | Both MC players and voice client connect to port " + proxyPort);
        bridge.logInfo("务必在 server.properties 设置 server-ip=127.0.0.1，使外网无法直连 " + mcPort);
        bridge.logInfo("=================================");
    }

    /** 服务端关闭时调用 */
    public void stop() {
        if (mcProxy != null) {
            mcProxy.stop();
            mcProxy = null;
        }
        if (wsServer != null) {
            wsServer.stop();
            wsServer = null;
        }
        if (voiceApiWsServer != null) {
            voiceApiWsServer.stop();
            voiceApiWsServer = null;
        }
        bridge.logInfo("简单的通讯器模组关闭完毕 | SimpleCom mod disabled.");
    }

    /**
     * 玩家登录时检查是否允许连接（仅允许通过代理的 IP）。
     * @return 若应踢出则返回踢出原因，否则 empty
     */
    public Optional<String> onPlayerLogin(String address) {
        if (proxyConfig == null || !proxyConfig.isRequireProxyConnection()) {
            return Optional.empty();
        }
        if (address == null || address.isEmpty()) {
            return Optional.of("请通过代理连接服务器\nPlease connect through the proxy.");
        }
        String normalized = normalizeAddress(address);
        for (String a : proxyConfig.getAllowedSourceAddresses()) {
            if (normalizeAddress(a).equals(normalized)) {
                return Optional.empty();
            }
        }
        return Optional.of("请通过代理连接服务器\nPlease connect through the proxy.");
    }

    /**
     * 玩家完全进入后：生成语音验证码并通过 bridge 按名字查找玩家发送消息。
     * 注意：若平台在 JOIN 事件里能直接拿到玩家对象，建议平台自行调用
     * {@link #generateCodeForPlayer(String)} 后直接发消息，以避免按名字查找的时序问题。
     */
    public void onPlayerJoin(String playerName) {
        if (playerName == null) return;
        String code = generateCodeForPlayer(playerName);
        if (code == null) return;
        List<String> lines = buildCodeMessage(code);
        bridge.sendMessageToPlayer(playerName, lines);
        bridge.logInfo("[SimpleCom] 已为 " + playerName + " 生成语音验证码 | Voice code generated for: " + playerName);
    }

    /**
     * 仅生成验证码并返回，不发消息。供平台在能直接拿到玩家对象时使用。
     * @return 生成的验证码，失败返回 null
     */
    public String generateCodeForPlayer(String playerName) {
        if (playerName == null) return null;
        return VerificationCodeStore.getInstance().generate(playerName);
    }

    /** 构造验证码消息行列表 */
    public static List<String> buildCodeMessage(String code) {
        return Arrays.asList(
                "━━━━━━━ SimpleCom ━━━━━━━",
                "Voice code | 语音验证码: " + code,
                "Enter in voice client. Invalid once you connect or leave.",
                "在语音客户端中输入。连接语音或退出游戏后立即失效。",
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    /** 构造 WS 断线重连通知消息行列表 */
    public static List<String> buildReconnectMessage(String newCode) {
        return Arrays.asList(
                "━━━━━━━ SimpleCom ━━━━━━━",
                "§c语音连接已断开，新验证码 | Voice disconnected, new code: §a§l" + newCode,
                "§7请在语音客户端重新输入验证码连接。| Re-enter code in voice client.",
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    /** 玩家退出时：清除验证码并断开其 WebSocket */
    public void onPlayerQuit(String playerName) {
        if (playerName == null) return;
        VerificationCodeStore.getInstance().remove(playerName);
        WsConnectionRegistry.getInstance().closeForUser(playerName);
    }

    private static String normalizeAddress(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
