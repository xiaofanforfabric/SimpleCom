package com.xiaofan.bukkitserver.payload;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家加入时发送握手 payload
 * 每 3 秒发送一次，最多 10 次；收到客户端握手确认即停止
 */
public class PlayerJoinListener implements Listener {

    private static final int HANDSHAKE_COUNT = 10;
    private static final int INTERVAL_TICKS = 60; // 3 秒 = 60 tick

    private final PayloadChannelHandler payloadHandler;
    private final JavaPlugin plugin;
    private final Map<UUID, BukkitRunnable> pendingHandshakes = new ConcurrentHashMap<>();

    public PlayerJoinListener(PayloadChannelHandler payloadHandler, JavaPlugin plugin) {
        this.payloadHandler = payloadHandler;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        BukkitRunnable runnable = new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= HANDSHAKE_COUNT || !player.isOnline()) {
                    pendingHandshakes.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                payloadHandler.sendHandshake(player);
                plugin.getLogger().info("[SimpleCom] 发送握手 #" + (count + 1) + " 给 " + player.getName());
                count++;
            }
        };
        pendingHandshakes.put(player.getUniqueId(), runnable);
        runnable.runTaskTimer(plugin, 0, INTERVAL_TICKS);
    }

    /** 收到客户端握手确认时调用，停止对该玩家的重发 */
    public void onHandshakeAck(Player player, int channel) {
        payloadHandler.setPlayerChannel(player, channel);
        BukkitRunnable runnable = pendingHandshakes.remove(player.getUniqueId());
        if (runnable != null) {
            runnable.cancel();
            plugin.getLogger().info("[SimpleCom] " + player.getName() + " 已确认握手，停止重发，信道：" + channel);
        }
    }
}
