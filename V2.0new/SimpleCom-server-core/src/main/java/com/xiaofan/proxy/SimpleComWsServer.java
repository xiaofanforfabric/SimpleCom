package com.xiaofan.proxy;

import com.xiaofan.config.ProxyConfig;
import com.xiaofan.ws.EncryptedChannelManager;
import com.xiaofan.ws.SimpleComWsHandler;
import com.xiaofan.ws.VoiceApiConnectionRegistry;
import com.xiaofan.ws.WsAuthResult;
import com.xiaofan.ws.WsConnectionRegistry;
import com.xiaofan.ws.WsSender;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 独立 WebSocket 服务器，监听 ws-port，仅处理 WebSocket 认证请求（验证码认证）。纯 Java，不依赖 Bukkit。
 */
public final class SimpleComWsServer {

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int PING_INTERVAL_SEC = 6;

    private final ProxyConfig config;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SimpleCom-WS");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SimpleCom-WSPing");
        t.setDaemon(true);
        return t;
    });

    public SimpleComWsServer(ProxyConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        if (running) return;
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(loopback, config.getWsPort()), 50);
        running = true;
        executor.execute(this::acceptLoop);
        System.out.println("[SimpleCom] WebSocket 服务器已启动（内部）| WebSocket server started (internal): 127.0.0.1:" + config.getWsPort());
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        executor.shutdown();
        pingScheduler.shutdown();
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(10000);
                executor.execute(() -> handle(client));
            } catch (SocketException e) {
                if (running) System.err.println("[SimpleCom] WS accept 异常 | WS accept error: " + e.getMessage());
            } catch (IOException e) {
                if (running) System.err.println("[SimpleCom] WS accept 异常 | WS accept error: " + e.getMessage());
            }
        }
    }

    private void handle(Socket client) {
        String clientIp = client.getInetAddress().getHostAddress();
        try {
            System.out.println("[SimpleCom] WebSocket 连接接入，来自 | WS connection from: " + clientIp);
            handleWebSocket(client, client.getInputStream());
        } catch (Exception e) {
            System.err.println("[SimpleCom] WS 连接处理异常 | WS connection error from " + clientIp
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            closeQuietly(client);
        }
    }

    private void handleWebSocket(Socket client, InputStream wsStream) {
        String socketIp = client.getInetAddress().getHostAddress();
        try {
            StringBuilder firstLine = new StringBuilder();
            byte[] oneByte = new byte[1];
            while (true) {
                int r = wsStream.read(oneByte);
                if (r < 0) {
                    System.err.println("[SimpleCom] WS 握手时连接断开 | WS closed during handshake from: " + socketIp);
                    return;
                }
                if (oneByte[0] == '\n') break;
                firstLine.append((char) oneByte[0]);
            }
            String firstLineStr = firstLine.toString().trim();
            String clientIp = socketIp;
            String reqStr;
            if (firstLineStr.startsWith("X-Real-IP:")) {
                clientIp = firstLineStr.substring(10).trim();
                if (clientIp.isEmpty()) clientIp = socketIp;
                StringBuilder req = new StringBuilder();
                int prev = -1, prev2 = -1, prev3 = -1;
                while (true) {
                    int rr = wsStream.read(oneByte);
                    if (rr < 0) break;
                    req.append((char) oneByte[0]);
                    if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && oneByte[0] == '\n') break;
                    prev3 = prev2; prev2 = prev; prev = oneByte[0];
                }
                reqStr = req.toString();
            } else {
                StringBuilder req = new StringBuilder(firstLineStr).append('\n');
                int prev = -1, prev2 = -1, prev3 = -1;
                while (true) {
                    int rr = wsStream.read(oneByte);
                    if (rr < 0) break;
                    req.append((char) oneByte[0]);
                    if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && oneByte[0] == '\n') break;
                    prev3 = prev2; prev2 = prev; prev = oneByte[0];
                }
                reqStr = req.toString();
            }

            String key = extractWsKey(reqStr);
            if (key == null) {
                System.err.println("[SimpleCom] WS 握手失败：缺少 Sec-WebSocket-Key | WS handshake failed: missing key from " + clientIp);
                System.err.println("[SimpleCom] 请求头 | Headers: " + reqStr.replace("\r\n", " | "));
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
            System.out.println("[SimpleCom] WS 握手成功，进入帧循环 | WS handshake OK, entering frame loop: " + clientIp);
            wsFrameLoop(client, wsStream, out, clientIp);
        } catch (Exception e) {
            System.err.println("[SimpleCom] WS 处理异常 | WS error from " + socketIp
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            closeQuietly(client);
        }
    }

    private String wsFrameLoop(Socket client, InputStream in, OutputStream out, String wsClientIp) throws IOException {
        byte[] buf = new byte[65536];
        int[] payloadLen = new int[1];
        boolean authenticated = false;
        String authenticatedUser = null;
        ScheduledFuture<?> pingTask = null;
        WsSender sender = null;

        try {
            while (running && !client.isClosed()) {
                int opcode = readWsFrame(in, buf, payloadLen);
                if (opcode < 0) {
                    System.out.println("[SimpleCom] WS 连接断开（EOF）| WS connection closed (EOF): " + wsClientIp);
                    break;
                }
                if (opcode == 8) {
                    System.out.println("[SimpleCom] WS 连接断开（客户端关闭）| WS connection closed (client): " + wsClientIp);
                    synchronized (out) { sendWsClose(out, 1000, "Normal closure"); }
                    break;
                }
                if (opcode == 9) {
                    synchronized (out) { writeWsFrame(out, 10, buf, payloadLen[0]); }
                } else if (opcode == 2 && authenticated) {
                    // 二进制帧：语音数据包，转发给同信道其他已认证客户端
                    int dataLen = payloadLen[0];
                    byte[] voiceData = new byte[dataLen];
                    System.arraycopy(buf, 0, voiceData, 0, dataLen);
                    forwardVoicePacket(authenticatedUser, voiceData);
                } else if (opcode == 1 && authenticated) {
                    // 已认证用户的文本消息（如信道切换、加密信道请求）
                    String text = new String(buf, 0, payloadLen[0], StandardCharsets.UTF_8);
                    if (text.contains("\"channel\"")) {
                        handleChannelSwitch(authenticatedUser, text, out);
                    } else if (text.contains("\"requestencryptedchannellist\"")) {
                        handleEncryptedChannelListRequest(out);
                    } else if (text.contains("\"connectencryptedchannel\"")) {
                        handleConnectEncryptedChannel(authenticatedUser, text, out);
                    } else if (text.contains("\"createencryptedchannel\"")) {
                        handleCreateEncryptedChannel(authenticatedUser, text, out);
                    }
                } else if (opcode == 1 && !authenticated) {
                    String text = new String(buf, 0, payloadLen[0], StandardCharsets.UTF_8);
                    String username = SimpleComWsHandler.parseUsername(text);
                    String code     = SimpleComWsHandler.parseCode(text);
                    if (username == null) {
                        System.err.println("[SimpleCom] WS 收到无效消息 | WS invalid message from " + wsClientIp + ": " + text);
                        synchronized (out) { sendWsClose(out, 4000, "Invalid message | 消息格式无效"); }
                        break;
                    }
                    WsAuthResult result = SimpleComWsHandler.authenticate(username, code);
                    if (result == WsAuthResult.OK) {
                        authenticated = true;
                        authenticatedUser = username.toLowerCase();
                        sender = new WsSender(client, out);
                        WsConnectionRegistry.getInstance().register(authenticatedUser, sender);
                        byte[] ok = ("{\"status\":\"ok\",\"username\":\"" + escapeJson(username) + "\"}")
                                .getBytes(StandardCharsets.UTF_8);
                        synchronized (out) { writeWsFrame(out, 1, ok, ok.length); }
                        System.out.println("[SimpleCom] WS 认证成功 | WS auth OK: " + username + " (" + wsClientIp + ")");
                        
                        // 发送服务器状态信息
                        String serverStatus = buildServerStatusJson();
                        System.out.println("[SimpleCom] 发送服务器状态: " + serverStatus);
                        byte[] statusBytes = serverStatus.getBytes(StandardCharsets.UTF_8);
                        synchronized (out) { writeWsFrame(out, 1, statusBytes, statusBytes.length); }
                        
                        // 认证成功后立即发送一次用户列表
                        String initialUserList = buildOnlineUsersList(authenticatedUser);
                        System.out.println("[SimpleCom] 发送初始用户列表: " + initialUserList);
                        byte[] initialListBytes = initialUserList.getBytes(StandardCharsets.UTF_8);
                        synchronized (out) { writeWsFrame(out, 1, initialListBytes, initialListBytes.length); }
                        
                        if (pingTask == null) {
                            OutputStream outRef = out;
                            Socket clientRef = client;
                            final String userRef = authenticatedUser;
                            pingTask = pingScheduler.scheduleAtFixedRate(() -> {
                                try {
                                    // 发送普通 Ping 保持连接
                                    synchronized (outRef) { writeWsFrame(outRef, 9, new byte[0], 0); }
                                    // 推送同信道用户列表（文本消息）
                                    String userList = buildOnlineUsersList(userRef);
                                    byte[] listBytes = userList.getBytes(StandardCharsets.UTF_8);
                                    synchronized (outRef) { writeWsFrame(outRef, 1, listBytes, listBytes.length); }
                                } catch (IOException e) {
                                    // 发送失败说明连接已断，主动关闭 socket 让帧循环退出
                                    closeQuietly(clientRef);
                                }
                            }, PING_INTERVAL_SEC, PING_INTERVAL_SEC, TimeUnit.SECONDS);
                        }
                    } else if (result == WsAuthResult.NOT_FOUND) {
                        System.out.println("[SimpleCom] WS 认证失败（验证码不存在或已过期）| WS auth failed (code not found/expired): " + username);
                        synchronized (out) { sendWsClose(out, 4004, "Code not found or expired | 验证码不存在或已过期"); }
                        break;
                    } else {
                        System.out.println("[SimpleCom] WS 认证失败（验证码错误）| WS auth failed (wrong code): " + username);
                        synchronized (out) { sendWsClose(out, 4003, "Wrong code | 验证码错误"); }
                        break;
                    }
                }
            }
        } finally {
            if (pingTask != null) pingTask.cancel(false);
            if (authenticatedUser != null) {
                int userChannel = WsConnectionRegistry.getInstance().getChannel(authenticatedUser);
                WsConnectionRegistry.getInstance().unregister(authenticatedUser);
                System.out.println("[SimpleCom] WS 连接注销 | WS connection unregistered: " + authenticatedUser);
                
                // 广播更新该信道的用户列表
                broadcastUserListToChannel(userChannel);
                
                // 如果是加密信道（>100），检查是否需要清理
                if (userChannel > 100) {
                    EncryptedChannelManager.getInstance().checkAndCleanup(userChannel);
                }
            }
        }
        return authenticatedUser;
    }

    private static String extractWsKey(String request) {
        String prefix = "Sec-WebSocket-Key:";
        int i = request.indexOf(prefix);
        if (i < 0) return null;
        i += prefix.length();
        int j = request.indexOf("\r\n", i);
        if (j < 0) j = request.length();
        return request.substring(i, j).trim();
    }

    private static String computeWsAccept(String key) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update((key + WS_MAGIC).getBytes(StandardCharsets.ISO_8859_1));
        return Base64.getEncoder().encodeToString(sha1.digest());
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

    /**
     * 构造用户列表 JSON 消息：{"type":"userlist","users":["user1","user2"]}
     * 只包含与指定用户同信道的用户。
     * Build user list JSON message, only for users in the same channel.
     */
    private static String buildOnlineUsersList(String forUser) {
        WsConnectionRegistry registry = WsConnectionRegistry.getInstance();
        int channel = registry.getChannel(forUser);
        java.util.List<String> usersInChannel = registry.getUsersInChannel(channel);
        StringBuilder sb = new StringBuilder("{\"type\":\"userlist\",\"users\":[");
        boolean first = true;
        for (String user : usersInChannel) {
            WsSender s = registry.getSender(user);
            if (s != null && s.isOpen()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(user)).append("\"");
                first = false;
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 向同信道所有用户推送用户列表更新。
     * Push user list update to all users in the same channel.
     */
    private static void broadcastUserListToChannel(int channel) {
        WsConnectionRegistry registry = WsConnectionRegistry.getInstance();
        java.util.List<String> usersInChannel = registry.getUsersInChannel(channel);
        for (String user : usersInChannel) {
            WsSender s = registry.getSender(user);
            if (s != null && s.isOpen()) {
                try {
                    String userList = buildOnlineUsersList(user);
                    s.sendText(userList);
                } catch (IOException e) {
                    System.err.println("[SimpleCom] 广播用户列表失败: " + user + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 构造服务器状态 JSON 消息：{"type":"serverstatus","compressionEncoder":true,"lowLatency":false}
     * Build server status JSON message.
     */
    private String buildServerStatusJson() {
        return "{\"type\":\"serverstatus\",\"compressionEncoder\":"
                + config.isCompressionEncoder()
                + ",\"lowLatency\":"
                + config.isLowLatency()
                + "}";
    }

    /**
     * 处理信道切换消息：{"type":"channel","channel":1}
     * Handle channel switch message.
     */
    private void handleChannelSwitch(String username, String text, OutputStream out) {
        try {
            // 解析 channel 字段
            int start = text.indexOf("\"channel\":") + 10;
            int end = text.indexOf("}", start);
            if (end == -1) end = text.length();
            // 去掉可能的逗号
            String channelStr = text.substring(start, end).replaceAll("[^0-9]", "").trim();
            int channel = Integer.parseInt(channelStr);
            
            int oldChannel = WsConnectionRegistry.getInstance().getChannel(username);
            WsConnectionRegistry.getInstance().setChannel(username, channel);
            
            System.out.println("[SimpleCom] 用户 " + username + " 切换信道: " + oldChannel + " -> " + channel);
            
            // 回复确认
            String ack = "{\"type\":\"channelok\",\"channel\":" + channel + "}";
            byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
            synchronized (out) { writeWsFrame(out, 1, ackBytes, ackBytes.length); }
            
            // 发送该用户当前信道的用户列表
            String userList = buildOnlineUsersList(username);
            byte[] listBytes = userList.getBytes(StandardCharsets.UTF_8);
            synchronized (out) { writeWsFrame(out, 1, listBytes, listBytes.length); }
            
            // 广播更新旧信道和新信道的用户列表
            if (oldChannel != channel) {
                broadcastUserListToChannel(oldChannel);
                broadcastUserListToChannel(channel);
                
                // 如果离开的是加密信道（>100），检查是否需要清理
                if (oldChannel > 100) {
                    EncryptedChannelManager.getInstance().checkAndCleanup(oldChannel);
                }
            }
        } catch (Exception e) {
            System.err.println("[SimpleCom] 处理信道切换失败: " + e.getMessage());
        }
    }

    /**
     * 处理加密信道列表请求：{"type":"requestencryptedchannellist"}
     * Handle encrypted channel list request.
     */
    private void handleEncryptedChannelListRequest(OutputStream out) {
        try {
            String[] channelNames = EncryptedChannelManager.getInstance().getAllChannelNames();
            StringBuilder sb = new StringBuilder("{\"type\":\"encryptedchannellist\",\"channels\":[");
            for (int i = 0; i < channelNames.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(channelNames[i])).append("\"");
            }
            sb.append("]}");
            
            byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
            synchronized (out) { writeWsFrame(out, 1, response, response.length); }
            System.out.println("[SimpleCom] 发送加密信道列表: " + channelNames.length + " 个信道");
        } catch (Exception e) {
            System.err.println("[SimpleCom] 处理加密信道列表请求失败: " + e.getMessage());
        }
    }

    /**
     * 处理连接加密信道请求：{"type":"connectencryptedchannel","name":"xxx","passwordHash":"xxx"}
     * Handle connect encrypted channel request.
     */
    private void handleConnectEncryptedChannel(String username, String text, OutputStream out) {
        try {
            String name = parseStringField(text, "name");
            String passwordHash = parseStringField(text, "passwordHash");
            
            if (name == null || passwordHash == null) {
                sendEncryptedChannelConnectResult(out, false, -1);
                return;
            }
            
            int tempChannel = EncryptedChannelManager.getInstance().verifyAndGetChannel(name, passwordHash);
            
            if (tempChannel > 0) {
                // 验证成功，切换到临时信道
                int oldChannel = WsConnectionRegistry.getInstance().getChannel(username);
                WsConnectionRegistry.getInstance().setChannel(username, tempChannel);
                
                System.out.println("[SimpleCom] 用户 " + username + " 连接加密信道: " + name + " (临时信道: " + tempChannel + ")");
                
                sendEncryptedChannelConnectResult(out, true, tempChannel);
                
                // 发送信道确认
                String ack = "{\"type\":\"channelok\",\"channel\":" + tempChannel + "}";
                byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
                synchronized (out) { writeWsFrame(out, 1, ackBytes, ackBytes.length); }
                
                // 发送该用户当前信道的用户列表
                String userList = buildOnlineUsersList(username);
                byte[] listBytes = userList.getBytes(StandardCharsets.UTF_8);
                synchronized (out) { writeWsFrame(out, 1, listBytes, listBytes.length); }
                
                // 广播更新旧信道和新信道的用户列表
                if (oldChannel != tempChannel) {
                    broadcastUserListToChannel(oldChannel);
                    broadcastUserListToChannel(tempChannel);
                    
                    // 如果离开的是加密信道（>100），检查是否需要清理
                    if (oldChannel > 100) {
                        EncryptedChannelManager.getInstance().checkAndCleanup(oldChannel);
                    }
                }
            } else {
                // 验证失败
                sendEncryptedChannelConnectResult(out, false, -1);
            }
        } catch (Exception e) {
            System.err.println("[SimpleCom] 处理连接加密信道请求失败: " + e.getMessage());
            try {
                sendEncryptedChannelConnectResult(out, false, -1);
            } catch (IOException ignored) {}
        }
    }

    /**
     * 处理创建加密信道请求：{"type":"createencryptedchannel","name":"xxx","passwordHash":"xxx"}
     * Handle create encrypted channel request.
     */
    private void handleCreateEncryptedChannel(String username, String text, OutputStream out) {
        try {
            String name = parseStringField(text, "name");
            String passwordHash = parseStringField(text, "passwordHash");
            
            if (name == null || passwordHash == null) {
                sendEncryptedChannelCreateResult(out, false, -1);
                return;
            }
            
            int tempChannel = EncryptedChannelManager.getInstance().createChannel(name, passwordHash);
            
            if (tempChannel > 0) {
                // 创建成功，切换到临时信道
                int oldChannel = WsConnectionRegistry.getInstance().getChannel(username);
                WsConnectionRegistry.getInstance().setChannel(username, tempChannel);
                
                System.out.println("[SimpleCom] 用户 " + username + " 创建加密信道: " + name + " (临时信道: " + tempChannel + ")");
                
                sendEncryptedChannelCreateResult(out, true, tempChannel);
                
                // 发送信道确认
                String ack = "{\"type\":\"channelok\",\"channel\":" + tempChannel + "}";
                byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
                synchronized (out) { writeWsFrame(out, 1, ackBytes, ackBytes.length); }
                
                // 发送该用户当前信道的用户列表
                String userList = buildOnlineUsersList(username);
                byte[] listBytes = userList.getBytes(StandardCharsets.UTF_8);
                synchronized (out) { writeWsFrame(out, 1, listBytes, listBytes.length); }
                
                // 广播更新旧信道和新信道的用户列表
                if (oldChannel != tempChannel) {
                    broadcastUserListToChannel(oldChannel);
                    broadcastUserListToChannel(tempChannel);
                    
                    // 如果离开的是加密信道（>100），检查是否需要清理
                    if (oldChannel > 100) {
                        EncryptedChannelManager.getInstance().checkAndCleanup(oldChannel);
                    }
                }
            } else {
                // 创建失败（可能已存在）
                sendEncryptedChannelCreateResult(out, false, -1);
            }
        } catch (Exception e) {
            System.err.println("[SimpleCom] 处理创建加密信道请求失败: " + e.getMessage());
            try {
                sendEncryptedChannelCreateResult(out, false, -1);
            } catch (IOException ignored) {}
        }
    }

    /**
     * 发送加密信道连接结果。
     * Send encrypted channel connect result.
     */
    private void sendEncryptedChannelConnectResult(OutputStream out, boolean success, int channel) throws IOException {
        String response = "{\"type\":\"encryptedchannelconnect\",\"success\":" + success + ",\"channel\":" + channel + "}";
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        synchronized (out) { writeWsFrame(out, 1, responseBytes, responseBytes.length); }
    }

    /**
     * 发送加密信道创建结果。
     * Send encrypted channel create result.
     */
    private void sendEncryptedChannelCreateResult(OutputStream out, boolean success, int channel) throws IOException {
        String response = "{\"type\":\"encryptedchannelcreate\",\"success\":" + success + ",\"channel\":" + channel + "}";
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        synchronized (out) { writeWsFrame(out, 1, responseBytes, responseBytes.length); }
    }

    /**
     * 解析 JSON 字符串字段（轻量级解析）。
     * Parse JSON string field (lightweight parsing).
     */
    private static String parseStringField(String text, String fieldName) {
        if (text == null) return null;
        String key = "\"" + fieldName + "\"";
        int i = text.indexOf(key);
        if (i < 0) return null;
        i = text.indexOf(':', i + key.length());
        if (i < 0) return null;
        i = text.indexOf('"', i);
        if (i < 0) return null;
        int j = text.indexOf('"', i + 1);
        if (j < 0) return null;
        String val = text.substring(i + 1, j).trim();
        return val.isEmpty() ? null : val;
    }

    /**
     * 解析语音数据包头部，获取发言者用户名、序号和总包数，然后转发给同信道其他已认证客户端。
     * Parse voice packet header and forward to other clients in the same channel.
     *
     * @param senderUser 发送者用户名（小写）
     * @param data       完整的语音数据包字节
     */
    private void forwardVoicePacket(String senderUser, byte[] data) {
        // 解析包头以获取序号信息用于日志
        String voiceUsername = senderUser;
        int seq = 0, total = 0;
        try {
            ByteBuffer header = ByteBuffer.wrap(data);
            int usernameLen = header.getInt();
            if (usernameLen > 0 && usernameLen <= 256 && data.length >= 4 + usernameLen + 8) {
                byte[] usernameBytes = new byte[usernameLen];
                header.get(usernameBytes);
                voiceUsername = new String(usernameBytes, StandardCharsets.UTF_8);
                seq = header.getInt();
                total = header.getInt();
            }
        } catch (Exception ignored) {}

        // 获取所有已认证连接，转发给同信道除发送者以外的所有人
        WsConnectionRegistry registry = WsConnectionRegistry.getInstance();
        int senderChannel = registry.getChannel(senderUser);
        
        // 信道 0 不转发
        if (senderChannel == 0) return;

        // WSAPI：转发所有普通信道（排除加密信道 > 100）
        if (senderChannel <= 100) {
            for (java.util.Map.Entry<java.util.UUID, WsSender> entry :
                    VoiceApiConnectionRegistry.getInstance().getAllSenders().entrySet()) {
                WsSender api = entry.getValue();
                if (api != null && api.isOpen()) {
                    try {
                        api.sendBinary(data);
                    } catch (IOException e) {
                        System.err.println("[SimpleCom] 转发语音包到 WSAPI 失败 | Failed to forward voice to WSAPI: " + e.getMessage());
                    }
                }
            }
        }
        
        Map<String, com.xiaofan.ws.WsSender> allSenders =
                WsConnectionRegistry.getInstance().getAllSenders();
        int forwarded = 0;
        for (Map.Entry<String, com.xiaofan.ws.WsSender> entry : allSenders.entrySet()) {
            if (entry.getKey().equals(senderUser)) continue;
            // 只转发给同信道用户
            if (registry.getChannel(entry.getKey()) != senderChannel) continue;
            com.xiaofan.ws.WsSender target = entry.getValue();
            if (target != null && target.isOpen()) {
                try {
                    target.sendBinary(data);
                    forwarded++;
                } catch (IOException e) {
                    System.err.println("[SimpleCom] 转发语音包失败 | Failed to forward voice to "
                            + entry.getKey() + ": " + e.getMessage());
                }
            }
        }

        if (seq > 0 && total > 0) {
            System.out.println("[SimpleCom] 收到来自 " + voiceUsername + " 的语音数据（"
                    + seq + "/" + total + "），正在向 " + forwarded + " 个客户端转发");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
