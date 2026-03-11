package com.xiaofan.simplecomServerFabric;

import com.xiaofan.SimpleComServerCore;
import com.xiaofan.config.ProxyConfig;
import com.xiaofan.config.SimpleComConfigLoader;
import com.xiaofan.proxy.PortMultiplexProxy;
import com.xiaofan.proxy.SimpleComWsServer;
import com.xiaofan.proxy.VoiceApiWsServer;
import com.xiaofan.simplecomServerFabric.api.PlayerJoinLeaveHandler;
import com.xiaofan.ws.WsConnectionRegistry;
import dev.neuralnexus.taterlib.event.api.ServerEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

public class SimplecomServerFabric implements ModInitializer {

    private static ProxyConfig proxyConfig;
    private static PortMultiplexProxy mcProxy;
    private static SimpleComWsServer wsServer;
    private static VoiceApiWsServer voiceApiWsServer;

    @Override
    public void onInitialize() {
        new SimpleComServerCore();
        // 判断本模组 JAR 的绝对路径，取其所在目录（如 E:/server/mods 或 /server/mods）传给 core，在 mods/SimpleComConfig/config.yml 创建配置
        File modsDir = getModJarParentDir();
        SimpleComConfigLoader.setConfigBaseDir(modsDir);
        PlayerJoinLeaveHandler.register();

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

        // 注册玩家在线检查器（TaterLib 无法直接查询，通过 server 引用查询）
        // PlayerJoinLeaveHandler 在 STARTED 后才有 server 实例，此处先注册占位，
        // 实际检查器在 ServerEvents.STARTED 里覆盖注册
        ServerEvents.STARTED.register(startedEvent -> {
            // 通过 TaterLib 的 Server 抽象获取在线玩家列表
            WsConnectionRegistry.getInstance().setPlayerOnlineChecker(username -> {
                try {
                    dev.neuralnexus.taterlib.api.TaterAPIProvider.api()
                            .ifPresent(api -> {}); // 触发懒加载
                    // TaterLib 1.x: Server.getInstance().onlinePlayers()
                    for (dev.neuralnexus.taterlib.player.SimplePlayer p :
                            dev.neuralnexus.taterlib.api.TaterAPIProvider.api()
                                    .map(api -> api.server().onlinePlayers())
                                    .orElse(java.util.Collections.emptyList())) {
                        if (p.name().equalsIgnoreCase(username)) return true;
                    }
                } catch (Exception ignored) {}
                return false;
            });
            WsConnectionRegistry.getInstance().setReconnectNotifier((username, newCode) -> {
                try {
                    for (dev.neuralnexus.taterlib.player.SimplePlayer p :
                            dev.neuralnexus.taterlib.api.TaterAPIProvider.api()
                                    .map(api -> api.server().onlinePlayers())
                                    .orElse(java.util.Collections.emptyList())) {
                        if (p.name().equalsIgnoreCase(username)) {
                            p.sendMessage("§6━━━━━━━ §bSimpleCom§6 ━━━━━━━");
                            p.sendMessage("§c语音连接已断开，新验证码 | Voice disconnected, new code: §a§l" + newCode);
                            p.sendMessage("§7请在语音客户端重新输入验证码连接。| Re-enter code in voice client.");
                            p.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            });
        });

        ServerEvents.STARTED.register(event -> {
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
                    System.err.println("[SimpleCom] 请确认端口 " + proxyConfig.getListenPort() + " 未被占用 | Check port " + proxyConfig.getListenPort() + " is free");
                }
                try {
                    wsServer.start();
                } catch (Exception e) {
                    System.err.println("[SimpleCom] WebSocket 服务器启动失败 | WS server failed to start: " + e.getMessage());
                    System.err.println("[SimpleCom] 请确认端口 " + proxyConfig.getWsPort() + " 未被占用 | Check port " + proxyConfig.getWsPort() + " is free");
                }
                try {
                    voiceApiWsServer.start();
                } catch (Exception e) {
                    System.err.println("[SimpleCom] Voice API WS 启动失败 | Voice API WS failed to start: " + e.getMessage());
                    System.err.println("[SimpleCom] 请确认端口 " + proxyConfig.getVoiceApiPort() + " 未被占用 | Check port " + proxyConfig.getVoiceApiPort() + " is free");
                }
                logServerReady();
            }, "SimpleCom-ProxyStarter");
            starter.setDaemon(false);
            starter.start();
        });

        ServerEvents.STOPPING.register(event -> {
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
        });

        System.out.println("SimpleCom Server (Fabric) " + SimpleComServerCore.VERSION + " 加载完毕!");
    }

