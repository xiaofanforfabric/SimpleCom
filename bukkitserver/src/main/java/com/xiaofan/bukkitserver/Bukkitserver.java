package com.xiaofan.bukkitserver;

import com.xiaofan.bukkitserver.payload.EncryptedChannelHandler;
import com.xiaofan.bukkitserver.payload.EncryptedChannelStore;
import com.xiaofan.bukkitserver.payload.PayloadChannelHandler;
import com.xiaofan.bukkitserver.payload.PlayerJoinListener;
import com.xiaofan.bukkitserver.payload.PlayerQuitListener;
import com.xiaofan.bukkitserver.payload.VoicePayloadListener;
import com.xiaofan.servercommen.payload.SimpleComChannels;
import com.xiaofan.servercommen.payload.VarIntUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Bukkitserver extends JavaPlugin {

    private PayloadChannelHandler payloadChannelHandler;
    private EncryptedChannelStore encryptedChannelStore;
    private EncryptedChannelHandler encryptedChannelHandler;
    private PlayerJoinListener playerJoinListener;
    private PlayerQuitListener playerQuitListener;
    private VoicePayloadListener voicePayloadListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        payloadChannelHandler = new PayloadChannelHandler(this);
        encryptedChannelStore = new EncryptedChannelStore(this);
        payloadChannelHandler.setEncryptedChannelStore(encryptedChannelStore);
        encryptedChannelHandler = new EncryptedChannelHandler(encryptedChannelStore, payloadChannelHandler, this);
        playerJoinListener = new PlayerJoinListener(payloadChannelHandler, this);
        playerQuitListener = new PlayerQuitListener(payloadChannelHandler);
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
                ch = VarIntUtil.readVarInt(data != null ? data : new byte[0]);
                ch = Math.max(0, Math.min(PayloadChannelHandler.CHANNEL_MAX, ch));
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

        // 加密信道：创建、列表、加入（双向通道，服务端也发回同一 channel 名）
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, SimpleComChannels.ENCRYPTED_CREATE);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, SimpleComChannels.ENCRYPTED_CREATE, (ch, player, message) -> encryptedChannelHandler.onEncryptedCreate(player, message));
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, SimpleComChannels.ENCRYPTED_LIST);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, SimpleComChannels.ENCRYPTED_LIST, (ch, player, message) -> encryptedChannelHandler.onEncryptedList(player));
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, SimpleComChannels.ENCRYPTED_JOIN);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, SimpleComChannels.ENCRYPTED_JOIN, (ch, player, message) -> encryptedChannelHandler.onEncryptedJoin(player, message));

        Bukkit.getPluginManager().registerEvents(playerJoinListener, this);
        Bukkit.getPluginManager().registerEvents(playerQuitListener, this);
        boolean compression = getConfig().getBoolean("use_compression_encoder", false);
        boolean lowLatency = getConfig().getBoolean("low_latency", false);
        getLogger().info("[SimpleCom] 已启用，使用 payload 通道传输数据，压缩编码器：" + (compression ? "启用" : "关闭") + "，低延迟：" + (lowLatency ? "开启" : "关闭"));
    }
}
