package com.xiaofan.ws;

import com.xiaofan.gui.ConnectedWindow;
import com.xiaofan.gui.VoiceRecorder;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * SimpleCom WebSocket 客户端。
 * 连接到代理的 listen-port，发送 MC 用户名 + 验证码，等待服务端验证结果。
 *
 * SimpleCom WebSocket client.
 * Connects to the proxy listen-port, sends MC username + verification code, waits for auth result.
 *
 * 服务端关闭码 | Server close codes:
 *   4004 - 验证码不存在或已过期（玩家未进入MC，或超时）| Code not found / expired
 *   4003 - 验证码错误 | Wrong verification code
 */
public final class SimpleComWsClient extends WebSocketClient {

    /** 接收语音包时，每个发言者的分包缓存 */
    private static final class VoiceAssembler {
        final byte[][] chunks;
        int received = 0;

        VoiceAssembler(int total) {
            this.chunks = new byte[total][];
        }

        boolean put(int seq, byte[] pcm) {
            int idx = seq - 1;
            if (idx < 0 || idx >= chunks.length || chunks[idx] != null) return false;
            chunks[idx] = pcm;
            received++;
            return received == chunks.length;
        }

        byte[] assemble() {
            int total = 0;
            for (byte[] c : chunks) if (c != null) total += c.length;
            byte[] result = new byte[total];
            int off = 0;
            for (byte[] c : chunks) {
                if (c != null) {
                    System.arraycopy(c, 0, result, off, c.length);
                    off += c.length;
                }
            }
            return result;
        }
    }

    /** 录音格式（与 VoiceRecorder 保持一致）：48000Hz, 16-bit, 单声道, 小端, 有符号 */
    private static final AudioFormat VOICE_FORMAT =
            new AudioFormat(48000f, 16, 1, true, false);

    private final String username;
    private final String code;
    /** 验证成功回调（在 WS 线程调用，需 Platform.runLater 切到 FX 线程）*/
    private final Runnable onSuccess;
    /** 验证失败回调：(closeCode, reason) — code=-1 表示网络异常 */
    private final BiConsumer<Integer, String> onFailure;
    /** 认证成功后连接断开回调（服务端主动断开、网络异常等）| Called when connection drops after auth */
    private final Runnable onDisconnected;

    private volatile boolean authenticated;
    private volatile boolean disconnectFired;

    /** 每个发言者的分包缓存：用户名小写 → VoiceAssembler */
    private final ConcurrentHashMap<String, VoiceAssembler> assemblers = new ConcurrentHashMap<>();

    /** 屏蔽的用户集合：用户名小写 */
    private final java.util.Set<String> blockedUsers = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    /** 被屏蔽用户尝试说话回调：参数为用户名（用于 UI 显示橙色提示）*/
    private volatile Consumer<String> onBlockedUserSpeaking;

    // ─────────────────────── 延迟检测 ────────────────────────────────────
    /** 上次发出 Ping 的时间戳（ms），-1 表示尚未发出 */
    private final AtomicLong pingSentAt = new AtomicLong(-1);
    /** 最近一次测得的 RTT（ms），-1 表示未知 */
    private volatile long latencyMs = -1;
    /** 延迟更新回调（在 WS 线程调用，需 Platform.runLater 切到 FX 线程） */
    private volatile Consumer<Long> onLatencyUpdate;
    /** 在线用户列表更新回调：参数为用户名数组（在 WS 线程调用） */
    private volatile Consumer<String[]> onOnlineUsersUpdate;
    /** 语音播放开始回调：参数为正在说话的用户名（在播放线程调用） */
    private volatile Consumer<String> onVoiceSpeaking;
    /** 服务器状态回调：参数为 [compressionEncoder, lowLatency] */
    private volatile Consumer<boolean[]> onServerStatus;
    
    /** 信道确认回调：参数为信道号 */
    private volatile Consumer<Integer> onChannelConfirmed;
    
    /** 加密信道列表回调：参数为信道名称数组 */
    private volatile Consumer<String[]> onEncryptedChannelList;
    
    /** 加密信道连接结果回调：参数为 (成功, 临时信道号) */
    private volatile BiConsumer<Boolean, Integer> onEncryptedChannelConnectResult;
    
    /** 加密信道创建结果回调：参数为 (成功, 临时信道号) */
    private volatile BiConsumer<Boolean, Integer> onEncryptedChannelCreateResult;
    
