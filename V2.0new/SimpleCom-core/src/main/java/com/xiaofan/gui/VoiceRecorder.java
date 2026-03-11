package com.xiaofan.gui;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * PTT 录音管理器。
 * 按下 PTT 键时开始录音，松开时结束录音，将 PCM 数据分包后通过回调发送。
 *
 * 两种模式：
 * 1. 普通模式：录音完成后编码并分包发送（30KB/包）
 * 2. 低延迟模式：边录边发，实时编码并立即发送（5KB/包）
 *
 * 数据包格式（二进制 WebSocket 帧）：
 *   [4字节 用户名长度(big-endian int)]
 *   [N字节 用户名 UTF-8]
 *   [4字节 当前包序号(1-based, big-endian int)]
 *   [4字节 总包数(big-endian int)]
 *   [M字节 PCM 原始数据 或 Opus 编码数据]
 *
 * 录音格式：48000Hz, 16-bit, 单声道, 小端, 有符号（Opus 要求 48kHz）
 */
public final class VoiceRecorder {

    /** 普通模式：每个数据包的负载大小（字节）*/
    private static final int CHUNK_SIZE_NORMAL = 30 * 1024; // 30 KB
    
    /** 低延迟模式：每个数据包的负载大小（字节）= 5 个 Opus 帧 = 9600 字节 */
    private static final int CHUNK_SIZE_LOW_LATENCY = com.xiaofan.audio.OpusCodec.getFrameSizeBytes() * 5; // 5 帧 = 9600 bytes

    /** 录音格式：48000Hz, 16-bit, 单声道（Opus 仅支持 8/12/16/24/48 kHz）*/
    private static final AudioFormat RECORD_FORMAT =
            new AudioFormat(48000f, 16, 1, true, false);

    private final String username;

    /** 发送回调：(packetBytes) → 通过 WS 发送二进制帧 */
    private final Consumer<byte[]> onSendPacket;

    /** 状态变化回调：(isRecording) */
    private final Consumer<Boolean> onStateChange;

    /** 音频数据实时回调：(pcmChunk) → 用于实时显示音量和波形 */
    private volatile Consumer<byte[]> onAudioData;
    
    /** Opus 编码器（如果启用压缩）*/
    private volatile io.github.jaredmdobson.concentus.OpusEncoder opusEncoder;
    
    /** 是否启用低延迟模式 */
    private volatile boolean lowLatencyMode = false;

    private volatile boolean recording = false;
    private volatile Thread recordThread = null;
    private volatile int packetSeq = 0; // 低延迟模式下的包序号

    /**
     * @param username      已认证的 MC 用户名
     * @param onSendPacket  每个数据包就绪时的发送回调（在录音线程调用）
     * @param onStateChange 录音状态变化回调：true=开始录音, false=结束录音（在录音线程调用）
     */
    public VoiceRecorder(String username, Consumer<byte[]> onSendPacket, Consumer<Boolean> onStateChange) {
        this.username = username;
        this.onSendPacket = onSendPacket;
        this.onStateChange = onStateChange;
    }

    /**
     * 设置 Opus 编码器（启用压缩时调用）。
     * Set Opus encoder (call when compression is enabled).
     *
     * @param encoder Opus 编码器实例
     */
    public void setOpusEncoder(io.github.jaredmdobson.concentus.OpusEncoder encoder) {
        this.opusEncoder = encoder;
    }

    /**
     * 设置低延迟模式。
     * Set low latency mode.
     *
     * @param enabled true=启用低延迟（边录边发），false=普通模式（录完再发）
     */
    public void setLowLatencyMode(boolean enabled) {
        this.lowLatencyMode = enabled;
        System.out.println("[SimpleCom] 低延迟模式: " + (enabled ? "启用" : "关闭"));
    }

    /**
     * 设置音频数据实时回调（用于显示音量和波形）。
     * Set real-time audio data callback (for volume and waveform display).
     *
     * @param callback 参数为 PCM 数据块（16-bit 小端有符号）
     */
    public void setOnAudioData(Consumer<byte[]> callback) {
        this.onAudioData = callback;
    }

