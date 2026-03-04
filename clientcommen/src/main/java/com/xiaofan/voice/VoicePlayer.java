package com.xiaofan.voice;

import javax.sound.sampled.*;
import java.util.Arrays;

/**
 * PCM 播放
 */
public final class VoicePlayer {

    private static final int SAMPLE_RATE = OpusCodec.SAMPLE_RATE;
    private static final int CHANNELS = OpusCodec.CHANNELS;

    private SourceDataLine line;

    /** 播放 PCM 数据 */
    public void play(short[] pcm) {
        if (pcm == null || pcm.length == 0) return;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
            if (line == null || !line.isOpen()) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
            }
            byte[] bytes = shortsToBytes(pcm);
            line.write(bytes, 0, bytes.length);
        } catch (LineUnavailableException e) {
            // 扬声器不可用
        }
    }

    public void close() {
        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
            line = null;
        }
    }

    private static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }
}
