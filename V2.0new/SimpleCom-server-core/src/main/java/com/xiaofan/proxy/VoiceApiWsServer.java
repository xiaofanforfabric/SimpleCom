package com.xiaofan.proxy;

import com.xiaofan.config.ProxyConfig;
import com.xiaofan.ws.VoiceApiConnectionRegistry;
import com.xiaofan.ws.WsSender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Voice Stream API WebSocket server (WSAPI).
 *
 * - 握手阶段校验 token：不匹配或缺失 → HTTP 403 并断开
 * - 连接成功后发送 serverstatus
 * - 每 5 秒发送一次 heartbeat 文本消息（并可作为保活）
 * - 服务端会把（非加密信道的）语音二进制包实时推送给所有 WSAPI 客户端
 */
public final class VoiceApiWsServer {

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int HEARTBEAT_INTERVAL_SEC = 5;

    private final ProxyConfig config;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SimpleCom-WSAPI");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SimpleCom-WSAPIHeartbeat");
        t.setDaemon(true);
        return t;
    });

    public VoiceApiWsServer(ProxyConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        if (running) return;
        if (!config.isVoiceApiEnabled()) {
            System.out.println("[SimpleCom] Voice API 未启用，跳过启动 | Voice API disabled, skipping.");
            return;
        }
        InetAddress bind = InetAddress.getByName(config.getVoiceApiBind());
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bind, config.getVoiceApiPort()), 50);
        running = true;
        executor.execute(this::acceptLoop);
        System.out.println("[SimpleCom] Voice API WS 已启动 | Voice API WS started: " + config.getVoiceApiBind() + ":" + config.getVoiceApiPort());
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        executor.shutdown();
        heartbeatScheduler.shutdown();
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(10000);
                executor.execute(() -> handle(client));
            } catch (SocketException e) {
                if (running) System.err.println("[SimpleCom] WSAPI accept 异常 | WSAPI accept error: " + e.getMessage());
            } catch (IOException e) {
                if (running) System.err.println("[SimpleCom] WSAPI accept 异常 | WSAPI accept error: " + e.getMessage());
            }
        }
    }

    private void handle(Socket client) {
        String clientIp = client.getInetAddress().getHostAddress();
        try {
            System.out.println("[SimpleCom] WSAPI 连接接入 | WSAPI connection from: " + clientIp);
            handleWebSocket(client, client.getInputStream());
        } catch (Exception e) {
            System.err.println("[SimpleCom] WSAPI 连接处理异常 | WSAPI error from " + clientIp
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            closeQuietly(client);
        }
    }

    private void handleWebSocket(Socket client, InputStream wsStream) {
        String socketIp = client.getInetAddress().getHostAddress();
        UUID connId = null;
        ScheduledFuture<?> heartbeatTask = null;
        try {
            byte[] requestBytes = readHttpHeader(wsStream);
            if (requestBytes == null) return;
            String request = new String(requestBytes, StandardCharsets.ISO_8859_1);

            String token = extractTokenFromRequest(request);
            if (token == null || token.isEmpty() || !token.equals(config.getVoiceApiToken())) {
                System.out.println("[SimpleCom] WSAPI token 校验失败，拒绝连接 | WSAPI token rejected: " + socketIp);
                sendHttpError(client, 403, "Forbidden");
                return;
            }

            String key = extractHeader(request, "Sec-WebSocket-Key");
            if (key == null) {
                System.err.println("[SimpleCom] WSAPI 握手失败：缺少 Sec-WebSocket-Key | WSAPI handshake missing key from " + socketIp);
                sendHttpError(client, 400, "Bad Request");
                return;
            }

            String accept = computeWsAccept(key);
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "\r\n";
            OutputStream out = client.getOutputStream();
            out.write(response.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            client.setSoTimeout(0);

            // register
            WsSender sender = new WsSender(client, out);
            connId = VoiceApiConnectionRegistry.getInstance().register(sender);

            // send server status
            String serverStatus = buildServerStatusJson();
            sender.sendText(serverStatus);

            // heartbeat every 5s
            final WsSender senderRef = sender;
            final Socket clientRef = client;
            heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    String hb = "{\"type\":\"heartbeat\",\"time\":" + System.currentTimeMillis() + "}";
                    senderRef.sendText(hb);
                } catch (IOException e) {
                    closeQuietly(clientRef);
                }
            }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

            // frame loop: ignore any incoming frames except close/ping
            wsFrameLoop(client, wsStream, out, socketIp);
        } catch (Exception e) {
            System.err.println("[SimpleCom] WSAPI 处理异常 | WSAPI error from " + socketIp
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            if (connId != null) VoiceApiConnectionRegistry.getInstance().unregister(connId);
            closeQuietly(client);
        }
    }

    private void wsFrameLoop(Socket client, InputStream in, OutputStream out, String wsClientIp) throws IOException {
        byte[] buf = new byte[65536];
        int[] payloadLen = new int[1];
        while (running && !client.isClosed()) {
            int opcode = readWsFrame(in, buf, payloadLen);
            if (opcode < 0) break;
            if (opcode == 8) {
                synchronized (out) { sendWsClose(out, 1000, "Normal closure"); }
                break;
            }
            if (opcode == 9) {
                synchronized (out) { writeWsFrame(out, 10, buf, payloadLen[0]); }
            }
            // opcode==1/2: ignore (WSAPI client is receive-only)
        }
        System.out.println("[SimpleCom] WSAPI 连接断开 | WSAPI closed: " + wsClientIp);
    }

    private String buildServerStatusJson() {
        return "{\"type\":\"serverstatus\",\"compressionEncoder\":"
                + config.isCompressionEncoder()
                + ",\"lowLatency\":"
                + config.isLowLatency()
                + "}";
    }

    private static byte[] readHttpHeader(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] one = new byte[1];
        int prev = -1, prev2 = -1, prev3 = -1;
        while (true) {
            int r = in.read(one);
            if (r < 0) return null;
            buf.write(one[0]);
            if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && one[0] == '\n') break;
            if (prev == '\n' && one[0] == '\n') break;
            prev3 = prev2; prev2 = prev; prev = one[0];
            if (buf.size() > 64 * 1024) return null;
        }
        return buf.toByteArray();
    }

    private String extractTokenFromRequest(String request) {
        // 1) Authorization: Bearer <token>
        String auth = extractHeader(request, "Authorization");
        if (auth != null) {
            String v = auth.trim();
            if (v.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                String t = v.substring(7).trim();
                if (!t.isEmpty()) return t;
            }
        }
        // 2) GET /path?token=xxx HTTP/1.1
        String firstLine = requestLine(request);
        if (firstLine != null) {
            int sp1 = firstLine.indexOf(' ');
            int sp2 = sp1 < 0 ? -1 : firstLine.indexOf(' ', sp1 + 1);
            if (sp1 > 0 && sp2 > sp1) {
                String target = firstLine.substring(sp1 + 1, sp2).trim();
                int q = target.indexOf('?');
                if (q >= 0) {
                    String qs = target.substring(q + 1);
                    String t = parseQueryParam(qs, "token");
                    if (t != null && !t.isEmpty()) return t;
                }
            }
        }
        // 3) Sec-WebSocket-Protocol: <token>
        String proto = extractHeader(request, "Sec-WebSocket-Protocol");
        if (proto != null && !proto.trim().isEmpty()) return proto.trim();
        return null;
    }

    private static String parseQueryParam(String qs, String key) {
        if (qs == null || key == null) return null;
        String[] parts = qs.split("&");
        for (String p : parts) {
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String k = p.substring(0, eq);
            if (!k.equals(key)) continue;
            String v = p.substring(eq + 1);
            try {
                return java.net.URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {
                return v;
            }
        }
        return null;
    }

    private static String requestLine(String request) {
        if (request == null) return null;
        int i = request.indexOf('\n');
        if (i < 0) return request.trim();
        return request.substring(0, i).trim();
    }

    private static String extractHeader(String request, String headerName) {
        if (request == null || headerName == null) return null;
        String[] lines = request.split("\r\n");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx <= 0) continue;
            String name = line.substring(0, idx).trim();
            if (name.equalsIgnoreCase(headerName)) {
                return line.substring(idx + 1).trim();
            }
        }
        return null;
    }

    private static String computeWsAccept(String key) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update((key + WS_MAGIC).getBytes(StandardCharsets.ISO_8859_1));
        return java.util.Base64.getEncoder().encodeToString(sha1.digest());
    }

    private static void sendHttpError(Socket client, int code, String status) throws IOException {
        String body = "HTTP/1.1 " + code + " " + status + "\r\nConnection: close\r\n\r\n";
        client.getOutputStream().write(body.getBytes(StandardCharsets.ISO_8859_1));
        client.getOutputStream().flush();
    }

    private void sendWsClose(OutputStream out, int code, String reason) throws IOException {
        byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((code >> 8) & 0xFF);
        payload[1] = (byte) (code & 0xFF);
        if (reasonBytes.length > 0) System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        writeWsFrame(out, 8, payload, payload.length);
        out.flush();
    }

    private int readWsFrame(InputStream in, byte[] buf, int[] outPayloadLen) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return -1;
        int opcode = b0 & 0x0F;
        int b1 = in.read();
        if (b1 < 0) return -1;
        long len = b1 & 0x7F;
        if (len == 126) {
            int hi = in.read(), lo = in.read();
            if (hi < 0 || lo < 0) return -1;
            len = ((hi & 0xFF) << 8) | (lo & 0xFF);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF);
        }
        int ilen = (int) Math.min(len, buf.length);
        if (outPayloadLen != null) outPayloadLen[0] = ilen;
        boolean masked = (b1 & 0x80) != 0;
        if (masked) {
            byte[] mask = new byte[4];
            if (readFully(in, mask, 0, 4) != 4) return -1;
            if (readFully(in, buf, 0, ilen) != ilen) return -1;
            for (int i = 0; i < ilen; i++) buf[i] ^= mask[i % 4];
        } else {
            if (readFully(in, buf, 0, ilen) != ilen) return -1;
        }
        if (len > buf.length) skipFully(in, (int) (len - buf.length));
        return opcode;
    }

    private static void writeWsFrame(OutputStream out, int opcode, byte[] payload, int len) throws IOException {
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

    private static int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(b, off + total, len - total);
            if (r <= 0) break;
            total += r;
        }
        return total;
    }

    private static void skipFully(InputStream in, int n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) break;
            n -= (int) s;
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try { s.close(); } catch (IOException ignored) {}
    }
}

