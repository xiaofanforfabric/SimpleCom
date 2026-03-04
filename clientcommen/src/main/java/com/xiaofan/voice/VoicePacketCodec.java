package com.xiaofan.voice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 语音包序列化/反序列化
 * 格式：[VarInt usernameLen][UTF-8 username][VarInt channelLen][UTF-8 channel][VarInt current][VarInt total][data]
 */
public final class VoicePacketCodec {

    public static byte[] encode(VoicePacket packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        byte[] usernameBytes = packet.getUsername().getBytes(StandardCharsets.UTF_8);
        byte[] channelBytes = packet.getChannel().getBytes(StandardCharsets.UTF_8);

        writeVarInt(out, usernameBytes.length);
        out.write(usernameBytes);
        writeVarInt(out, channelBytes.length);
        out.write(channelBytes);
        writeVarInt(out, packet.getCurrentIndex());
        writeVarInt(out, packet.getTotalCount());
        out.write(packet.getAudioData());

        return baos.toByteArray();
    }

    public static VoicePacket decode(byte[] data) throws IOException {
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

        byte[] audioData = new byte[in.available()];
        in.readFully(audioData);

        return new VoicePacket(username, channel, current, total, audioData);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
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
