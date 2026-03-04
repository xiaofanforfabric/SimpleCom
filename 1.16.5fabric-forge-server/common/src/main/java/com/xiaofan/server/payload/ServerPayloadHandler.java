package com.xiaofan.server.payload;

import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 payload 处理逻辑（平台无关）
 * 平台层负责：注册通道、收发字节、获取在线玩家
 */
public abstract class ServerPayloadHandler {

    protected final Map<UUID, Integer> playerChannels = new ConcurrentHashMap<>();
    protected static final int HANDSHAKE_COUNT = 10;
    protected static final int HANDSHAKE_INTERVAL_TICKS = 60; // 3 秒

    public void setPlayerChannel(UUID playerId, int channel) {
        if (playerId != null) {
            playerChannels.put(playerId, Math.max(0, Math.min(100, channel)));
        }
    }

    public int getPlayerChannel(UUID playerId) {
        if (playerId == null) return 1;
        return playerChannels.getOrDefault(playerId, 1);
    }

    /** 生成握手 payload 字节，格式与 Bukkit 一致 */
    public static byte[] buildHandshakePayload(String version, String name, String serverType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeByte(0); // Forge SimpleChannel discriminator
        out.writeByte(SimpleComChannels.PROTOCOL_VERSION);
        writeVarIntString(out, version != null ? version : "");
        writeVarIntString(out, name != null ? name : "");
        writeVarIntString(out, serverType != null ? serverType : "");
        return baos.toByteArray();
    }

    /** 生成信道切换确认 payload */
    public static byte[] buildChannelSwitchAckPayload(int channel) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        writeVarInt(out, channel);
        return baos.toByteArray();
    }

    protected static void writeVarIntString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    protected static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    /** 平台实现：发送 payload 给指定玩家 */
    public abstract void sendToPlayer(UUID playerId, Identifier channel, byte[] data);

    /** 平台实现：获取除 sender 外、指定信道的所有在线玩家 ID */
    public abstract Iterable<UUID> getOnlinePlayerIdsInChannel(UUID excludeSender, String channel);

    /** 平台实现：获取玩家名（用于日志） */
    public abstract String getPlayerName(UUID playerId);

    /** 平台实现：日志 */
    public abstract void logInfo(String msg);

    /** 平台实现：日志警告 */
    public abstract void logWarning(String msg);
}
