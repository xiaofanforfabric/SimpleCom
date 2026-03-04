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

    public static final byte PROTOCOL_VERSION = 1;

    private SimpleComChannels() {
    }
}
