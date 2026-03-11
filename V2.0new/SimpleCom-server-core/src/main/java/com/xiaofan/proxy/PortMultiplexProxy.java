package com.xiaofan.proxy;

import com.xiaofan.config.ProxyConfig;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 端口复用代理：监听单一对外端口，根据首包协议路由到 MC 或 WS 后端。纯 Java，不依赖 Bukkit。
 */
public final class PortMultiplexProxy {

    private static final byte[] GET_PREFIX = new byte[]{'G', 'E', 'T', ' '};

    private final ProxyConfig config;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SimpleCom-MCProxy");
        t.setDaemon(true);
        return t;
    });

    public PortMultiplexProxy(ProxyConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        if (running) return;
        if (!config.isProxyEnabled()) {
            System.out.println("[SimpleCom] 端口复用代理已禁用，跳过启动 | Port-mux proxy is disabled, skipping. WS server will still run.");
            return;
        }
        InetAddress bindAddr = InetAddress.getByName(config.getBindAddress());
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindAddr, config.getListenPort()), 50);
        running = true;
        executor.execute(this::acceptLoop);
        System.out.println("[SimpleCom] 端口复用代理已启动 | Port-mux proxy started: "
                + config.getBindAddress() + ":" + config.getListenPort()
                + "  (MC→" + config.getMcBackendHost() + ":" + config.getMcBackendPort()
                + ", WS→127.0.0.1:" + config.getWsPort() + ")");
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        executor.shutdown();
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(5000);
                executor.execute(() -> handle(client));
            } catch (SocketException e) {
                if (running) System.err.println("[SimpleCom] MC 代理 accept 异常 | MC proxy accept: " + e.getMessage());
            } catch (IOException e) {
                if (running) System.err.println("[SimpleCom] MC 代理 accept 异常 | MC proxy accept: " + e.getMessage());
            }
        }
    }

    private static boolean isHttpGet(byte[] first4) {
        for (int i = 0; i < GET_PREFIX.length; i++) {
            if (first4[i] != GET_PREFIX[i]) return false;
        }
        return true;
    }

    private void handle(Socket client) {
        byte[] peek = new byte[4];
        try {
            InputStream in = client.getInputStream();
            int n = readFully(in, peek, 0, 4);
            if (n < 4) {
                closeQuietly(client);
                return;
            }
            if (isHttpGet(peek)) {
                System.out.println("[SimpleCom] WebSocket 连接接入，转发到 WS 服务器 | WS connection, forwarding to WS server: "
                        + client.getInetAddress().getHostAddress());
                forwardToWsServer(client, in, peek);
            } else {
                forwardToMinecraft(client, in, peek);
            }
        } catch (Exception e) {
            System.err.println("[SimpleCom] 代理连接处理异常 | Proxy connection error: " + e.getMessage());
            closeQuietly(client);
        }
    }

    private void forwardToWsServer(Socket client, InputStream clientIn, byte[] first4) {
        Socket wsBackend = null;
        try {
            String realIp = client.getInetAddress().getHostAddress();
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            headerBuf.write(first4);
            byte[] one = new byte[1];
            int prev = -1, prev2 = -1, prev3 = -1;
            while (true) {
                int r = clientIn.read(one);
                if (r < 0) break;
                headerBuf.write(one[0]);
                if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && one[0] == '\n') break;
                if (prev == '\n' && one[0] == '\n') break;
                prev3 = prev2; prev2 = prev; prev = one[0];
            }
            byte[] rawRequest = headerBuf.toByteArray();

            wsBackend = new Socket("127.0.0.1", config.getWsPort());
            wsBackend.setSoTimeout(10000);
            OutputStream wsBackendOut = wsBackend.getOutputStream();
            wsBackendOut.write(("X-Real-IP: " + realIp + "\n").getBytes(StandardCharsets.ISO_8859_1));
            wsBackendOut.write(rawRequest);
            wsBackendOut.flush();
            InputStream wsBackendIn = wsBackend.getInputStream();
            client.setSoTimeout(0);

            ByteArrayOutputStream respBuf = new ByteArrayOutputStream();
            byte[] respOne = new byte[1];
            int p = -1, p2 = -1, p3 = -1;
            while (true) {
                int rr = wsBackendIn.read(respOne);
                if (rr < 0) break;
                respBuf.write(respOne[0]);
                if (p3 == '\r' && p2 == '\n' && p == '\r' && respOne[0] == '\n') break;
                p3 = p2; p2 = p; p = respOne[0];
            }
            OutputStream clientOut = client.getOutputStream();
            clientOut.write(respBuf.toByteArray());
            clientOut.flush();
            wsBackend.setSoTimeout(0);

            final Socket cs = client;
            final Socket ws = wsBackend;
            executor.execute(() -> copy(clientIn, wsBackendOut, cs, ws));
            copy(wsBackendIn, clientOut, ws, cs);
        } catch (IOException e) {
            System.err.println("[SimpleCom] WS 后端连接失败 (port=" + config.getWsPort() + "): " + e.getMessage());
        } finally {
            closeQuietly(client);
            closeQuietly(wsBackend);
        }
    }

    private void forwardToMinecraft(Socket client, InputStream clientIn, byte[] first4) {
        Socket backend = null;
        try {
            backend = new Socket(config.getMcBackendHost(), config.getMcBackendPort());
            backend.setSoTimeout(0);
            OutputStream backendOut = backend.getOutputStream();
            String realIp = client.getInetAddress().getHostAddress();
            byte[] handshakeToBackend = rewriteHandshakeWithRealIp(first4, clientIn, realIp);
            if (handshakeToBackend == null) {
                backendOut.write(first4);
                backendOut.flush();
            } else {
                backendOut.write(handshakeToBackend);
                backendOut.flush();
            }
            byte[] loginStartPacket = readOnePacket(clientIn);
            if (loginStartPacket != null) {
                String username = parseLoginStartUsername(loginStartPacket);
                if (username != null) {
                    System.out.println("[SimpleCom] 玩家 " + username + " 通过代理连接 | Player " + username + " connected via proxy");
                }
                backendOut.write(loginStartPacket);
                backendOut.flush();
            }

            InputStream backendIn = backend.getInputStream();
            client.setSoTimeout(0);

            final Socket clientSocket = client;
            final Socket backendSocket = backend;
            executor.execute(() -> copy(clientIn, backendOut, clientSocket, backendSocket));
            copy(backendIn, client.getOutputStream(), backendSocket, clientSocket);
        } catch (IOException e) {
            System.err.println("[SimpleCom] MC 后端连接失败 (port=" + config.getMcBackendPort() + "): " + e.getMessage());
        } finally {
            closeQuietly(client);
            closeQuietly(backend);
        }
    }

    private byte[] rewriteHandshakeWithRealIp(byte[] first4, InputStream clientIn, String realIp) throws IOException {
        InputStream combined = new SequenceInputStream(new ByteArrayInputStream(first4), clientIn);
        int payloadLen = readVarInt(combined);
        if (payloadLen <= 0 || payloadLen > 65536) return null;
        byte[] payload = new byte[payloadLen];
        if (readFully(combined, payload, 0, payloadLen) != payloadLen) return null;

        int[] out = new int[1];
        int pos = 0;
        int packetId = readVarIntFrom(payload, pos);
        pos = skipVarInt(payload, pos, out);
        int protocol = readVarIntFrom(payload, pos);
        pos = skipVarInt(payload, pos, out);
        int addrLen = readVarIntFrom(payload, pos);
        pos = skipVarInt(payload, pos, out);
        if (pos + addrLen > payload.length) return null;
        int portOff = pos + addrLen;
        if (portOff + 2 > payload.length) return null;
        int port = ((payload[portOff] & 0xFF) << 8) | (payload[portOff + 1] & 0xFF);
        int nextStateOff = portOff + 2;
        if (nextStateOff >= payload.length) return null;
        int nextState = readVarIntFrom(payload, nextStateOff);

        byte[] addrBytes = realIp.getBytes(StandardCharsets.UTF_8);
        if (addrBytes.length > 65535) return null;
        ByteArrayOutputStream newPayload = new ByteArrayOutputStream();
        writeVarInt(newPayload, packetId);
        writeVarInt(newPayload, protocol);
        writeVarInt(newPayload, addrBytes.length);
        newPayload.write(addrBytes);
        newPayload.write((port >> 8) & 0xFF);
        newPayload.write(port & 0xFF);
        writeVarInt(newPayload, nextState);
        byte[] newPayloadArr = newPayload.toByteArray();
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        writeVarInt(full, newPayloadArr.length);
        full.write(newPayloadArr);
        return full.toByteArray();
    }

    private static int readVarInt(InputStream in) throws IOException {
        int v = 0, shift = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.read();
            if (b < 0) return -1;
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
        }
        return -1;
    }

    private static void writeVarInt(OutputStream out, int v) throws IOException {
        while ((v & 0xFFFFFF80) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v & 0x7F);
    }

    private static int readVarIntFrom(byte[] b, int off) {
        int v = 0, shift = 0;
        for (int i = 0; i < 5 && off + i < b.length; i++) {
            int x = b[off + i] & 0xFF;
            v |= (x & 0x7F) << shift;
            if ((x & 0x80) == 0) return v;
            shift += 7;
        }
        return 0;
    }

    private static int skipVarInt(byte[] b, int off, int[] outValue) {
        int v = 0, shift = 0;
        for (int i = 0; i < 5 && off + i < b.length; i++) {
            int x = b[off + i] & 0xFF;
            v |= (x & 0x7F) << shift;
            if ((x & 0x80) == 0) {
                if (outValue != null) outValue[0] = v;
                return off + i + 1;
            }
            shift += 7;
        }
        if (outValue != null) outValue[0] = 0;
        return off;
    }

    private byte[] readOnePacket(InputStream clientIn) throws IOException {
        int payloadLen = readVarInt(clientIn);
        if (payloadLen <= 0 || payloadLen > 65536) return null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeVarInt(buf, payloadLen);
        byte[] payload = new byte[payloadLen];
        if (readFully(clientIn, payload, 0, payloadLen) != payloadLen) return null;
        buf.write(payload);
        return buf.toByteArray();
    }

    private String parseLoginStartUsername(byte[] packet) {
        if (packet == null || packet.length < 2) return null;
        int[] out = new int[1];
        int pos = skipVarInt(packet, 0, out);
        if (pos >= packet.length) return null;
        int packetId = readVarIntFrom(packet, pos);
        if (packetId != 0) return null;
        pos = skipVarInt(packet, pos, out);
        if (pos >= packet.length) return null;
        int nameLen = readVarIntFrom(packet, pos);
        pos = skipVarInt(packet, pos, out);
        if (pos + nameLen > packet.length || nameLen <= 0 || nameLen > 65536) return null;
        return new String(packet, pos, nameLen, StandardCharsets.UTF_8);
    }

    private void copy(InputStream from, OutputStream to, Socket a, Socket b) {
        byte[] buf = new byte[8192];
        try {
            int r;
            while ((r = from.read(buf)) != -1) {
                to.write(buf, 0, r);
                to.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(a);
            closeQuietly(b);
        }
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

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try { s.close(); } catch (IOException ignored) {}
    }
}
