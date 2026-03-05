package com.xiaofan.payload;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * 与 Bukkit 插件 payload 协议适配的握手解析器
 * 插件发送格式：[discriminator 0][protocol 1][VarInt+UTF8 version][VarInt+UTF8 name][VarInt+UTF8 serverType]
 */
public final class HandshakePayloadAdapter {

    public static final Identifier HANDSHAKE_CHANNEL = new Identifier("simplecom", "handshake");
    public static final Identifier HANDSHAKE_ACK_CHANNEL = new Identifier("simplecom", "handshake_ack");
    public static final Identifier VOICE_DATA_CHANNEL = new Identifier("simplecom", "voice_data");
    public static final Identifier CHANNEL_SWITCH_CHANNEL = new Identifier("simplecom", "channel_switch");
    public static final Identifier CHANNEL_SWITCH_ACK_CHANNEL = new Identifier("simplecom", "channel_switch_ack");

    private static volatile boolean serverHasPlugin = false;
    private static volatile String serverPluginVersion = "";
    private static volatile String serverPluginName = "";
    private static volatile String serverType = "";

    /**
     * 从 PacketByteBuf 解析握手 payload（Fabric 用，buffer 含完整 payload）
     * 格式：[discriminator 0][protocol 1][VarInt+UTF8 version][VarInt+UTF8 name][VarInt+UTF8 serverType]
     */
    public static HandshakeData parse(PacketByteBuf buf) {
        buf.readByte(); // discriminator
        byte protocolVersion = buf.readByte();
        String version = readVarIntString(buf);
        String name = readVarIntString(buf);
        String serverType = buf.readableBytes() > 0 ? readVarIntString(buf) : "";
        return new HandshakeData(protocolVersion, version, name, serverType);
    }

    /**
     * 解析握手 payload（Forge 用，Forge 已消费 discriminator，buffer 从 protocol 开始）
     */
    public static HandshakeData parseFromProtocol(PacketByteBuf buf) {
        byte protocolVersion = buf.readByte();
        String version = readVarIntString(buf);
        String name = readVarIntString(buf);
        String serverType = buf.readableBytes() > 0 ? readVarIntString(buf) : "";
        return new HandshakeData(protocolVersion, version, name, serverType);
    }

    /** 按 Minecraft PacketByteBuf 格式读取：VarInt 长度 + UTF-8 字节 */
    private static String readVarIntString(PacketByteBuf buf) {
        int len = readVarInt(buf);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readVarInt(PacketByteBuf buf) {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = buf.readByte() & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public static void onHandshakeReceived(HandshakeData data) {
        serverHasPlugin = true;
        serverPluginVersion = data.version;
        serverPluginName = data.name;
        serverType = data.serverType != null ? data.serverType : "";
    }

    public static void reset() {
        serverHasPlugin = false;
        serverPluginVersion = "";
        serverPluginName = "";
        serverType = "";
    }

    public static boolean hasServerPlugin() {
        return serverHasPlugin;
    }

    public static String getServerPluginVersion() {
        return serverPluginVersion;
    }

    public static String getServerPluginName() {
        return serverPluginName;
    }

    public static String getServerType() {
        return serverType;
    }

    public static final class HandshakeData {
        public final byte protocolVersion;
        public final String version;
        public final String name;
        public final String serverType;

        public HandshakeData(byte protocolVersion, String version, String name, String serverType) {
            this.protocolVersion = protocolVersion;
            this.version = version;
            this.name = name != null ? name : "";
            this.serverType = serverType != null ? serverType : "";
        }
    }
}
