package com.xiaofan.servercommen.payload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 解析语音包头部，获取 username、current、total
 * 格式与 VoicePacketCodec 一致：[VarInt usernameLen][UTF-8 username][VarInt channelLen][UTF-8 channel][VarInt current][VarInt total][...]
 */
public final class VoicePacketHeaderParser {

    public static class Header {
        public final String username;
        public final String channel;
        public final int current;
        public final int total;

        public Header(String username, String channel, int current, int total) {
            this.username = username;
            this.channel = channel != null ? channel : "1";
            this.current = current;
            this.total = total;
        }
    }

    public static Header parse(byte[] data) throws IOException {
        if (data == null || data.length == 0) return null;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

        int usernameLen = readVarInt(in);
        byte[] usernameBytes = new byte[usernameLen];
        in.readFully(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);

        int channelLen = readVarInt(in);
        byte[] channelBytes = new byte[channelLen];
        in.readFully(channelBytes);
        String channel = new String(channelBytes, StandardCharsets.UTF_8);

        int current = readVarInt(in);
        int total = readVarInt(in);

        return new Header(username, channel, current, total);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = in.readByte() & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }
}
