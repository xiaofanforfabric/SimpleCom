package com.xiaofan.server.payload;

import net.minecraft.util.Identifier;

/**
 * SimpleCom payload 通道常量，与客户端、Bukkit 服务端一致
 */
public final class SimpleComChannels {

    public static final Identifier HANDSHAKE = new Identifier("simplecom", "handshake");
    public static final Identifier HANDSHAKE_ACK = new Identifier("simplecom", "handshake_ack");
    public static final Identifier VOICE_DATA = new Identifier("simplecom", "voice_data");
    public static final Identifier CHANNEL_SWITCH = new Identifier("simplecom", "channel_switch");
    public static final Identifier CHANNEL_SWITCH_ACK = new Identifier("simplecom", "channel_switch_ack");
    public static final Identifier ENCRYPTED_CREATE = new Identifier("simplecom", "encrypted_create");
    public static final Identifier ENCRYPTED_LIST = new Identifier("simplecom", "encrypted_list");
    public static final Identifier ENCRYPTED_JOIN = new Identifier("simplecom", "encrypted_join");

    public static final byte PROTOCOL_VERSION = 1;
    /** 加密信道 ID 起始值，>100 为加密信道 */
    public static final int ENCRYPTED_CHANNEL_ID_START = 101;

    private SimpleComChannels() {
    }
}
