package com.xiaofan.server.payload;

import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    /** 普通信道 0~100，加密信道 >100，最大 10000 */
    protected static final int CHANNEL_MAX = 10000;

    protected EncryptedChannelStore encryptedStore;

    /** 注入加密信道存储，由平台在 register 前调用 */
    public void setEncryptedChannelStore(EncryptedChannelStore store) {
        this.encryptedStore = store;
    }

    public void setPlayerChannel(UUID playerId, int channel) {
        if (playerId == null) return;
        Integer old = playerChannels.get(playerId);
        int ch = Math.max(0, Math.min(CHANNEL_MAX, channel));
        playerChannels.put(playerId, ch);
        if (encryptedStore != null && old != null && old >= SimpleComChannels.ENCRYPTED_CHANNEL_ID_START) {
            checkAndRemoveEmptyEncryptedChannel(old);
        }
    }

    /** 统计指定信道当前玩家数 */
    protected int countPlayersInChannel(int channelId) {
        int n = 0;
        for (Integer v : playerChannels.values()) {
            if (v != null && v == channelId) n++;
        }
        return n;
    }

    /** 若加密信道无人则销毁 */
    protected void checkAndRemoveEmptyEncryptedChannel(int channelId) {
        if (encryptedStore == null || channelId < SimpleComChannels.ENCRYPTED_CHANNEL_ID_START) return;
        if (countPlayersInChannel(channelId) > 0) return;
        String name = encryptedStore.removeByChannelId(channelId);
        if (name != null) logInfo("加密信道已无人，已销毁: " + name);
    }

    /** 玩家断开时调用，移除信道并检查加密信道是否需销毁 */
    public void onPlayerDisconnect(UUID playerId) {
        Integer ch = playerChannels.remove(playerId);
        if (encryptedStore != null && ch != null && ch >= SimpleComChannels.ENCRYPTED_CHANNEL_ID_START) {
            checkAndRemoveEmptyEncryptedChannel(ch);
        }
    }

    public int getPlayerChannel(UUID playerId) {
        if (playerId == null) return 1;
        return playerChannels.getOrDefault(playerId, 1);
    }

    /** 生成握手 payload 字节，格式与 Bukkit 一致：[discriminator][protocol][VarInt+UTF8 version,name,serverType][byte useCompressionEncoder][byte lowLatency] */
    public static byte[] buildHandshakePayload(String version, String name, String serverType, boolean useCompressionEncoder, boolean lowLatency) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeByte(0); // Forge SimpleChannel discriminator
        out.writeByte(SimpleComChannels.PROTOCOL_VERSION);
        writeVarIntString(out, version != null ? version : "");
        writeVarIntString(out, name != null ? name : "");
        writeVarIntString(out, serverType != null ? serverType : "");
        out.writeByte(useCompressionEncoder ? 1 : 0);
        out.writeByte(lowLatency ? 1 : 0);
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

    // ========== 加密信道 ==========

    /** 处理加密信道创建请求，解析后创建并回复 */
    public void onEncryptedCreate(UUID playerId, byte[] data) {
        if (encryptedStore == null || data == null) return;
        try {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
            int nameLen = VarIntUtil.readVarInt(bis);
            byte[] nameBytes = new byte[nameLen];
            readFully(bis, nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            int hashLen = VarIntUtil.readVarInt(bis);
            byte[] hashBytes = new byte[hashLen];
            readFully(bis, hashBytes);
            String passwordHash = new String(hashBytes, StandardCharsets.UTF_8);

            Integer channelId = encryptedStore.create(name, passwordHash);
            if (channelId == null) {
                sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_CREATE, buildEncryptedCreateResponsePayloadSafe(false, 0));
                logInfo(getPlayerName(playerId) + " 创建加密信道失败（名称已存在）: " + name);
                return;
            }
            setPlayerChannel(playerId, channelId);
            sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_CREATE, buildEncryptedCreateResponsePayloadSafe(true, channelId));
            logInfo(getPlayerName(playerId) + " 创建加密信道: " + name + " -> ID " + channelId);
        } catch (Exception e) {
            logWarning("解析加密信道创建请求失败: " + e.getMessage());
            sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_CREATE, buildEncryptedCreateResponsePayloadSafe(false, 0));
        }
    }

    /** 处理加密信道列表请求，无信道时返回空列表 */
    public void onEncryptedList(UUID playerId) {
        if (encryptedStore == null) return;
        try {
            byte[] payload = buildEncryptedListPayload(encryptedStore.listNames());
            sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_LIST, payload);
        } catch (IOException e) {
            logWarning("发送加密信道列表失败: " + e.getMessage());
        }
    }

    /** 处理加密信道加入请求 */
    public void onEncryptedJoin(UUID playerId, byte[] data) {
        if (encryptedStore == null || data == null) return;
        try {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
            int nameLen = VarIntUtil.readVarInt(bis);
            byte[] nameBytes = new byte[nameLen];
            readFully(bis, nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            int hashLen = VarIntUtil.readVarInt(bis);
            byte[] hashBytes = new byte[hashLen];
            readFully(bis, hashBytes);
            String passwordHash = new String(hashBytes, StandardCharsets.UTF_8);

            EncryptedChannelStore.Entry entry = encryptedStore.getByName(name);
            if (entry == null) {
                sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_JOIN, buildEncryptedJoinResponsePayloadSafe(false, 0));
                logInfo(getPlayerName(playerId) + " 加入加密信道失败（不存在）: " + name);
                return;
            }
            if (!entry.passwordHash.equals(passwordHash)) {
                sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_JOIN, buildEncryptedJoinResponsePayloadSafe(false, 0));
                logInfo(getPlayerName(playerId) + " 加入加密信道失败（密码错误）: " + name);
                return;
            }
            setPlayerChannel(playerId, entry.channelId);
            sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_JOIN, buildEncryptedJoinResponsePayloadSafe(true, entry.channelId));
            logInfo(getPlayerName(playerId) + " 加入加密信道: " + name + " -> ID " + entry.channelId);
        } catch (Exception e) {
            logWarning("解析加密信道加入请求失败: " + e.getMessage());
            sendToPlayer(playerId, SimpleComChannels.ENCRYPTED_JOIN, buildEncryptedJoinResponsePayloadSafe(false, 0));
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r <= 0) throw new IOException("EOF");
            n += r;
        }
    }

    /** 构建加密信道创建响应：[byte success][VarInt channelId?] */
    public static byte[] buildEncryptedCreateResponsePayload(boolean success, int channelId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeByte(success ? 1 : 0);
        if (success) writeVarInt(out, channelId);
        return baos.toByteArray();
    }

    private static byte[] buildEncryptedCreateResponsePayloadSafe(boolean success, int channelId) {
        try { return buildEncryptedCreateResponsePayload(success, channelId); } catch (IOException e) { return new byte[]{0}; }
    }

    private static byte[] buildEncryptedJoinResponsePayloadSafe(boolean success, int channelId) {
        try { return buildEncryptedJoinResponsePayload(success, channelId); } catch (IOException e) { return new byte[]{0}; }
    }

    /** 构建加密信道列表响应：VarInt n + n*(VarInt len + UTF8 name)，无信道时返回 VarInt 0 */
    public static byte[] buildEncryptedListPayload(List<String> names) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        writeVarInt(out, names != null ? names.size() : 0);
        if (names != null) {
            for (String name : names) {
                byte[] b = name.getBytes(StandardCharsets.UTF_8);
                writeVarInt(out, b.length);
                out.write(b);
            }
        }
        return baos.toByteArray();
    }

    /** 构建加密信道加入响应：[byte success][VarInt channelId?] */
    public static byte[] buildEncryptedJoinResponsePayload(boolean success, int channelId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeByte(success ? 1 : 0);
        if (success) writeVarInt(out, channelId);
        return baos.toByteArray();
    }
}
