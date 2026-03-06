package com.xiaofan.servercommen.payload;

/**
 * SimpleCom 插件通道常量，服务端与客户端共用
 */
public final class SimpleComChannels {

    /** 握手通道：服务端发送，客户端用于判断服务端是否安装插件 */
    public static final String HANDSHAKE = "simplecom:handshake";

    /** 握手确认通道：客户端收到握手后发送，服务端收到即停止重发 */
    public static final String HANDSHAKE_ACK = "simplecom:handshake_ack";

    /** 语音/数据通道：客户端发送数据，服务端可转发给其他玩家 */
    public static final String VOICE_DATA = "simplecom:voice_data";

    /** 信道切换通道：客户端发送新信道，服务端确认后回复 */
    public static final String CHANNEL_SWITCH = "simplecom:channel_switch";

    /** 信道切换确认通道：服务端回复客户端 */
    public static final String CHANNEL_SWITCH_ACK = "simplecom:channel_switch_ack";

    /** 加密信道：创建。C2S: VarInt nameLen, UTF8 name, VarInt hashLen, UTF8 hashHex。S2C: byte success(1/0), [VarInt channelId] */
    public static final String ENCRYPTED_CREATE = "simplecom:encrypted_create";
    /** 加密信道：请求列表。C2S: 空。S2C: VarInt n, n×(VarInt len, UTF8 name) */
    public static final String ENCRYPTED_LIST = "simplecom:encrypted_list";
    /** 加密信道：加入。C2S: VarInt nameLen, UTF8 name, VarInt hashLen, UTF8 hashHex。S2C: byte success(1/0), [VarInt channelId] */
    public static final String ENCRYPTED_JOIN = "simplecom:encrypted_join";

    /** 加密信道 ID 起始值（>100） */
    public static final int ENCRYPTED_CHANNEL_ID_START = 101;

    /** Payload 协议版本 */
    public static final byte PROTOCOL_VERSION = 1;

    private SimpleComChannels() {
    }
}
