package com.xiaofan.simplecom.server;

import com.xiaofan.SimpleComServerCore;
import com.xiaofan.config.ProxyConfig;
import com.xiaofan.config.SimpleComConfigLoader;
import com.xiaofan.proxy.PortMultiplexProxy;
import com.xiaofan.proxy.SimpleComWsServer;
import com.xiaofan.proxy.VoiceApiWsServer;
import com.xiaofan.ws.VerificationCodeStore;
import com.xiaofan.ws.WsConnectionRegistry;
import dev.architectury.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * 简单的通讯器模组服务端 - 共用逻辑，仅用 Architectury API + 反射，不直接引用任何 MC 类以跨版本。
 */
public final class ExampleMod {

    public static final String MOD_ID = "simplecom-server-all";

    private static ProxyConfig proxyConfig;
    private static PortMultiplexProxy mcProxy;
    private static SimpleComWsServer wsServer;
    private static VoiceApiWsServer voiceApiWsServer;

    public static void init() {
        new SimpleComServerCore();

        File configBase = Platform.getGameFolder().toFile();
        SimpleComConfigLoader.setConfigBaseDir(configBase);

        try {
            proxyConfig = SimpleComConfigLoader.load();
            System.out.println("[SimpleCom] 配置已加载 | Config loaded: " + SimpleComConfigLoader.getConfigDir());
        } catch (IOException e) {
            System.err.println("[SimpleCom] 配置加载失败，使用默认配置 | Config load failed, using defaults: " + e.getMessage());
            proxyConfig = new ProxyConfig("0.0.0.0", 25566, 25567, "127.0.0.1", 25565, true, Arrays.asList("127.0.0.1", "::1"), true, false, false, "", 25500, "127.0.0.1", true);
        }

        mcProxy = new PortMultiplexProxy(proxyConfig);
        wsServer = new SimpleComWsServer(proxyConfig);
        voiceApiWsServer = new VoiceApiWsServer(proxyConfig);

        registerLifecycleEvents();
        registerPlayerEvents();

        System.out.println("[SimpleCom] 简单的通讯器模组服务端 (Architectury) 已加载 | SimpleCom mod server loaded.");
    }

    private static void registerLifecycleEvents() {
        try {
            Class<?> lifecycleEvent = Class.forName("dev.architectury.event.events.common.LifecycleEvent");
            Object started = lifecycleEvent.getField("SERVER_STARTED").get(null);
            Object stopping = lifecycleEvent.getField("SERVER_STOPPING").get(null);
            Class<?> serverStateListener = Class.forName("dev.architectury.event.events.common.LifecycleEvent$ServerState");
            Method register = started.getClass().getMethod("register", serverStateListener);

            Object startedListener = Proxy.newProxyInstance(serverStateListener.getClassLoader(), new Class<?>[]{serverStateListener}, (proxy, method, args) -> {
                if (args != null && args.length > 0) onServerStarted(args[0]);
                return null;
            });
            Object stoppingListener = Proxy.newProxyInstance(serverStateListener.getClassLoader(), new Class<?>[]{serverStateListener}, (proxy, method, args) -> {
                if (args != null && args.length > 0) onServerStopping(args[0]);
                return null;
            });
            register.invoke(started, startedListener);
            register.invoke(stopping, stoppingListener);
        } catch (Throwable t) {
            System.err.println("[SimpleCom] 注册生命周期事件失败 | Lifecycle events failed: " + t.getMessage());
        }
    }

    private static void registerPlayerEvents() {
        try {
            Class<?> playerEvent = Class.forName("dev.architectury.event.events.common.PlayerEvent");
            Object joinEvent = playerEvent.getField("PLAYER_JOIN").get(null);
            Object quitEvent = playerEvent.getField("PLAYER_QUIT").get(null);
            Class<?> joinListener = Class.forName("dev.architectury.event.events.common.PlayerEvent$PlayerJoin");
            Class<?> quitListener = Class.forName("dev.architectury.event.events.common.PlayerEvent$PlayerQuit");
            Method registerJoin = joinEvent.getClass().getMethod("register", joinListener);
            Method registerQuit = quitEvent.getClass().getMethod("register", quitListener);

            Object joinHandler = Proxy.newProxyInstance(joinListener.getClassLoader(), new Class<?>[]{joinListener}, (proxy, method, args) -> {
                if (args != null && args.length > 0) onPlayerJoin(args[0]);
                return null;
            });
            Object quitHandler = Proxy.newProxyInstance(quitListener.getClassLoader(), new Class<?>[]{quitListener}, (proxy, method, args) -> {
                if (args != null && args.length > 0) onPlayerQuit(args[0]);
                return null;
            });
            registerJoin.invoke(joinEvent, joinHandler);
            registerQuit.invoke(quitEvent, quitHandler);
        } catch (Throwable t) {
            System.err.println("[SimpleCom] 注册玩家事件失败 | Player events failed: " + t.getMessage());
        }
    }

