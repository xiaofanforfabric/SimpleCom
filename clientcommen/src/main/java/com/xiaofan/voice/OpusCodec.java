package com.xiaofan.voice;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * 音频编解码：PCM 透传 或 Opus（64 kbps，20ms 帧）
 * 采样率 48000Hz，单声道，16bit
 * 通过反射调用 org.concentus（concentus 需通过 Fabric/Forge 的 shadowBundle 打进 mod，且不要 relocate）。
 */
public final class OpusCodec {

    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 1;
    public static final int FRAME_SIZE_MS = 20;
    public static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_SIZE_MS / 1000 * CHANNELS; // 960
    public static final int MAX_PACKET_BYTES = 4000;
    /** Opus 编码码率 64 kbps */
    public static final int OPUS_BITRATE = 64000;

    private static final Logger LOG = Logger.getLogger("SimpleCom.Opus");
    private static final OpusImpl OPUS = OpusImpl.create();

    /** 编码 PCM (short[]) 为字节。useOpus=false 透传 PCM；useOpus=true 使用 Opus 64kbps，输出带 2 字节小端帧长前缀的帧序列 */
    public byte[] encode(short[] pcm, boolean useOpus) {
        if (pcm == null || pcm.length == 0) return new byte[0];
        if (!useOpus || OPUS == null)
            return encodePcm(pcm);
        return OPUS.encode(pcm);
    }

    /** 解码字节为 PCM (short[])。useOpus=false 按 PCM 解析；useOpus=true 按 [2B len][opus 帧] 解析并解码 */
    public short[] decode(byte[] data, boolean useOpus) {
        if (data == null || data.length < 2) return new short[0];
        if (!useOpus || OPUS == null)
            return decodePcm(data);
        return OPUS.decode(data);
    }

    /** 兼容旧调用：按 PCM 透传 */
    public byte[] encode(short[] pcm) {
        return encode(pcm, false);
    }

    /** 兼容旧调用：按 PCM 透传 */
    public short[] decode(byte[] data) {
        return decode(data, false);
    }

    private static byte[] encodePcm(short[] pcm) {
        byte[] out = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            out[i * 2] = (byte) (pcm[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        return out;
    }

    private static short[] decodePcm(byte[] data) {
        short[] pcm = new short[data.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) ((data[i * 2] & 0xFF) | ((data[i * 2 + 1] & 0xFF) << 8));
        }
        return pcm;
    }

    private static final class OpusImpl {
        private final Object encoder;
        private final Object decoder;
        private final java.lang.reflect.Method encodeMethod;
        private final java.lang.reflect.Method decodeMethod;

        static OpusImpl create() {
            String[] packages = { "org.concentus", "concentus", "io.github.jaredmdobson.concentus" };
            Throwable lastErr = null;
            for (String pkg : packages) {
                try {
                    OpusImpl impl = tryCreate(pkg);
                    if (impl != null) {
                        LOG.info("[SimpleCom] Opus 编码器已加载 (package=" + pkg + ")，码率 " + (OPUS_BITRATE / 1000) + " kbps");
                        return impl;
                    }
                } catch (Throwable t) {
                    lastErr = t;
                }
            }
            LOG.warning("[SimpleCom] Opus 不可用，将使用 PCM 透传。原因: " + (lastErr != null ? (lastErr.getClass().getSimpleName() + " - " + lastErr.getMessage()) : "未找到 concentus 类"));
            return null;
        }

        @SuppressWarnings("unchecked")
        private static OpusImpl tryCreate(String pkg) {
            try {
                Class<?> encClass = Class.forName(pkg + ".OpusEncoder");
                Class<?> decClass = Class.forName(pkg + ".OpusDecoder");
                Class<?> appClass = Class.forName(pkg + ".OpusApplication");
                Object voip = Enum.valueOf((Class<Enum>) appClass, "OPUS_APPLICATION_VOIP");
                java.lang.reflect.Constructor<?> encCtor = encClass.getConstructor(int.class, int.class, appClass);
                Object encoder = encCtor.newInstance(SAMPLE_RATE, CHANNELS, voip);
                try {
                    java.lang.reflect.Field bitrateField = encClass.getField("user_bitrate_bps");
                    bitrateField.set(encoder, OPUS_BITRATE);
                } catch (NoSuchFieldException e) {
                    try {
                        encClass.getMethod("setBitrate", int.class).invoke(encoder, OPUS_BITRATE);
                    } catch (NoSuchMethodException ignored) { }
                }
                Object decoder = decClass.getConstructor(int.class, int.class).newInstance(SAMPLE_RATE, CHANNELS);
                java.lang.reflect.Method enc = encClass.getMethod("encode", short[].class, int.class, int.class, byte[].class, int.class, int.class);
                java.lang.reflect.Method dec = decClass.getMethod("decode", byte[].class, int.class, int.class, short[].class, int.class, int.class, boolean.class);
                return new OpusImpl(encoder, decoder, enc, dec);
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                        ? ((java.lang.reflect.InvocationTargetException) t).getTargetException() : t;
                if (cause != null) t = cause;
                throw new RuntimeException("Opus load fail (" + pkg + "): " + t.getClass().getSimpleName() + " - " + t.getMessage(), t);
            }
        }

        OpusImpl(Object encoder, Object decoder, java.lang.reflect.Method encodeMethod, java.lang.reflect.Method decodeMethod) {
            this.encoder = encoder;
            this.decoder = decoder;
            this.encodeMethod = encodeMethod;
            this.decodeMethod = decodeMethod;
        }

        byte[] encode(short[] pcm) {
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] packetBuf = new byte[MAX_PACKET_BYTES];
                int offset = 0;
                while (offset + FRAME_SAMPLES <= pcm.length) {
                    int written = (Integer) encodeMethod.invoke(encoder, pcm, offset, FRAME_SAMPLES, packetBuf, 0, packetBuf.length);
                    offset += FRAME_SAMPLES;
                    if (written > 0) {
                        out.write(written & 0xFF);
                        out.write((written >> 8) & 0xFF);
                        out.write(packetBuf, 0, written);
                    }
                }
                return out.toByteArray();
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                        ? ((java.lang.reflect.InvocationTargetException) t).getTargetException() : t;
                LOG.warning("[SimpleCom] Opus 编码异常，本次回退 PCM: " + (cause != null ? cause.getMessage() : t.getMessage()));
                return encodePcm(pcm);
            }
        }

        short[] decode(byte[] data) {
            try {
                java.util.List<short[]> chunks = new java.util.ArrayList<>();
                int pos = 0;
                short[] frameOut = new short[FRAME_SAMPLES * 2];
                while (pos + 2 <= data.length) {
                    int len = (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
                    pos += 2;
                    if (len <= 0 || pos + len > data.length) break;
                    int ns = (Integer) decodeMethod.invoke(decoder, data, pos, len, frameOut, 0, frameOut.length, false);
                    pos += len;
                    if (ns > 0)
                        chunks.add(Arrays.copyOf(frameOut, ns));
                }
                int total = 0;
                for (short[] c : chunks) total += c.length;
                short[] result = new short[total];
                int i = 0;
                for (short[] c : chunks) {
                    System.arraycopy(c, 0, result, i, c.length);
                    i += c.length;
                }
                return result;
            } catch (Throwable t) {
                return decodePcm(data);
            }
        }
    }
}
