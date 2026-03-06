package com.xiaofan.bukkitserver.payload;

import com.xiaofan.servercommen.payload.SimpleComChannels;
import com.xiaofan.servercommen.payload.VarIntUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 payload 通道收发数据：握手、语音/数据、信道切换
 * 使用 Minecraft PacketByteBuf 兼容格式：VarInt 长度前缀 + UTF-8
 */
public final class PayloadChannelHandler {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> playerChannels = new ConcurrentHashMap<>();

    public PayloadChannelHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setPlayerChannel(Player player, int channel) {
        if (player != null) {
            playerChannels.put(player.getUniqueId(), Math.max(0, Math.min(100, channel)));
        }
    }

    public int getPlayerChannel(Player player) {
        if (player == null) return 1;
        return playerChannels.getOrDefault(player.getUniqueId(), 1);
    }

    public void onChannelSwitch(Player player, byte[] data) {
        if (player == null || data == null) return;
        try {
            int ch = VarIntUtil.readVarInt(data);
            ch = Math.max(0, Math.min(100, ch));
            setPlayerChannel(player, ch);
            plugin.getLogger().info(player.getName() + " 已切换到" + ch + "信道");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeVarInt(out, ch);
            byte[] ack = baos.toByteArray();
            player.sendPluginMessage(plugin, SimpleComChannels.CHANNEL_SWITCH_ACK, ack);
        } catch (Exception e) {
            plugin.getLogger().warning("解析信道切换失败: " + e.getMessage());
        }
    }

    /**
     * 向玩家发送握手 payload，客户端收到后可确认服务端已安装插件
     * 格式：[discriminator][protocol][VarInt+UTF8 version][VarInt+UTF8 name][VarInt+UTF8 serverType][byte useCompressionEncoder 0/1][byte lowLatency 0/1]
     */
    public void sendHandshake(Player player) {
        try {
            boolean useCompression = plugin.getConfig().getBoolean("use_compression_encoder", false);
            boolean lowLatency = plugin.getConfig().getBoolean("low_latency", false);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            out.writeByte(0); // Forge SimpleChannel discriminator
            out.writeByte(SimpleComChannels.PROTOCOL_VERSION);
            writeVarIntString(out, plugin.getDescription().getVersion());
            writeVarIntString(out, plugin.getDescription().getName());
            writeVarIntString(out, "spigot");
            out.writeByte(useCompression ? 1 : 0); // Use a compression encoder: false=0, true=1
            out.writeByte(lowLatency ? 1 : 0);     // Low latency: false=0, true=1

            byte[] data = baos.toByteArray();
            player.sendPluginMessage(plugin, SimpleComChannels.HANDSHAKE, data);
        } catch (IOException e) {
            plugin.getLogger().warning("发送握手 payload 失败: " + e.getMessage());
        }
    }

    /**
     * 向指定玩家发送语音/数据 payload
     */
    public void sendVoiceData(Player target, byte[] data) {
        if (data != null && data.length > 0) {
            target.sendPluginMessage(plugin, SimpleComChannels.VOICE_DATA, data);
        }
    }

    /**
     * 向除发送者外、同信道的在线玩家广播语音/数据
     */
    public void broadcastVoiceData(Player sender, byte[] data, String senderChannel) {
        if (data == null || data.length == 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != sender && p.isOnline()) {
                int targetChannel = getPlayerChannel(p);
                if (senderChannel != null && senderChannel.equals(String.valueOf(targetChannel))) {
                    p.sendPluginMessage(plugin, SimpleComChannels.VOICE_DATA, data);
                }
            }
        }
    }

    /** 按 Minecraft PacketByteBuf 格式写入字符串：VarInt 长度 + UTF-8 字节 */
    private static void writeVarIntString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
