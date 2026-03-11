package com.xiaofan.simpleComServer;

import com.xiaofan.SimpleComServerCore;
import com.xiaofan.config.ProxyConfig;
import com.xiaofan.config.SimpleComConfigLoader;
import com.xiaofan.proxy.PortMultiplexProxy;
import com.xiaofan.proxy.SimpleComWsServer;
import com.xiaofan.proxy.VoiceApiWsServer;
import com.xiaofan.simpleComServer.proxy.ProxyConnectionListener;
import com.xiaofan.ws.WsConnectionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.function.Consumer;

public final class SimpleComServer extends JavaPlugin {

    private PortMultiplexProxy mcProxy;
    private SimpleComWsServer wsServer;
    private VoiceApiWsServer voiceApiWsServer;
    private ProxyConfig proxyConfig;

    /**
     * 延迟执行任务：Folia/Paper 下使用 GlobalRegionScheduler（runDelayed(Plugin, Consumer, long)），否则使用 Bukkit 全局调度器。
     * Folia 的 runDelayed 第二个参数是 Consumer&lt;ScheduledTask&gt;，不是 Runnable。
     */
    private void runDelayed(long delayTicks, Runnable task) {
        try {
            Object globalScheduler = getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(getServer());
            if (globalScheduler != null) {
                // Folia/Paper: runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks)
                Consumer<?> consumer = t -> task.run();
                globalScheduler.getClass()
                        .getMethod("runDelayed", org.bukkit.plugin.Plugin.class, Consumer.class, long.class)
                        .invoke(globalScheduler, this, consumer, Long.valueOf(delayTicks));
                return;
            }
        } catch (Throwable ignored) {}
        Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
    }

    @Override
    public void onEnable() {
        new SimpleComServerCore();
        // 配置根 = 插件 JAR 所在目录（即 plugins/），配置在 plugins/SimpleComConfig/config.yml
        SimpleComConfigLoader.setConfigBaseDir(getFile().getParentFile());

        try {
            proxyConfig = SimpleComConfigLoader.load();
            getLogger().info("配置已加载 | Config loaded: " + SimpleComConfigLoader.getConfigDir());
        } catch (IOException e) {
            getLogger().severe("配置加载失败，使用默认配置 | Config load failed, using defaults: " + e.getMessage());
            proxyConfig = new ProxyConfig("0.0.0.0", 25566, 25567, "127.0.0.1", 25565, true,
                                         java.util.Arrays.asList("127.0.0.1", "::1"), true, false,
                                         false, "", 25500, "127.0.0.1", true);
        }

        mcProxy = new PortMultiplexProxy(proxyConfig);
        wsServer = new SimpleComWsServer(proxyConfig);
        voiceApiWsServer = new VoiceApiWsServer(proxyConfig);

        // 注册玩家在线检查器和重连通知器
        WsConnectionRegistry.getInstance().setPlayerOnlineChecker(
                username -> Bukkit.getPlayerExact(username) != null
        );
        WsConnectionRegistry.getInstance().setReconnectNotifier((username, newCode) -> {
            org.bukkit.entity.Player player = Bukkit.getPlayerExact(username);
            if (player == null || !player.isOnline()) return;
            player.sendMessage(org.bukkit.ChatColor.GOLD + "━━━━━━━ " + org.bukkit.ChatColor.AQUA + "SimpleCom" + org.bukkit.ChatColor.GOLD + " ━━━━━━━");
            player.sendMessage(org.bukkit.ChatColor.RED + "语音连接已断开，新验证码 | Voice disconnected, new code: " +
                    org.bukkit.ChatColor.GREEN + org.bukkit.ChatColor.BOLD.toString() + newCode);
            player.sendMessage(org.bukkit.ChatColor.GRAY + "请在语音客户端重新输入验证码连接。| Re-enter code in voice client.");
            player.sendMessage(org.bukkit.ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });

        // 延迟 2 秒启动，确保 MC 服务端 25565 已绑定，避免端口竞争
        // Delay 2s to ensure MC server has bound port 25565
        runDelayed(40L, () -> {
            try {
                mcProxy.start();
            } catch (Exception e) {
                getLogger().warning("MC 代理启动失败 | MC proxy failed to start: " + e.getMessage());
                getLogger().warning("请确认端口 " + proxyConfig.getListenPort() + " 未被占用 | Check port " + proxyConfig.getListenPort() + " is free");
            }
            try {
                wsServer.start();
            } catch (Exception e) {
                getLogger().warning("WebSocket 服务器启动失败 | WS server failed to start: " + e.getMessage());
                getLogger().warning("请确认端口 " + proxyConfig.getWsPort() + " 未被占用 | Check port " + proxyConfig.getWsPort() + " is free");
            }
            try {
                voiceApiWsServer.start();
            } catch (Exception e) {
                getLogger().warning("Voice API WS 启动失败 | Voice API WS failed to start: " + e.getMessage());
                getLogger().warning("请确认端口 " + proxyConfig.getVoiceApiPort() + " 未被占用 | Check port " + proxyConfig.getVoiceApiPort() + " is free");
            }
        });

        Bukkit.getPluginManager().registerEvents(new ProxyConnectionListener(this, proxyConfig), this);

        getLogger().info("简单的通讯器插件加载完毕 | SimpleCom plugin loaded.");

        // 服务器就绪后输出连接说明
        runDelayed(40L, this::logServerReady);
    }

    private void logServerReady() {
        int mcPort    = proxyConfig.getMcBackendPort();
        int proxyPort = proxyConfig.getListenPort();
        int wsPort    = proxyConfig.getWsPort();
        boolean proxyEnabled = proxyConfig.isProxyEnabled();
        getLogger().info("========== SimpleCom ==========");
        getLogger().info("服务器启动完毕 | Server started.");
        if (proxyEnabled) {
            getLogger().info("端口规划（对外只需开放代理端口！）| Port layout (only proxy port needs to be public!):");
            getLogger().info("  ┌ 代理（对外）  | Proxy  (external): " + proxyPort + "  ← MC 和 WS 共用此端口 | both MC & WS use this");
            getLogger().info("  ├─→ MC 服务端（内部）| MC server (internal): " + mcPort);
            getLogger().info("  └─→ WS 服务器 （内部）| WS server (internal): " + wsPort);
            getLogger().info("MC 玩家和语音客户端都连接代理端口 " + proxyPort + " | Both MC players and voice client connect to port " + proxyPort);
            getLogger().info("务必在 server.properties 设置 server-ip=127.0.0.1，使外网无法直连 " + mcPort);
            getLogger().info("Set server-ip=127.0.0.1 in server.properties so direct external access to " + mcPort + " is blocked.");
        } else {
            getLogger().info("端口规划 | Port layout:");
            getLogger().info("  ┌ 代理（未开启）| Proxy  (disabled): N/A");
            getLogger().info("  ├─→ MC 服务端（对外）| MC server (external): " + mcPort);
            getLogger().info("  └─→ WS 服务器 （对外）| WS server (external): " + wsPort);
            getLogger().info("MC 玩家连接 " + mcPort + "，语音客户端直连 WS 端口 " + wsPort + " | MC players connect to " + mcPort + ", voice client connects directly to WS port " + wsPort);
            getLogger().info("务必在 server.properties 设置 server-ip=0.0.0.0，否则玩家无法连接到 MC 服务器 " + mcPort);
            getLogger().info("Set server-ip=0.0.0.0 in server.properties so players can connect to MC server port " + mcPort + ".");
        }
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
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
        getLogger().info("简单的通讯器插件关闭完毕 | SimpleCom plugin disabled.");
    }
}