    /** 开始录音（幂等，已在录音中则忽略） */
    public void startRecording() {
        if (recording) return;
        recording = true;
        Thread t = new Thread(this::doRecord, "SimpleCom-Record");
        t.setDaemon(true);
        recordThread = t;
        t.start();
    }

    /** 停止录音（幂等） */
    public void stopRecording() {
        recording = false;
        Thread t = recordThread;
        if (t != null) t.interrupt();
    }

    public boolean isRecording() {
        return recording;
    }

    // ─────────────────────── 录音主逻辑 ──────────────────────────────────

    private void doRecord() {
        if (onStateChange != null) onStateChange.accept(true);

        TargetDataLine line = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, RECORD_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[SimpleCom] 不支持录音设备 | Microphone not supported");
                return;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(RECORD_FORMAT);
            line.start();

            if (lowLatencyMode) {
                // 低延迟模式：边录边发
                doRecordLowLatency(line);
            } else {
                // 普通模式：录完再发
                doRecordNormal(line);
            }

            line.stop();
            line.close();
            line = null;

        } catch (LineUnavailableException e) {
            System.err.println("[SimpleCom] 录音设备不可用 | Microphone unavailable: " + e.getMessage());
        } finally {
            if (line != null) {
                try { line.stop(); line.close(); } catch (Exception ignored) {}
            }
            recording = false;
            if (onStateChange != null) onStateChange.accept(false);
        }
    }

    /**
     * 普通模式：录完所有数据后再编码和分包发送。
     * Normal mode: record all data first, then encode and send in chunks.
     */
    private void doRecordNormal(TargetDataLine line) {
        ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];

        while (recording && !Thread.currentThread().isInterrupted()) {
            int read = line.read(buf, 0, buf.length);
            if (read > 0) {
                pcmBuffer.write(buf, 0, read);
                // 实时回调音频数据用于显示
                if (onAudioData != null) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buf, 0, chunk, 0, read);
                    onAudioData.accept(chunk);
                }
            }
        }

        byte[] allPcm = pcmBuffer.toByteArray();
        if (allPcm.length > 0) {
            sendPackets(allPcm, CHUNK_SIZE_NORMAL);
        }
    }

    /**
     * 低延迟模式：边录边发，累积到 5 个 Opus 帧（9600 字节）立即编码并发送。
     * Low latency mode: record and send immediately, encode and send every 5 Opus frames (9600 bytes).
     */
    private void doRecordLowLatency(TargetDataLine line) {
        packetSeq = 0; // 重置包序号
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // 每次读取 1 个 Opus 帧大小的数据
        int frameSize = com.xiaofan.audio.OpusCodec.getFrameSizeBytes();
        byte[] buf = new byte[frameSize];
        int chunkSize = CHUNK_SIZE_LOW_LATENCY;

        while (recording && !Thread.currentThread().isInterrupted()) {
            int read = line.read(buf, 0, buf.length);
            if (read > 0) {
                buffer.write(buf, 0, read);
                
                // 实时回调音频数据用于显示
                if (onAudioData != null) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buf, 0, chunk, 0, read);
                    onAudioData.accept(chunk);
                }

                // 累积到 5 个帧（9600 字节）立即发送
                if (buffer.size() >= chunkSize) {
                    byte[] pcmChunk = buffer.toByteArray();
                    buffer.reset();
                    
                    try {
                        sendSinglePacketLowLatency(pcmChunk);
                    } catch (AssertionError e) {
                        // Opus 编码器内部断言失败，重新初始化
                        System.err.println("[SimpleCom] Opus 编码器断言失败，正在重新初始化: " + e.getMessage());
                        reinitializeOpusEncoder();
                        // 跳过这个数据包（不降级到 PCM，避免编解码器不一致）
                        System.err.println("[SimpleCom] 跳过当前数据包以保持编解码器一致性");
                    }
                }
            }
        }

        // 发送剩余数据（补齐到帧大小）
        if (buffer.size() > 0) {
            byte[] remaining = buffer.toByteArray();
            // 补齐到帧大小的整数倍
            int paddedSize = ((remaining.length + frameSize - 1) / frameSize) * frameSize;
            byte[] pcmChunk = new byte[paddedSize];
            System.arraycopy(remaining, 0, pcmChunk, 0, remaining.length);
            // 剩余部分自动填充 0（静音）
            
            try {
                sendSinglePacketLowLatency(pcmChunk);
            } catch (AssertionError e) {
                System.err.println("[SimpleCom] Opus 编码器断言失败（剩余数据），跳过: " + e.getMessage());
                reinitializeOpusEncoder();
            }
        }
        
        // 发送结束标记（total = 0 表示结束）
        sendEndMarkerLowLatency();
    }

    // ─────────────────────── 分包发送 ────────────────────────────────────

    /**
     * 普通模式：编码后分包发送（带总包数）。
     * Normal mode: encode then send in chunks (with total count).
     */
    private void sendPackets(byte[] pcm, int chunkSize) {
        // 如果启用 Opus 编码，先编码
        byte[] dataToSend = null;
        if (opusEncoder != null) {
            try {
                dataToSend = encodeWithOpus(pcm);
                System.out.println("[SimpleCom] Opus 编码完成: " + pcm.length + " bytes -> " + dataToSend.length + " bytes");
            } catch (AssertionError e) {
                System.err.println("[SimpleCom] Opus 编码器断言失败，正在重新初始化: " + e.getMessage());
                reinitializeOpusEncoder();
                // 跳过这次录音，不发送（避免编解码器不一致）
                System.err.println("[SimpleCom] 跳过当前录音以保持编解码器一致性");
                return;
            } catch (Exception e) {
                System.err.println("[SimpleCom] Opus 编码失败，跳过当前录音: " + e.getMessage());
                return;
            }
        } else {
            // 未启用 Opus，使用原始 PCM
            dataToSend = pcm;
        }
        
        List<byte[]> chunks = splitIntoChunks(dataToSend, chunkSize);
        int total = chunks.size();
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < total; i++) {
            byte[] chunk = chunks.get(i);
            int seq = i + 1; // 1-based

            // 包头：[4字节用户名长度][用户名][4字节序号][4字节总数][数据]
            int headerLen = 4 + usernameBytes.length + 4 + 4;
            ByteBuffer packet = ByteBuffer.allocate(headerLen + chunk.length);
            packet.putInt(usernameBytes.length);
            packet.put(usernameBytes);
            packet.putInt(seq);
            packet.putInt(total);
            packet.put(chunk);

            if (onSendPacket != null) {
                onSendPacket.accept(packet.array());
            }
        }
    }

    /**
     * 低延迟模式：立即编码并发送单个数据包（total = -1 表示流式传输）。
     * Low latency mode: encode and send single packet immediately (total = -1 for streaming).
     */
    private void sendSinglePacketLowLatency(byte[] pcm) {
        // 如果启用 Opus 编码，先编码
        byte[] dataToSend = null;
        if (opusEncoder != null) {
            try {
                dataToSend = encodeWithOpus(pcm);
            } catch (AssertionError e) {
                // Opus 编码器内部断言失败，抛出让上层处理
                throw e;
            } catch (Exception e) {
                System.err.println("[SimpleCom] Opus 编码失败，跳过当前数据包: " + e.getMessage());
                return; // 跳过这个数据包
            }
        } else {
            // 未启用 Opus，使用原始 PCM
            dataToSend = pcm;
        }

        packetSeq++;
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);

        // 包头：[4字节用户名长度][用户名][4字节序号][4字节总数=-1表示流式][数据]
        int headerLen = 4 + usernameBytes.length + 4 + 4;
        ByteBuffer packet = ByteBuffer.allocate(headerLen + dataToSend.length);
        packet.putInt(usernameBytes.length);
        packet.put(usernameBytes);
        packet.putInt(packetSeq);
        packet.putInt(-1); // -1 表示低延迟流式传输
        packet.put(dataToSend);

        if (onSendPacket != null) {
            onSendPacket.accept(packet.array());
        }
    }

    /**
     * 低延迟模式：发送结束标记（total = 0）。
     * Low latency mode: send end marker (total = 0).
     */
    private void sendEndMarkerLowLatency() {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        int headerLen = 4 + usernameBytes.length + 4 + 4;
        ByteBuffer packet = ByteBuffer.allocate(headerLen);
        packet.putInt(usernameBytes.length);
        packet.put(usernameBytes);
        packet.putInt(packetSeq + 1);
        packet.putInt(0); // 0 表示结束

        if (onSendPacket != null) {
            onSendPacket.accept(packet.array());
        }
    }

    private static List<byte[]> splitIntoChunks(byte[] data, int chunkSize) {
        List<byte[]> result = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(chunkSize, data.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(data, offset, chunk, 0, len);
            result.add(chunk);
            offset += len;
        }
        return result;
    }

    // ─────────────────────── 数据包解析（客户端接收用）────────────────────

    /**
     * 解析收到的语音数据包。
     * Parse a received voice packet.
     *
     * @param data 原始字节数据
     * @return 解析结果，格式无效时返回 null
     */
    public static VoicePacket parsePacket(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            int usernameLen = buf.getInt();
            if (usernameLen <= 0 || usernameLen > 256) return null;
            byte[] usernameBytes = new byte[usernameLen];
            buf.get(usernameBytes);
            String username = new String(usernameBytes, StandardCharsets.UTF_8);
            int seq = buf.getInt();
            int total = buf.getInt();
            byte[] pcm = new byte[buf.remaining()];
            buf.get(pcm);
            return new VoicePacket(username, seq, total, pcm);
        } catch (Exception e) {
            return null;
        }
    }

    /** 语音数据包 */
    public static final class VoicePacket {
        public final String username;
        public final int seq;
        public final int total;
        public final byte[] pcm;

        public VoicePacket(String username, int seq, int total, byte[] pcm) {
            this.username = username;
            this.seq = seq;
            this.total = total;
            this.pcm = pcm;
        }
    }

    // ─────────────────────── Opus 编码 ────────────────────────────────────

    /**
     * 使用 Opus 编码 PCM 数据。
     * Encode PCM data with Opus.
     *
     * @param pcm 原始 PCM 数据
     * @return Opus 编码后的数据
     * @throws io.github.jaredmdobson.concentus.OpusException 如果编码失败
     */
    private byte[] encodeWithOpus(byte[] pcm) throws io.github.jaredmdobson.concentus.OpusException {
        int frameSize = com.xiaofan.audio.OpusCodec.getFrameSizeBytes();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        
        // 按帧编码
        int offset = 0;
        while (offset + frameSize <= pcm.length) {
            byte[] frame = new byte[frameSize];
            System.arraycopy(pcm, offset, frame, 0, frameSize);
            
            byte[] encoded = com.xiaofan.audio.OpusCodec.encode(opusEncoder, frame);
            
            // 写入编码后的帧长度（2字节）和数据
            output.write((encoded.length >> 8) & 0xFF);
            output.write(encoded.length & 0xFF);
            output.write(encoded, 0, encoded.length);
            
            offset += frameSize;
        }
        
        // 处理剩余不足一帧的数据（填充静音）
        if (offset < pcm.length) {
            byte[] lastFrame = new byte[frameSize];
            System.arraycopy(pcm, offset, lastFrame, 0, pcm.length - offset);
            // 剩余部分已经是 0（静音）
            
            byte[] encoded = com.xiaofan.audio.OpusCodec.encode(opusEncoder, lastFrame);
            output.write((encoded.length >> 8) & 0xFF);
            output.write(encoded.length & 0xFF);
            output.write(encoded, 0, encoded.length);
        }
        
        return output.toByteArray();
    }

    /**
     * 重新初始化 Opus 编码器（当编码器内部断言失败时调用）。
     * Reinitialize Opus encoder (called when encoder internal assertion fails).
     */
    private void reinitializeOpusEncoder() {
        try {
            System.out.println("[SimpleCom] 正在重新初始化 Opus 编码器...");
            opusEncoder = com.xiaofan.audio.OpusCodec.createEncoder();
            System.out.println("[SimpleCom] Opus 编码器重新初始化成功");
        } catch (io.github.jaredmdobson.concentus.OpusException e) {
            System.err.println("[SimpleCom] Opus 编码器重新初始化失败: " + e.getMessage());
            // 保持 opusEncoder 不变，下次录音会继续尝试使用
        }
    }
}
