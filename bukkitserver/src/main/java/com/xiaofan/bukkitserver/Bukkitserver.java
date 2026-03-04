package com.xiaofan.bukkitserver;

import com.xiaofan.bukkitserver.payload.PayloadChannelHandler;
import com.xiaofan.bukkitserver.payload.PlayerJoinListener;
import com.xiaofan.bukkitserver.payload.VoicePayloadListener;
import com.xiaofan.servercommen.payload.SimpleComChannels;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Bukkitserver extends JavaPlugin {

    private PayloadChannelHandler payloadChannelHandler;
    private PlayerJoinListener playerJoinListener;
    private VoicePayloadListener voicePayloadListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        payloadChannelHandler = new PayloadChannelHandler(this);
        playerJoinListener = new PlayerJoinListener(payloadChannelHandler, this);
        voicePayloadListener = new VoicePayloadListener(payloadChannelHandler, this);

        // 握手通道
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, SimpleComChannels.HANDSHAKE);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, SimpleComChannels.HANDSHAKE_ACK, (channel, player, message) -> {
            int ch = 1;
            try {
                byte[] data = message;
                if (data != null && data.length > 1) {
                    data = java.util.Arrays.copyOfRange(data, 1, data.length);
                }
                ch = com.xiaofan.servercommen.payload.VarIntUtil.readVarInt(data != null ? data : new byte[0]);
                ch = Math.max(0, Math.min(100, ch));
            } catch (Exception ignored) {
            }
            playerJoinListener.onHandshakeAck(player, ch);
        });

        // 语音/数据通道
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, SimpleComChannels.VOICE_DATA);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, SimpleComChannels.VOICE_DATA, (channel, player, message) -> voicePayloadListener.onVoiceData(player, message));

        // 信道切换通道
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, SimpleComChannels.CHANNEL_SWITCH_ACK);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, SimpleComChannels.CHANNEL_SWITCH, (ch, player, message) -> payloadChannelHandler.onChannelSwitch(player, message));

        Bukkit.getPluginManager().registerEvents(playerJoinListener, this);
        getLogger().info("[SimpleCom] 已启用，使用 payload 通道传输数据");
    }
}
