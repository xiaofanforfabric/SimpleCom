package com.xiaofan.ws;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 对单个已认证 WS 连接的封装，提供线程安全的文本帧发送。
 * Wrapper around a single authenticated WS connection with thread-safe text frame sending.
 */
public final class WsSender {

    private final Socket socket;
    private final OutputStream out;

    public WsSender(Socket socket, OutputStream out) {
        this.socket = socket;
        this.out = out;
    }

    /** 连接是否仍然打开 | Whether the connection is still open */
    public boolean isOpen() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    /**
     * 线程安全地向客户端发送 UTF-8 文本帧。
     * Thread-safe: send a UTF-8 text frame to the client.
     *
     * @param text 消息内容 | Message content
     * @throws IOException 发送失败 | Send failed
     */
    public void sendText(String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            writeWsFrame(out, 1, payload, payload.length);
        }
    }

    /**
     * 线程安全地向客户端发送二进制帧（语音数据包）。
     * Thread-safe: send a binary frame (voice data packet) to the client.
     *
     * @param data 原始字节数据 | Raw byte data
     * @throws IOException 发送失败 | Send failed
     */
    public void sendBinary(byte[] data) throws IOException {
        synchronized (out) {
            writeWsFrame(out, 2, data, data.length);
        }
    }

    /** 关闭底层 socket | Close the underlying socket */
    public void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    /**
     * 暴露 OutputStream 供外部发送 ping/pong/close 帧时同步。
     * Expose OutputStream for external callers (ping/pong/close) to synchronize on.
     */
    public OutputStream getOut() {
        return out;
    }

    /** 写入 WebSocket 数据帧（内部工具方法）| Write a WS data frame (internal utility) */
    static void writeWsFrame(OutputStream out, int opcode, byte[] payload, int len) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(14);
        b.put((byte) (0x80 | opcode));
        if (len <= 125) {
            b.put((byte) len);
        } else if (len <= 65535) {
            b.put((byte) 126);
            b.put((byte) (len >> 8));
            b.put((byte) len);
        } else {
            b.put((byte) 127);
            b.putLong(len);
        }
        b.flip();
        out.write(b.array(), 0, b.limit());
        if (len > 0) out.write(payload, 0, len);
        out.flush();
    }
}
