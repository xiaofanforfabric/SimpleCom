package com.xiaofan.voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 录音完成后的编码与分片
 * 格式：MC用户名 + 信道(1) + 序号(如3/8) + 30KB 数据
 */
public final class VoiceSession {

    private final String username;
    private final String channel;
    private final OpusCodec codec = new OpusCodec();

    public VoiceSession(String username) {
        this(username, VoicePacket.CHANNEL_PLACEHOLDER);
    }

    public VoiceSession(String username, String channel) {
        this.username = username != null ? username : "";
        this.channel = channel != null ? channel : VoicePacket.CHANNEL_PLACEHOLDER;
    }

    /**
     * 将 PCM 编码并分片为多个 VoicePacket
     */
    public List<byte[]> encodeAndChunk(short[] pcm) throws IOException {
        if (pcm == null || pcm.length == 0) return new ArrayList<>();

        byte[] encoded = codec.encode(pcm);
        int chunkSize = VoicePacket.CHUNK_SIZE;
        int total = (encoded.length + chunkSize - 1) / chunkSize;
        List<byte[]> result = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            int from = i * chunkSize;
            int to = Math.min(from + chunkSize, encoded.length);
            byte[] chunk = new byte[to - from];
            System.arraycopy(encoded, from, chunk, 0, chunk.length);

            VoicePacket packet = new VoicePacket(username, channel, i, total, chunk);
            result.add(VoicePacketCodec.encode(packet));
        }
        return result;
    }
}