    private static void onServerStarted(Object server) {
        Thread starter = new Thread(() -> {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                mcProxy.start();
            } catch (Exception e) {
                System.err.println("[SimpleCom] MC 代理启动失败 | MC proxy failed to start: " + e.getMessage());
            }
            try {
                wsServer.start();
            } catch (Exception e) {
                System.err.println("[SimpleCom] WebSocket 服务器启动失败 | WS server failed to start: " + e.getMessage());
            }
            try {
                voiceApiWsServer.start();
            } catch (Exception e) {
                System.err.println("[SimpleCom] Voice API WS 启动失败 | Voice API WS failed to start: " + e.getMessage());
            }
            logServerReady();
        }, "SimpleCom-ProxyStarter");
        starter.setDaemon(false);
        starter.start();
    }

    private static void onServerStopping(Object server) {
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
        System.out.println("[SimpleCom] 简单的通讯器模组已关闭 | SimpleCom mod disabled.");
    }

    private static void onPlayerJoin(Object player) {
        String name = getPlayerName(player);
        if (name == null) return;
        String code = VerificationCodeStore.getInstance().generate(name);
        if (code == null) return;

        sendMessageToPlayer(player, "§6━━━━━━━ §bSimpleCom§6 ━━━━━━━");
        sendMessageToPlayer(player, "§eVoice code | 语音验证码: §a§l" + code);
        sendMessageToPlayer(player, "§7Enter in voice client. Invalid once you connect or leave.");
        sendMessageToPlayer(player, "§7在语音客户端中输入。连接语音或退出游戏后立即失效。");
        sendMessageToPlayer(player, "§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        System.out.println("[SimpleCom] 已为 " + name + " 生成语音验证码 | Voice code generated for: " + name);
    }

    private static void onPlayerQuit(Object player) {
        String name = getPlayerName(player);
        if (name == null) return;
        VerificationCodeStore.getInstance().remove(name);
        WsConnectionRegistry.getInstance().closeForUser(name);
    }

    /**
     * 仅用反射取玩家名，不依赖方法名/类名：从 player 的 0 参方法里找返回值类型上有「无参且返回 String」的，当作名字。
     * 过滤掉 "false"/"true" 等无效结果。
     */
    private static String getPlayerName(Object player) {
        if (player == null) return null;
        try {
            for (Method m : player.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() == void.class) continue;
                Object nameObj;
                try {
                    nameObj = m.invoke(player);
                } catch (Throwable ignored) { continue; }
                if (nameObj == null) continue;
                String s = getStringFromNameLike(nameObj);
                if (isValidPlayerName(s)) return s;
            }
        } catch (Throwable t) {
            System.err.println("[SimpleCom] getPlayerName failed: " + t.getMessage());
        }
        return null;
    }

    private static String getStringFromNameLike(Object obj) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() != String.class) continue;
                Object s = m.invoke(obj);
                return s != null ? s.toString() : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isValidPlayerName(String s) {
        if (s == null || s.isEmpty()) return false;
        if ("false".equals(s) || "true".equals(s)) return false;
        return s.length() <= 16;
    }

    /**
     * 仅用反射发聊天消息，不依赖任何 MC 类名/方法名：
     * 从 player 的方法里按「第一参类型 + 可 from String 构造」推断出发消息方法，再创建消息并调用。
     */
    private static void sendMessageToPlayer(Object player, String message) {
        if (player == null || message == null) return;
        Class<?> playerClass = player.getClass();
        for (Method m : playerClass.getMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 0) continue;
            if (params.length == 2 && params[1] != boolean.class) continue;
            if (params.length > 2) continue;
            Class<?> messageType = params[0];
            Object messageObj = createMessageFromString(messageType, message);
            if (messageObj == null) continue;
            try {
                if (params.length == 2) m.invoke(player, messageObj, false);
                else m.invoke(player, messageObj);
                return;
            } catch (Throwable ignored) {}
        }
        System.err.println("[SimpleCom] sendMessage failed: could not find send method for player " + playerClass.getName());
    }

    /** 从「发消息方法的第一个参数类型」反推：用该类型的 (String) 构造或静态方法从 message 创建实例，不依赖类名。 */
    private static Object createMessageFromString(Class<?> messageType, String message) {
        try {
            for (java.lang.reflect.Constructor<?> c : messageType.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 1 && p[0] == String.class) return c.newInstance(message);
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : messageType.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) continue;
                if (!messageType.isAssignableFrom(m.getReturnType())) continue;
                return m.invoke(null, message);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void logServerReady() {
        if (proxyConfig == null) return;
        int mcPort = proxyConfig.getMcBackendPort();
        int proxyPort = proxyConfig.getListenPort();
        int wsPort = proxyConfig.getWsPort();
        System.out.println("========== SimpleCom ==========");
        System.out.println("[SimpleCom] 服务器启动完毕 | Server started.");
        System.out.println("[SimpleCom] 端口规划（对外只需开放代理端口！）| Port layout (only proxy port needs to be public!):");
        System.out.println("  ┌ 代理（对外）  | Proxy  (external): " + proxyPort + "  ← MC 和 WS 共用此端口 | both MC & WS use this");
        System.out.println("  ├─→ MC 服务端（内部）| MC server (internal): " + mcPort);
        System.out.println("  └─→ WS 服务器 （内部）| WS server (internal): " + wsPort);
        System.out.println("[SimpleCom] MC 玩家和语音客户端都连接代理端口 " + proxyPort);
        System.out.println("=================================");
    }
}