    /**
     * 获取本模组 JAR 所在目录的绝对路径（即 mods 文件夹，如 E:/server/mods 或 /server/mods）。
     * 通过当前类所在 CodeSource 定位 JAR 文件，取其父目录；解析失败时回退到游戏目录。
     */
    private static File getModJarParentDir() {
        try {
            java.net.URL location = SimplecomServerFabric.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return FabricLoader.getInstance().getGameDir().toFile();
            String path = location.getPath();
            // 支持 jar:file:/E:/server/mods/xxx.jar!/ 或 file:/path/to/jar.jar
            if (path != null && path.contains("!")) {
                path = path.substring(0, path.indexOf('!'));
            }
            if (path != null && path.startsWith("file:")) {
                path = path.substring(5);
            }
            // Windows: getPath() 可能返回 /E:/server/mods/xxx.jar，去掉首斜杠
            if (path != null && path.length() > 2 && path.startsWith("/") && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            if (path != null && !path.isEmpty()) {
                // 解码 URL 编码（如 %20 -> 空格）
                path = java.net.URLDecoder.decode(path, "UTF-8");
                File jarOrDir = new File(path);
                File parent = jarOrDir.isFile() ? jarOrDir.getParentFile() : jarOrDir.getParentFile();
                if (parent != null) return parent.getAbsoluteFile();
            }
            File fromUri = new File(location.toURI());
            File parent = fromUri.isFile() ? fromUri.getParentFile() : fromUri.getParentFile();
            if (parent != null) return parent.getAbsoluteFile();
        } catch (URISyntaxException | IllegalArgumentException | java.io.UnsupportedEncodingException ignored) {
        }
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    private static void logServerReady() {
        if (proxyConfig == null) return;
        int mcPort = proxyConfig.getMcBackendPort();
        int proxyPort = proxyConfig.getListenPort();
        int wsPort = proxyConfig.getWsPort();
        boolean proxyEnabled = proxyConfig.isProxyEnabled();
        System.out.println("========== SimpleCom ==========");
        System.out.println("[SimpleCom] 服务器启动完毕 | Server started.");
        if (proxyEnabled) {
            System.out.println("[SimpleCom] 端口规划（对外只需开放代理端口！）| Port layout (only proxy port needs to be public!):");
            System.out.println("  ┌ 代理（对外）  | Proxy  (external): " + proxyPort + "  ← MC 和 WS 共用此端口 | both MC & WS use this");
            System.out.println("  ├─→ MC 服务端（内部）| MC server (internal): " + mcPort);
            System.out.println("  └─→ WS 服务器 （内部）| WS server (internal): " + wsPort);
            System.out.println("[SimpleCom] MC 玩家和语音客户端都连接代理端口 " + proxyPort + " | Both MC players and voice client connect to port " + proxyPort);
            System.out.println("[SimpleCom] 务必在 server.properties 设置 server-ip=127.0.0.1，使外网无法直连 " + mcPort);
            System.out.println("[SimpleCom] Set server-ip=127.0.0.1 in server.properties so direct external access to " + mcPort + " is blocked.");
        } else {
            System.out.println("[SimpleCom] 端口规划 | Port layout:");
            System.out.println("  ┌ 代理（未开启）| Proxy  (disabled): N/A");
            System.out.println("  ├─→ MC 服务端（对外）| MC server (external): " + mcPort);
            System.out.println("  └─→ WS 服务器 （对外）| WS server (external): " + wsPort);
            System.out.println("[SimpleCom] MC 玩家连接 " + mcPort + "，语音客户端直连 WS 端口 " + wsPort + " | MC players connect to " + mcPort + ", voice client connects directly to WS port " + wsPort);
            System.out.println("[SimpleCom] 务必在 server.properties 设置 server-ip=0.0.0.0，否则玩家无法连接到 MC 服务器 " + mcPort);
            System.out.println("[SimpleCom] Set server-ip=0.0.0.0 in server.properties so players can connect to MC server port " + mcPort + ".");
        }
        System.out.println("=================================");
    }
}
