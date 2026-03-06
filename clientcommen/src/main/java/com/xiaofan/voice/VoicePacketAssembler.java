package com.xiaofan.voice;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 按 (username, channel) 缓存语音包，收齐后输出完整数据
 * 以 MC 用户名为唯一性：每个玩家的语音在独立线程中解码、独立播放器播放，多人同时说话时混音同时播放，不乱序。
 * lowLatency 时收到包立即提交到该玩家专属线程解码播放。
 */
public final class VoicePacketAssembler {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean useCompressionEncoder = false;
    private volatile boolean lowLatency = false;

    /** 按用户名：专属单线程执行器，保证同一玩家包顺序解码播放 */
    private final Map<String, ExecutorService> userExecutors = new ConcurrentHashMap<>();
    /** 按用户名：专属解码器（Opus 可能非线程安全） */
    private final Map<String, OpusCodec> userCodecs = new ConcurrentHashMap<>();
    /** 按用户名：专属播放器（独立 SourceDataLine，系统混音） */
    private final Map<String, VoicePlayer> userPlayers = new ConcurrentHashMap<>();

    /** 低延迟去重：按用户名记录上次播放的包标识与时间 */
    private final Map<String, String> lastPacketKeyByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPlayTimeByUser = new ConcurrentHashMap<>();
    private static final long LOW_LATENCY_DEDUP_MS = 80;

    /** 设置是否使用压缩编码（收到服务端握手后由客户端调用） */
    public void setUseCompressionEncoder(boolean useCompressionEncoder) {
        this.useCompressionEncoder = useCompressionEncoder;
    }

    /** 设置是否低延迟（收到服务端握手后由客户端调用，true 时收到包立即提交到该玩家线程播放） */
    public void setLowLatency(boolean lowLatency) {
        this.lowLatency = lowLatency;
    }

    /** 接收一个语音包，收齐则解码播放（或低延迟时提交到该玩家专属线程立即播放）并返回发言人用户名 */
    public String feed(VoicePacket packet) {
        if (packet == null) return null;
        String username = packet.getUsername();
        if (username == null || username.isEmpty()) return null;

        if (lowLatency) {
            String packetKey = username + "|" + packet.getChannel() + "|" + packet.getCurrentIndex() + "|" + packet.getTotalCount();
            long now = System.currentTimeMillis();
            String lastKey = lastPacketKeyByUser.get(username);
            Long lastTime = lastPlayTimeByUser.get(username);
            if (packetKey.equals(lastKey) && lastTime != null && (now - lastTime) < LOW_LATENCY_DEDUP_MS) {
                return null;
            }
            lastPacketKeyByUser.put(username, packetKey);
            lastPlayTimeByUser.put(username, now);

            byte[] data = packet.getAudioData();
            if (data == null || data.length == 0) return username;

            final boolean useCompression = this.useCompressionEncoder;
            executorFor(username).execute(() -> {
                OpusCodec codec = codecFor(username);
                VoicePlayer player = playerFor(username);
                short[] pcm = codec.decode(data, useCompression);
                if (pcm != null && pcm.length > 0) {
                    player.play(pcm);
                }
            });
            return username;
        }

        String key = username + "|" + packet.getChannel();
        Session s = sessions.computeIfAbsent(key, k -> new Session(packet.getTotalCount()));
        s.add(packet.getCurrentIndex(), packet.getAudioData());

        if (s.isComplete()) {
            sessions.remove(key);
            byte[] full = s.assemble();
            final boolean useCompression = this.useCompressionEncoder;
            executorFor(username).execute(() -> {
                OpusCodec codec = codecFor(username);
                VoicePlayer player = playerFor(username);
                short[] pcm = codec.decode(full, useCompression);
                if (pcm != null && pcm.length > 0) {
                    player.play(pcm);
                }
            });
            return username;
        }
        return null;
    }

    private ExecutorService executorFor(String username) {
        return userExecutors.computeIfAbsent(username, u -> {
            return Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SimpleCom-Voice-" + u);
                t.setDaemon(true);
                return t;
            });
        });
    }

    private OpusCodec codecFor(String username) {
        return userCodecs.computeIfAbsent(username, u -> new OpusCodec());
    }

    private VoicePlayer playerFor(String username) {
        return userPlayers.computeIfAbsent(username, u -> new VoicePlayer());
    }

    public void clear() {
        sessions.clear();
        lastPacketKeyByUser.clear();
        lastPlayTimeByUser.clear();
        for (ExecutorService ex : userExecutors.values()) {
            ex.shutdown();
        }
        userExecutors.clear();
        for (VoicePlayer p : userPlayers.values()) {
            p.close();
        }
        userPlayers.clear();
        userCodecs.clear();
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
