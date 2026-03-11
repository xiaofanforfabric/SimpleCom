package com.xiaofan.simpleComServer.proxy;

import com.xiaofan.config.ProxyConfig;
import com.xiaofan.ws.VerificationCodeStore;
import com.xiaofan.ws.WsConnectionRegistry;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 仅允许通过代理连接的玩家进入，直接连 MC 端口的踢出并提示。
 * 玩家进入后生成语音验证码并发送到玩家聊天框。
 * 玩家退出时清除验证码和连接记录，断开对应的 WebSocket 连接。
 */
public final class ProxyConnectionListener implements Listener {

    private static final String KICK_MESSAGE = "请通过代理连接服务器\nPlease connect through the proxy.";

    private final JavaPlugin plugin;
    private final ProxyConfig config;

    public ProxyConnectionListener(JavaPlugin plugin, ProxyConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!config.isRequireProxyConnection()) {
            return;
        }
        if (event.getAddress() == null) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(KICK_MESSAGE);
            return;
        }
        String address = getNormalizedAddress(event.getAddress().getHostAddress());
        List<String> allowed = config.getAllowedSourceAddresses();
        boolean fromProxy = false;
        for (String a : allowed) {
            if (getNormalizedAddress(a).equals(address)) {
                fromProxy = true;
                break;
            }
        }
        if (!fromProxy) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(KICK_MESSAGE);
        }
    }

    /**
     * 玩家完全进入服务器后：生成6位语音验证码并发送到聊天。
     * After player fully joins: generate a 6-digit voice code and send it in chat.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer() == null) return;
        String name = event.getPlayer().getName();
        String code = VerificationCodeStore.getInstance().generate(name);
        if (code == null) return;

        event.getPlayer().sendMessage(
                ChatColor.GOLD + "━━━━━━━ " + ChatColor.AQUA + "SimpleCom" + ChatColor.GOLD + " ━━━━━━━");
        event.getPlayer().sendMessage(
                ChatColor.YELLOW + "Voice code | 语音验证码: " +
                ChatColor.GREEN + ChatColor.BOLD.toString() + code);
        event.getPlayer().sendMessage(
                ChatColor.GRAY + "Enter in voice client. Invalid once you connect or leave.");
        event.getPlayer().sendMessage(
                ChatColor.GRAY + "在语音客户端中输入。连接语音或退出游戏后立即失效。");
        event.getPlayer().sendMessage(
                ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        plugin.getLogger().info("[SimpleCom] 已为 " + name + " 生成语音验证码 | Voice code generated for: " + name);
    }

    /** 玩家退出时：清除验证码、连接记录，主动断开其 WebSocket 连接。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) return;
        String name = event.getPlayer().getName();
        VerificationCodeStore.getInstance().remove(name);
        WsConnectionRegistry.getInstance().closeForUser(name);
    }

    private static String getNormalizedAddress(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }
}
