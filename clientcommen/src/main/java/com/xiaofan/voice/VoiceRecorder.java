package com.xiaofan.voice;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 麦克风录音，输出 PCM short[]
 */
public final class VoiceRecorder {

    private static final int SAMPLE_RATE = OpusCodec.SAMPLE_RATE;
    private static final int CHANNELS = OpusCodec.CHANNELS;
    private static final int SAMPLE_SIZE_BITS = 16;

    private volatile boolean recording;
    private Thread recordThread;
    private TargetDataLine line;

    /** 开始录音，返回已录制的 PCM 数据（需在后台线程调用） */
    public void start() {
        if (recording) return;
        recording = true;
        recordThread = new Thread(this::doRecord, "SimpleCom-Record");
        recordThread.setDaemon(true);
        recordThread.start();
    }

    /** 停止录音，返回录制的 PCM 数据 */
    public short[] stop() {
        recording = false;
        if (recordThread != null) {
            try {
                recordThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recordThread = null;
        }
        return drainRecorded();
    }

    private final List<short[]> recordedChunks = new ArrayList<>();

    private void doRecord() {
        recordedChunks.clear();
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            int frameSize = format.getFrameSize();
            int bufferSize = SAMPLE_RATE * frameSize / 10; // 100ms
            byte[] buffer = new byte[bufferSize];

            while (recording && line.isOpen()) {
                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) {
                    short[] pcm = bytesToShorts(buffer, read);
                    recordedChunks.add(pcm);
                }
            }
        } catch (LineUnavailableException e) {
            // 麦克风不可用
        } finally {
            if (line != null && line.isOpen()) {
                line.stop();
                line.close();
            }
        }
    }

    /** 获取已录制的所有 PCM 并清空 */
    public short[] drainRecorded() {
        if (recordedChunks.isEmpty()) return new short[0];
        int total = 0;
        for (short[] c : recordedChunks) total += c.length;
        short[] result = new short[total];
        int pos = 0;
        for (short[] c : recordedChunks) {
            System.arraycopy(c, 0, result, pos, c.length);
            pos += c.length;
        }
        recordedChunks.clear();
        return result;
    }

    public boolean isRecording() {
        return recording;
    }

    private static short[] bytesToShorts(byte[] bytes, int len) {
        int count = len / 2;
        short[] shorts = new short[count];
        for (int i = 0; i < count; i++) {
            shorts[i] = (short) ((bytes[i * 2] & 0xFF) | ((bytes[i * 2 + 1] & 0xFF) << 8));
        }
        return shorts;
    }
}
