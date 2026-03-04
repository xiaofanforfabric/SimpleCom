package com.xiaofan.voice;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 (username, channel) 缓存语音包，收齐后输出完整数据
 */
public final class VoicePacketAssembler {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final OpusCodec codec = new OpusCodec();
    private final VoicePlayer player = new VoicePlayer();

    /** 接收一个语音包，收齐则解码播放并返回发言人用户名 */
    public String feed(VoicePacket packet) {
        if (packet == null) return null;
        String key = packet.getUsername() + "|" + packet.getChannel();
        Session s = sessions.computeIfAbsent(key, k -> new Session(packet.getTotalCount()));
        s.add(packet.getCurrentIndex(), packet.getAudioData());

        if (s.isComplete()) {
            sessions.remove(key);
            byte[] full = s.assemble();
            short[] pcm = codec.decode(full);
            if (pcm.length > 0) {
                player.play(pcm);
                return packet.getUsername();
            }
        }
        return null;
    }

    public void clear() {
        sessions.clear();
    }

    private static class Session {
        private final int total;
        private final Map<Integer, byte[]> chunks = new TreeMap<>();

        Session(int total) {
            this.total = total;
        }

        void add(int index, byte[] data) {
            chunks.put(index, data);
        }

        boolean isComplete() {
            return chunks.size() >= total;
        }

        byte[] assemble() {
            int len = 0;
            for (byte[] b : chunks.values()) len += b.length;
            byte[] out = new byte[len];
            int pos = 0;
            for (byte[] b : chunks.values()) {
                System.arraycopy(b, 0, out, pos, b.length);
                pos += b.length;
            }
            return out;
        }
    }
}
