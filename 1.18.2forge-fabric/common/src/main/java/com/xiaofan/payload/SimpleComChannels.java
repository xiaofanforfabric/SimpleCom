package com.xiaofan.payload;

/**
 * SimpleCom 插件通道常量，与 Bukkit 服务端 SimpleComChannels 一致
 */
public final class SimpleComChannels {

    /** 握手通道：客户端用于判断服务端是否安装插件 */
    public static final String HANDSHAKE = "simplecom:handshake";

    /** Payload 协议版本 */
    public static final byte PROTOCOL_VERSION = 1;

    private SimpleComChannels() {
    }
}
