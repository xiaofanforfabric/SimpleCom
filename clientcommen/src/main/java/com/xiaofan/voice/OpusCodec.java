package com.xiaofan.voice;

import java.util.Arrays;

/**
 * 音频编解码（当前为 PCM 透传，可后续替换为 Opus）
 * 采样率 48000Hz，单声道，16bit
 */
public final class OpusCodec {

    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 1;
    public static final int FRAME_SIZE_MS = 20;
    public static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_SIZE_MS / 1000 * CHANNELS; // 960
    public static final int MAX_PACKET_BYTES = 4000;

    /** 编码 PCM (short[]) 为字节（当前透传，TODO: Opus） */
    public byte[] encode(short[] pcm) {
        if (pcm == null || pcm.length == 0) return new byte[0];
        byte[] out = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            out[i * 2] = (byte) (pcm[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        return out;
    }

    /** 解码字节为 PCM (short[])（当前透传，TODO: Opus） */
    public short[] decode(byte[] data) {
        if (data == null || data.length < 2) return new short[0];
        short[] pcm = new short[data.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) ((data[i * 2] & 0xFF) | ((data[i * 2 + 1] & 0xFF) << 8));
        }
        return pcm;
    }
}
