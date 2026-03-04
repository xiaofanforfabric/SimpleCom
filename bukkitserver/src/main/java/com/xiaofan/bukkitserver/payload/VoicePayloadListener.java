package com.xiaofan.bukkitserver.payload;

import com.xiaofan.servercommen.payload.VoicePacketHeaderParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 接收客户端通过 payload 通道发送的语音/数据，并广播给其他玩家
 */
public final class VoicePayloadListener {

    private final PayloadChannelHandler payloadHandler;
    private final JavaPlugin plugin;

    public VoicePayloadListener(PayloadChannelHandler payloadHandler, JavaPlugin plugin) {
        this.payloadHandler = payloadHandler;
        this.plugin = plugin;
    }

    public void onVoiceData(Player player, byte[] data) {
        if (data == null || data.length == 0) return;
        String senderChannel = "1";
        int targetCount = 0;
        try {
            VoicePacketHeaderParser.Header header = VoicePacketHeaderParser.parse(data);
            if (header != null) {
                senderChannel = header.channel;
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p != player && p.isOnline() && senderChannel.equals(String.valueOf(payloadHandler.getPlayerChannel(p)))) {
                        targetCount++;
                    }
                }
                plugin.getLogger().info(String.format("收到%s的语音数据包，%d/%d,正在向%d个客户端转发",
                        header.username, header.current + 1, header.total, targetCount));
            }
        } catch (Exception ignored) {
        }
        payloadHandler.broadcastVoiceData(player, data, senderChannel);
    }
}
