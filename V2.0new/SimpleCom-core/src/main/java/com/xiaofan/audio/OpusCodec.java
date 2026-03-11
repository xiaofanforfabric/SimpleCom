package com.xiaofan.audio;

import io.github.jaredmdobson.concentus.*;

/**
 * Opus 音频编解码器（使用 Concentus 纯 Java 实现）。
 * Opus audio codec (using Concentus pure Java implementation).
 *
 * 配置：48000Hz, 单声道, 64kbps, 20ms 帧
 * Opus 仅支持 8/12/16/24/48 kHz，必须用 48000Hz
 */
public final class OpusCodec {

    /** Opus 支持的采样率（48kHz） */
    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 1;
    public static final int BITRATE = 64000; // 64 kbps
    /** 20ms @ 48kHz = 960 samples */
    public static final int FRAME_SAMPLES = 960;

    private OpusCodec() {}

    /**
     * 创建 Opus 编码器。
     * Create Opus encoder.
     */
    public static OpusEncoder createEncoder() throws OpusException {
        OpusEncoder encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.setBitrate(BITRATE);
        encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
        encoder.setComplexity(5);
        return encoder;
    }

    /**
     * 创建 Opus 解码器。
     * Create Opus decoder.
     */
    public static OpusDecoder createDecoder() throws OpusException {
        return new OpusDecoder(SAMPLE_RATE, CHANNELS);
    }

    /**
     * 编码一帧 PCM（960 samples = 20ms @ 48kHz）为 Opus。
     * Encode one PCM frame (960 samples = 20ms @ 48kHz) to Opus.
     *
     * @param encoder Opus 编码器
     * @param pcm     16-bit 小端有符号 PCM 数据（48000Hz, 单声道）
     * @return Opus 编码后的字节数组
     */
    public static byte[] encode(OpusEncoder encoder, byte[] pcm) throws OpusException {
        short[] pcmShort = bytesToShorts(pcm);
        byte[] opusData = new byte[4000];
        int encodedBytes = encoder.encode(pcmShort, 0, FRAME_SAMPLES, opusData, 0, opusData.length);
        byte[] result = new byte[encodedBytes];
        System.arraycopy(opusData, 0, result, 0, encodedBytes);
        return result;
    }

    /**
     * 解码 Opus 帧为 PCM。
     * Decode Opus frame to PCM.
     *
     * @param decoder  Opus 解码器
     * @param opusData Opus 编码的字节数组
     * @return 16-bit 小端有符号 PCM 数据（48000Hz, 单声道）
     */
    public static byte[] decode(OpusDecoder decoder, byte[] opusData) throws OpusException {
        short[] pcmShort = new short[FRAME_SAMPLES * 2]; // 留足空间
        int decodedSamples = decoder.decode(opusData, 0, opusData.length, pcmShort, 0, FRAME_SAMPLES, false);
        return shortsToBytes(pcmShort, decodedSamples);
    }

    /**
     * 获取每帧的 PCM 字节数（用于分帧）。
     * Get PCM bytes per frame (for framing).
     */
    public static int getFrameSizeBytes() {
        return FRAME_SAMPLES * 2; // 16-bit = 2 bytes per sample = 1920 bytes
    }

    /** byte[]（小端 16-bit）→ short[] */
    private static short[] bytesToShorts(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            int low  = bytes[i * 2] & 0xFF;
            int high = bytes[i * 2 + 1];
            shorts[i] = (short) ((high << 8) | low);
        }
        return shorts;
    }

    /** short[] → byte[]（小端 16-bit） */
    private static byte[] shortsToBytes(short[] shorts, int length) {
        byte[] bytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            bytes[i * 2]     = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }
}