    /** 当前信道（0=静音，1-100=正常信道）*/
    private volatile int currentChannel = 1;
    
    /** 缓存的服务器状态（用于 UI 创建时应用）*/
    private volatile boolean[] cachedServerStatus = null;
    
    /** 是否启用压缩编码器（从服务器状态获取）*/
    private volatile boolean compressionEnabled = false;
    
    /** 是否启用低延迟模式（从服务器状态获取）*/
    private volatile boolean lowLatencyEnabled = false;
    
    /** 低延迟模式的每个玩家的播放器（用户名小写 → PlayerVoicePlayer）*/
    private final ConcurrentHashMap<String, PlayerVoicePlayer> playerVoicePlayers = new ConcurrentHashMap<>();
    
    /** 每个玩家的语音播放器（低延迟模式）*/
    private static final class PlayerVoicePlayer {
        final String username;
        final java.util.concurrent.BlockingQueue<byte[]> queue = new java.util.concurrent.LinkedBlockingQueue<>();
        volatile SourceDataLine line = null;
        volatile Thread playThread = null;
        
        PlayerVoicePlayer(String username) {
            this.username = username;
        }
        
        synchronized void start() {
            if (playThread != null && playThread.isAlive()) return;
            
            playThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] pcm = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (pcm != null) {
                            playChunk(pcm);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    cleanup();
                }
            }, "SimpleCom-VoicePlay-" + username);
            playThread.setDaemon(true);
            playThread.start();
        }
        
        private void playChunk(byte[] pcm) {
            try {
                if (line == null || !line.isOpen()) {
                    line = AudioSystem.getSourceDataLine(VOICE_FORMAT);
                    int bufferSize = 20480;
                    line.open(VOICE_FORMAT, bufferSize);
                    line.start();
                    System.out.println("[SimpleCom] 玩家 " + username + " 的播放器已启动");
                }
                line.write(pcm, 0, pcm.length);
            } catch (LineUnavailableException e) {
                System.err.println("[SimpleCom] 玩家 " + username + " 播放失败: " + e.getMessage());
                cleanup();
            }
        }
        
        synchronized void stop() {
            if (playThread != null) {
                playThread.interrupt();
                playThread = null;
            }
            cleanup();
        }
        
        private void cleanup() {
            if (line != null && line.isOpen()) {
                try {
                    line.drain();
                    line.stop();
                    line.close();
                } catch (Exception ignored) {}
                line = null;
            }
            queue.clear();
        }
    }
    
    /** Opus 编码器（启用压缩时使用）*/
    private volatile io.github.jaredmdobson.concentus.OpusEncoder opusEncoder = null;
    
    /** Opus 解码器（启用压缩时使用）*/
    private volatile io.github.jaredmdobson.concentus.OpusDecoder opusDecoder = null;
    /** 客户端主动发 Ping 的定时任务 */
    private ScheduledFuture<?> pingTask;
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SimpleCom-LatencyPing");
        t.setDaemon(true);
        return t;
    });

    /** 缓存的用户列表 JSON（用于对比，避免重复更新 UI）*/
    private volatile String cachedUserListJson = "";

    /**
     * @param serverUri      ws://host:port/
     * @param username       MC 玩家用户名
     * @param code           玩家在 MC 聊天框中收到的 6 位验证码
     * @param onSuccess      验证成功时调用（WS 线程）
     * @param onFailure      验证失败或连接异常时调用（WS 线程）；closeCode: 4003/4004 或 -1（异常）
     * @param onDisconnected 认证成功后连接断开时调用（WS 线程）；可为 null
     */
    public SimpleComWsClient(URI serverUri, String username, String code,
                              Runnable onSuccess,
                              BiConsumer<Integer, String> onFailure,
                              Runnable onDisconnected) {
        super(serverUri);
        this.username = username;
        this.code = code != null ? code : "";
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
        this.onDisconnected = onDisconnected;
    }

    /**
     * 设置延迟更新回调（认证成功后调用，每次 Pong 返回时触发）。
     * Set latency update callback (called after auth, triggered on each Pong).
     *
     * @param callback 参数为 RTT 毫秒数
     */
    public void setOnLatencyUpdate(Consumer<Long> callback) {
        this.onLatencyUpdate = callback;
    }

    /**
     * 设置在线用户列表更新回调（每次收到 Pong 时触发）。
     * Set online users list update callback (triggered on each Pong).
     *
     * @param callback 参数为用户名数组（可能为空数组）
     */
    public void setOnOnlineUsersUpdate(Consumer<String[]> callback) {
        this.onOnlineUsersUpdate = callback;
    }

    /**
     * 设置语音播放开始回调（某用户的完整语音开始播放时触发）。
     * Set voice speaking callback (triggered when a user's voice starts playing).
     *
     * @param callback 参数为正在说话的用户名（原始大小写，来自数据包头）
     */
    public void setOnVoiceSpeaking(Consumer<String> callback) {
        this.onVoiceSpeaking = callback;
    }

    /**
     * 设置被屏蔽用户尝试说话回调（收到被屏蔽用户的语音包时触发，但不播放）。
     * Set blocked user speaking callback (triggered when receiving voice from blocked user, but not played).
     *
     * @param callback 参数为被屏蔽用户的用户名
     */
    public void setOnBlockedUserSpeaking(Consumer<String> callback) {
        this.onBlockedUserSpeaking = callback;
    }

    /**
     * 设置服务器状态回调（认证成功后收到服务器状态时触发）。
     * Set server status callback (triggered when receiving server status after auth).
     *
     * @param callback 参数为 [compressionEncoder, lowLatency]
     */
    public void setOnServerStatus(Consumer<boolean[]> callback) {
        this.onServerStatus = callback;
        // 如果已经收到过服务器状态，立即触发回调
        if (cachedServerStatus != null && callback != null) {
            callback.accept(cachedServerStatus);
        }
    }

    /**
     * 获取缓存的服务器状态（如果还没收到则返回 null）。
     * Get cached server status (returns null if not received yet).
     *
     * @return [compressionEncoder, lowLatency] 或 null
     */
    public boolean[] getCachedServerStatus() {
        return cachedServerStatus;
    }

    /**
     * 设置信道确认回调（服务器确认信道切换时触发）。
     * Set channel confirmed callback (triggered when server confirms channel switch).
     *
     * @param callback 参数为确认的信道号
     */
    public void setOnChannelConfirmed(Consumer<Integer> callback) {
        this.onChannelConfirmed = callback;
    }

    /**
     * 获取当前信道。
     * Get current channel.
     *
     * @return 当前信道（0=静音，1-100=正常信道）
     */
    public int getCurrentChannel() {
        return currentChannel;
    }

    /**
     * 切换信道并通知服务器。
     * Switch channel and notify server.
     *
     * @param channel 新信道（0=静音，1-100=正常信道，>100=加密信道临时信道）
     */
    public void switchChannel(int channel) {
        if (channel < 0) {
            System.err.println("[SimpleCom] 无效的信道号: " + channel);
            return;
        }
        
        // 发送信道更新到服务器（不立即更新本地 currentChannel，等待服务端确认）
        if (authenticated && isOpen()) {
            String json = "{\"type\":\"channel\",\"channel\":" + channel + "}";
            send(json);
            System.out.println("[SimpleCom] 请求切换到信道: " + channel);
        }
    }

    /**
     * 切换用户的屏蔽状态。
     * Toggle block status for a user.
     *
     * @param username 用户名（大小写不敏感）
     * @return true=已屏蔽，false=未屏蔽
     */
    public boolean toggleBlockUser(String username) {
        if (username == null || username.isEmpty()) return false;
        String key = username.toLowerCase();
        if (blockedUsers.contains(key)) {
            blockedUsers.remove(key);
            return false;
        } else {
            blockedUsers.add(key);
            return true;
        }
    }

    /**
     * 检查用户是否被屏蔽。
     * Check if a user is blocked.
     *
     * @param username 用户名（大小写不敏感）
     * @return true=已屏蔽，false=未屏蔽
     */
    public boolean isUserBlocked(String username) {
        if (username == null || username.isEmpty()) return false;
        return blockedUsers.contains(username.toLowerCase());
    }

    /** 获取最近测得的 RTT（ms），-1 表示未知 */
    public long getLatencyMs() {
        return latencyMs;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        // 连接建立后立即发送用户名 + 验证码 JSON
        // Send username + verification code JSON immediately after connection
        String msg = "{\"username\":\"" + escapeJson(username)
                   + "\",\"code\":\"" + escapeJson(code) + "\"}";
        send(msg);
    }

    @Override
    public void onMessage(String message) {
        if (message == null) return;
        
        // 服务端回 {"status":"ok",...} 则认证成功 | Auth success when server sends {"status":"ok",...}
        if (message.contains("\"ok\"")) {
            authenticated = true;
            if (onSuccess != null) onSuccess.run();
            
            // 认证成功后立即上报当前信道
            switchChannel(currentChannel);
            
            // 认证成功后启动客户端主动 Ping，每 5 秒测一次延迟
            startLatencyPing();
        }
        // 处理信道确认 {"type":"channelok","channel":1}
        else if (message.contains("\"channelok\"")) {
            try {
                // 简单解析：提取 channel 值
                int start = message.indexOf("\"channel\":") + 10;
                int end = message.indexOf("}", start);
                if (end == -1) end = message.length();
                String channelStr = message.substring(start, end).trim();
                int confirmedChannel = Integer.parseInt(channelStr);
                
                System.out.println("[SimpleCom] 服务器确认信道: " + confirmedChannel);
                
                // 更新本地信道
                currentChannel = confirmedChannel;
                
                // 触发回调
                Consumer<Integer> channelCb = onChannelConfirmed;
                if (channelCb != null) {
                    channelCb.accept(confirmedChannel);
                }
            } catch (Exception e) {
                System.err.println("[SimpleCom] 解析信道确认失败: " + e.getMessage());
            }
        }
        // 处理服务器状态推送 {"type":"serverstatus","compressionEncoder":true,"lowLatency":false}
        else if (message.contains("\"serverstatus\"")) {
            System.out.println("[SimpleCom] 收到服务器状态: " + message);
            try {
                parseAndNotifyServerStatus(message);
            } catch (Exception e) {
                System.err.println("[SimpleCom] 解析服务器状态失败 | Failed to parse server status: " + e.getMessage());
            }
        }
        // 处理用户列表推送 {"type":"userlist","users":["user1","user2"]}
        else if (message.contains("\"userlist\"")) {
            System.out.println("[SimpleCom] 收到用户列表: " + message);
            try {
                parseAndNotifyUserList(message);
            } catch (Exception e) {
                System.err.println("[SimpleCom] 解析用户列表失败 | Failed to parse user list: " + e.getMessage());
            }
        }
        // 处理加密信道列表 {"type":"encryptedchannellist","channels":["channel1","channel2"]}
        else if (message.contains("\"encryptedchannellist\"")) {
            System.out.println("[SimpleCom] 收到加密信道列表: " + message);
            try {
                parseAndNotifyEncryptedChannelList(message);
            } catch (Exception e) {
                System.err.println("[SimpleCom] 解析加密信道列表失败: " + e.getMessage());
            }
        }
        // 处理加密信道连接结果 {"type":"encryptedchannelconnect","success":true,"channel":101}
        else if (message.contains("\"encryptedchannelconnect\"")) {
            System.out.println("[SimpleCom] 收到加密信道连接结果: " + message);
            try {
                parseEncryptedChannelConnectResult(message);
            } catch (Exception e) {
                System.err.println("[SimpleCom] 解析加密信道连接结果失败: " + e.getMessage());
            }
        }
        // 处理加密信道创建结果 {"type":"encryptedchannelcreate","success":true,"channel":101}
        else if (message.contains("\"encryptedchannelcreate\"")) {
            System.out.println("[SimpleCom] 收到加密信道创建结果: " + message);
            try {
                parseEncryptedChannelCreateResult(message);
            } catch (Exception e) {
                System.err.println("[SimpleCom] 解析加密信道创建结果失败: " + e.getMessage());
            }
        }
    }

    /**
     * 收到服务端 Pong 帧时计算 RTT。
     * Called when a Pong frame is received: measure RTT.
     */
    @Override
    public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
        long sent = pingSentAt.getAndSet(-1);
        if (sent >= 0) {
            long rtt = System.currentTimeMillis() - sent;
            latencyMs = rtt;
            Consumer<Long> cb = onLatencyUpdate;
            if (cb != null) cb.accept(rtt);
        }
    }

    /** 启动定时 Ping 任务（每 5 秒发一次） */
    private void startLatencyPing() {
        if (pingTask != null) return;
        pingTask = pingScheduler.scheduleAtFixedRate(() -> {
            if (isOpen()) {
                pingSentAt.set(System.currentTimeMillis());
                try {
                    sendPing();
                } catch (Exception e) {
                    pingSentAt.set(-1);
                }
            }
        }, 1, 5, TimeUnit.SECONDS);
    }

    /**
     * 接收二进制帧：服务端转发的语音数据包。
     * Receive binary frame: voice data packet forwarded by the server.
     */
    @Override
    public void onMessage(ByteBuffer bytes) {
        if (!authenticated) return;
        
        // 信道 0（静音）：不播放任何语音
        if (currentChannel == 0) {
            return;
        }
        
        try {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);

            VoiceRecorder.VoicePacket packet = VoiceRecorder.parsePacket(data);
            if (packet == null) return;

            String key = packet.username.toLowerCase();
            
            // 检查是否被屏蔽
            if (blockedUsers.contains(key)) {
                Consumer<String> blockedCb = onBlockedUserSpeaking;
                if (blockedCb != null) blockedCb.accept(packet.username);
                return; // 不播放被屏蔽用户的语音
            }
            
            // 收到任何语音包都触发说话回调（用于实时高亮）
            Consumer<String> speakingCb = onVoiceSpeaking;
            if (speakingCb != null) speakingCb.accept(packet.username);
            
            // 低延迟模式：total = -1 表示流式传输，立即播放
            if (packet.total == -1) {
                handleLowLatencyPacket(packet);
                return;
            }
            
            // 低延迟模式：total = 0 表示结束标记
            if (packet.total == 0) {
                handleLowLatencyEnd(packet);
                return;
            }
            
            // 普通模式：拼装完整数据后播放
            VoiceAssembler assembler = assemblers.computeIfAbsent(key,
                    k -> new VoiceAssembler(packet.total));

            // 总包数变化说明是新一轮语音，重置缓存
            if (assembler.chunks.length != packet.total) {
                VoiceAssembler fresh = new VoiceAssembler(packet.total);
                assemblers.put(key, fresh);
                assembler = fresh;
            }

            boolean complete = assembler.put(packet.seq, packet.pcm);
            if (complete) {
                assemblers.remove(key);
                byte[] fullData = assembler.assemble();
                
                // 如果启用压缩，先解码
                byte[] fullPcm;
                if (compressionEnabled && opusDecoder != null) {
                    try {
                        fullPcm = decodeWithOpus(fullData);
                        System.out.println("[SimpleCom] Opus 解码完成: " + fullData.length + " bytes -> " + fullPcm.length + " bytes");
                    } catch (Exception e) {
                        System.err.println("[SimpleCom] Opus 解码失败 | Opus decode failed: " + e.getMessage());
                        return; // 解码失败，不播放
                    }
                } else {
                    fullPcm = fullData;
                }
                
                playReceivedVoice(fullPcm);
            }
        } catch (Exception e) {
            System.err.println("[SimpleCom] 处理语音包异常 | Error processing voice packet: " + e.getMessage());
        }
    }

    /**
     * 处理低延迟模式的语音包（立即解码并加入播放队列）。
     * Handle low latency voice packet (decode and add to play queue immediately).
     */
    private void handleLowLatencyPacket(VoiceRecorder.VoicePacket packet) {
        try {
            // 解码（如果启用压缩）
            byte[] pcm;
            if (compressionEnabled && opusDecoder != null) {
                try {
                    pcm = decodeWithOpus(packet.pcm);
                } catch (Exception e) {
                    System.err.println("[SimpleCom] 低延迟 Opus 解码失败: " + e.getMessage());
                    return;
                }
            } else {
                pcm = packet.pcm;
            }

            // 获取或创建该玩家的播放器
            String key = packet.username.toLowerCase();
            PlayerVoicePlayer player = playerVoicePlayers.computeIfAbsent(key, k -> new PlayerVoicePlayer(packet.username));
            
            // 启动播放线程（仅第一次）
            if (packet.seq == 1) {
                player.start();
            }

            // 加入该玩家的播放队列
            player.queue.offer(pcm);
        } catch (Exception e) {
            System.err.println("[SimpleCom] 处理低延迟语音包异常: " + e.getMessage());
        }
    }

    /**
     * 处理低延迟模式的结束标记。
     * Handle low latency end marker.
     */
    private void handleLowLatencyEnd(VoiceRecorder.VoicePacket packet) {
        String key = packet.username.toLowerCase();
        PlayerVoicePlayer player = playerVoicePlayers.remove(key);
        
        if (player != null) {
            // 等待该玩家的队列播放完毕
            try {
                while (!player.queue.isEmpty()) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 停止该玩家的播放器
            player.stop();
        }
        
        // 播放尾音
        ConnectedWindow.playPttReleaseSound();
    }

    /**
     * 发送语音数据包（二进制帧）。
     * Send a voice data packet as a binary WebSocket frame.
     *
     * @param packetBytes VoiceRecorder 打包好的字节数组
     */
    public void sendVoicePacket(byte[] packetBytes) {
        if (authenticated && isOpen()) {
            send(ByteBuffer.wrap(packetBytes));
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        stopLatencyPing();
        if (authenticated) {
            // 认证成功后断开：服务端主动关闭（如玩家退出 MC）或网络异常
            // 用 disconnectFired 防止 onError + onClose 双重触发
            if (!disconnectFired) {
                disconnectFired = true;
                if (onDisconnected != null) onDisconnected.run();
            }
        } else {
            // 认证阶段失败：4003/4004 或异常
            if (code != 1000 || remote) {
                if (onFailure != null) {
                    onFailure.accept(code, reason != null ? reason : "");
                }
            }
        }
    }

    @Override
    public void onError(Exception ex) {
        stopLatencyPing();
        if (authenticated) {
            // onError 之后通常还会触发 onClose，用 disconnectFired 防止重复回调
            if (!disconnectFired) {
                disconnectFired = true;
                if (onDisconnected != null) onDisconnected.run();
            }
        } else {
            if (onFailure != null) {
                onFailure.accept(-1, ex != null ? ex.getMessage() : "Unknown error | 未知错误");
            }
        }
    }

    private void stopLatencyPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        pingScheduler.shutdown();
    }

    // ─────────────────────── 语音播放 ────────────────────────────────────

    /**
     * 播放收到的完整 PCM 语音，播放完毕后立即播放倒放 SAE.mp3 作为尾音。
     * Play the received full PCM voice, then immediately play reversed SAE.mp3 as a tail sound.
     */
    private static void playReceivedVoice(byte[] pcm) {
        Thread t = new Thread(() -> {
            try {
                SourceDataLine line = AudioSystem.getSourceDataLine(VOICE_FORMAT);
                line.open(VOICE_FORMAT);
                line.start();
                int off = 0;
                while (off < pcm.length && !Thread.currentThread().isInterrupted()) {
                    int chunk = Math.min(4096, pcm.length - off);
                    line.write(pcm, off, chunk);
                    off += chunk;
                }
                if (!Thread.currentThread().isInterrupted()) line.drain();
                line.stop();
                line.close();
            } catch (LineUnavailableException e) {
                System.err.println("[SimpleCom] 播放语音失败 | Voice playback failed: " + e.getMessage());
                return;
            }
            // 播放完毕后立即播放倒放 SAE.mp3 作为尾音
            ConnectedWindow.playPttReleaseSound();
        }, "SimpleCom-VoicePlay");
        t.setDaemon(true);
        t.start();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 解析用户列表 JSON 并触发回调（仅当列表变化时）。
     * Parse user list JSON and trigger callback (only when list changes).
     * 
     * 格式：{"type":"userlist","users":["user1","user2"]}
     */
    private void parseAndNotifyUserList(String json) {
        Consumer<String[]> usersCb = this.onOnlineUsersUpdate;
        if (usersCb == null) return;

        // 缓存对比：只有列表变化时才更新
        if (json.equals(cachedUserListJson)) {
            System.out.println("[SimpleCom] 用户列表未变化，跳过更新");
            return; // 列表未变化，不触发回调
        }
        System.out.println("[SimpleCom] 用户列表已变化，触发更新");
        cachedUserListJson = json;

        try {
            // 简单解析：提取 "users":[...] 中的内容
            int usersIdx = json.indexOf("\"users\"");
            if (usersIdx < 0) {
                System.out.println("[SimpleCom] 解析结果: 空列表 (未找到 users 字段)");
                usersCb.accept(new String[0]);
                return;
            }
            int arrayStart = json.indexOf('[', usersIdx);
            int arrayEnd = json.indexOf(']', arrayStart);
            if (arrayStart < 0 || arrayEnd < 0) {
                System.out.println("[SimpleCom] 解析结果: 空列表 (未找到数组)");
                usersCb.accept(new String[0]);
                return;
            }
            String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
            if (arrayContent.isEmpty()) {
                System.out.println("[SimpleCom] 解析结果: 空列表 (数组为空)");
                usersCb.accept(new String[0]);
                return;
            }
            // 分割并去除引号
            String[] parts = arrayContent.split(",");
            java.util.List<String> users = new java.util.ArrayList<>();
            for (String part : parts) {
                String cleaned = part.trim().replace("\"", "");
                if (!cleaned.isEmpty()) users.add(cleaned);
            }
            System.out.println("[SimpleCom] 解析结果: " + users.size() + " 个用户: " + users);
            usersCb.accept(users.toArray(new String[0]));
        } catch (Exception e) {
            System.err.println("[SimpleCom] 解析异常: " + e.getMessage());
            usersCb.accept(new String[0]);
        }
    }

    /**
     * 解析服务器状态 JSON 并触发回调。
     * Parse server status JSON and trigger callback.
     * 
     * 格式：{"type":"serverstatus","compressionEncoder":true,"lowLatency":false}
     */
    private void parseAndNotifyServerStatus(String json) {
        try {
            boolean compressionEncoder = json.contains("\"compressionEncoder\":true");
            boolean lowLatency = json.contains("\"lowLatency\":true");
            System.out.println("[SimpleCom] 解析服务器状态: compressionEncoder=" + compressionEncoder + ", lowLatency=" + lowLatency);
            
            // 缓存状态
            cachedServerStatus = new boolean[]{compressionEncoder, lowLatency};
            lowLatencyEnabled = lowLatency;
            
            // 初始化 Opus 编解码器
            if (compressionEncoder) {
                try {
                    opusEncoder = com.xiaofan.audio.OpusCodec.createEncoder();
                    opusDecoder = com.xiaofan.audio.OpusCodec.createDecoder();
                    compressionEnabled = true;
                    System.out.println("[SimpleCom] Opus 编解码器已初始化 | Opus codec initialized");
                } catch (io.github.jaredmdobson.concentus.OpusException e) {
                    System.err.println("[SimpleCom] Opus 编解码器初始化失败 | Opus codec init failed: " + e.getMessage());
                    compressionEnabled = false;
                }
            } else {
                compressionEnabled = false;
            }
            
            // 触发回调
            Consumer<boolean[]> statusCb = this.onServerStatus;
            if (statusCb != null) {
                statusCb.accept(cachedServerStatus);
            }
        } catch (Exception e) {
            System.err.println("[SimpleCom] 解析服务器状态异常: " + e.getMessage());
        }
    }

    // ─────────────────────── Opus 解码 ────────────────────────────────────

    /**
     * 使用 Opus 解码数据。
     * Decode data with Opus.
     *
     * @param opusData Opus 编码的数据
     * @return 解码后的 PCM 数据
     * @throws io.github.jaredmdobson.concentus.OpusException 如果解码失败
     */
    private byte[] decodeWithOpus(byte[] opusData) throws io.github.jaredmdobson.concentus.OpusException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        
        int offset = 0;
        while (offset + 2 <= opusData.length) {
            // 读取帧长度（2字节）
            int frameLen = ((opusData[offset] & 0xFF) << 8) | (opusData[offset + 1] & 0xFF);
            offset += 2;
            
            if (offset + frameLen > opusData.length) {
                System.err.println("[SimpleCom] Opus 数据格式错误：帧长度超出范围");
                break;
            }
            
            // 提取帧数据
            byte[] frame = new byte[frameLen];
            System.arraycopy(opusData, offset, frame, 0, frameLen);
            offset += frameLen;
            
            // 解码
            byte[] decoded = com.xiaofan.audio.OpusCodec.decode(opusDecoder, frame);
            output.write(decoded, 0, decoded.length);
        }
        
        return output.toByteArray();
    }

    // ─────────────────────── 加密信道 ────────────────────────────────────

    /**
     * 请求加密信道列表。
     * Request encrypted channel list.
     *
     * @param callback 回调，参数为信道名称数组
     */
    public void requestEncryptedChannelList(Consumer<String[]> callback) {
        this.onEncryptedChannelList = callback;
        if (authenticated && isOpen()) {
            String json = "{\"type\":\"requestencryptedchannellist\"}";
            send(json);
            System.out.println("[SimpleCom] 请求加密信道列表");
        }
    }

    /**
     * 连接加密信道。
     * Connect to encrypted channel.
     *
     * @param channelName  信道名称
     * @param passwordHash 密码 SHA-256 哈希
     * @param callback     回调，参数为 (成功, 临时信道号)
     */
    public void connectEncryptedChannel(String channelName, String passwordHash, BiConsumer<Boolean, Integer> callback) {
        this.onEncryptedChannelConnectResult = callback;
        if (authenticated && isOpen()) {
            String json = "{\"type\":\"connectencryptedchannel\",\"name\":\"" + escapeJson(channelName)
                    + "\",\"passwordHash\":\"" + escapeJson(passwordHash) + "\"}";
            send(json);
            System.out.println("[SimpleCom] 请求连接加密信道: " + channelName);
        }
    }

    /**
     * 创建加密信道。
     * Create encrypted channel.
     *
     * @param channelName  信道名称
     * @param passwordHash 密码 SHA-256 哈希
     * @param callback     回调，参数为 (成功, 临时信道号)
     */
    public void createEncryptedChannel(String channelName, String passwordHash, BiConsumer<Boolean, Integer> callback) {
        this.onEncryptedChannelCreateResult = callback;
        if (authenticated && isOpen()) {
            String json = "{\"type\":\"createencryptedchannel\",\"name\":\"" + escapeJson(channelName)
                    + "\",\"passwordHash\":\"" + escapeJson(passwordHash) + "\"}";
            send(json);
            System.out.println("[SimpleCom] 请求创建加密信道: " + channelName);
        }
    }

    /**
     * 解析加密信道列表 JSON 并触发回调。
     * Parse encrypted channel list JSON and trigger callback.
     * 
     * 格式：{"type":"encryptedchannellist","channels":["channel1","channel2"]}
     */
    private void parseAndNotifyEncryptedChannelList(String json) {
        Consumer<String[]> callback = this.onEncryptedChannelList;
        if (callback == null) return;

        try {
            // 简单解析：提取 "channels":[...] 中的内容
            int channelsIdx = json.indexOf("\"channels\"");
            if (channelsIdx < 0) {
                callback.accept(new String[0]);
                return;
            }
            int arrayStart = json.indexOf('[', channelsIdx);
            int arrayEnd = json.indexOf(']', arrayStart);
            if (arrayStart < 0 || arrayEnd < 0) {
                callback.accept(new String[0]);
                return;
            }
            String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
            if (arrayContent.isEmpty()) {
                callback.accept(new String[0]);
                return;
            }
            // 分割并去除引号
            String[] parts = arrayContent.split(",");
            java.util.List<String> channels = new java.util.ArrayList<>();
            for (String part : parts) {
                String cleaned = part.trim().replace("\"", "");
                if (!cleaned.isEmpty()) channels.add(cleaned);
            }
            callback.accept(channels.toArray(new String[0]));
        } catch (Exception e) {
            System.err.println("[SimpleCom] 解析加密信道列表异常: " + e.getMessage());
            callback.accept(new String[0]);
        }
    }

    /**
     * 解析加密信道连接结果 JSON 并触发回调。
     * Parse encrypted channel connect result JSON and trigger callback.
     * 
     * 格式：{"type":"encryptedchannelconnect","success":true,"channel":101}
     */
    private void parseEncryptedChannelConnectResult(String json) {
        BiConsumer<Boolean, Integer> callback = this.onEncryptedChannelConnectResult;
        if (callback == null) return;

        try {
            boolean success = json.contains("\"success\":true");
            int channel = -1;
            
            if (success) {
                int channelIdx = json.indexOf("\"channel\":");
                if (channelIdx >= 0) {
                    int start = channelIdx + 10;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    if (end > start) {
                        String channelStr = json.substring(start, end).trim();
                        channel = Integer.parseInt(channelStr);
                    }
                }
            }
            
            callback.accept(success, channel);
        } catch (Exception e) {
            System.err.println("[SimpleCom] 解析加密信道连接结果异常: " + e.getMessage());
            callback.accept(false, -1);
        }
    }

    /**
     * 解析加密信道创建结果 JSON 并触发回调。
     * Parse encrypted channel create result JSON and trigger callback.
     * 
     * 格式：{"type":"encryptedchannelcreate","success":true,"channel":101}
     */
    private void parseEncryptedChannelCreateResult(String json) {
        BiConsumer<Boolean, Integer> callback = this.onEncryptedChannelCreateResult;
        if (callback == null) return;

        try {
            boolean success = json.contains("\"success\":true");
            int channel = -1;
            
            if (success) {
                int channelIdx = json.indexOf("\"channel\":");
                if (channelIdx >= 0) {
                    int start = channelIdx + 10;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    if (end > start) {
                        String channelStr = json.substring(start, end).trim();
                        channel = Integer.parseInt(channelStr);
                    }
                }
            }
            
            callback.accept(success, channel);
        } catch (Exception e) {
            System.err.println("[SimpleCom] 解析加密信道创建结果异常: " + e.getMessage());
            callback.accept(false, -1);
        }
    }
}
