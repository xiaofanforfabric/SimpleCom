package com.xiaofan.bukkitserver.payload;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家下线时从信道表移除，若原在加密信道且该信道无人则销毁
 */
public final class PlayerQuitListener implements Listener {

    private final PayloadChannelHandler payloadHandler;

    public PlayerQuitListener(PayloadChannelHandler payloadHandler) {
        this.payloadHandler = payloadHandler;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            payloadHandler.onPlayerQuit(player);
        }
    }
}
