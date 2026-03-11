package com.xiaofan.gui;

import com.xiaofan.ws.SimpleComWsClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 连接成功窗口，含断开按钮与按键绑定。
 * Connected window with disconnect button and PTT key binding.
 *
 * 按下 PTT 键：播放 SAE.mp3（正放）+ 开始录音
 * 松开 PTT 键：结束录音，发送语音数据包 + 播放 SAE.mp3（倒放）
 */
public final class ConnectedWindow {

    private static final KeyCode DEFAULT_PTT_KEY = KeyCode.V;
    private static final String PTT_SOUND_RESOURCE = "/SAE.mp3";

    // ── 音频缓存（懒加载，首次调用时解码一次）─────────────────────────
    private static volatile AudioData cachedAudio = null;
    private static final Object AUDIO_LOCK = new Object();

    // 当前播放线程（用于在下一个声音开始前中止上一个）
    private static volatile Thread currentSoundThread = null;

    /** 尾音开关：true=开（默认），false=关（松开PTT/收到语音后不播放倒放SAE.mp3） */
    private static volatile boolean tailSoundEnabled = true;

    public static boolean isTailSoundEnabled() { return tailSoundEnabled; }
    public static void setTailSoundEnabled(boolean enabled) { tailSoundEnabled = enabled; }

    private ConnectedWindow() {}

    // ─────────────────────── 音频数据容器 ────────────────────────────
    private static final class AudioData {
        final AudioFormat format;
        final byte[] forward;   // 正放 PCM
        final byte[] reversed;  // 倒放 PCM

        AudioData(AudioFormat format, byte[] forward) {
            this.format   = format;
            this.forward  = forward;
            this.reversed = reversePcm(forward, format.getChannels());
        }

        /**
         * 将 PCM 按音频帧（每帧 channels × 2 字节）逆序。
         * 声道顺序保持不变，只是时间方向翻转。
         */
        private static byte[] reversePcm(byte[] pcm, int channels) {
            int bytesPerFrame = channels * 2; // 16-bit
            int numFrames     = pcm.length / bytesPerFrame;
            byte[] rev        = new byte[pcm.length];
            for (int i = 0; i < numFrames; i++) {
                int srcOff = (numFrames - 1 - i) * bytesPerFrame;
                System.arraycopy(pcm, srcOff, rev, i * bytesPerFrame, bytesPerFrame);
            }
            return rev;
        }

        /**
         * 使用 JLayer 内部 API 将 MP3 解码为交错 16-bit 小端 PCM。
         * SampleBuffer 的布局：L0, R0, L1, R1, …（已交错）。
         */
        static AudioData from(byte[] mp3) throws Exception {
            Bitstream bs = new Bitstream(new ByteArrayInputStream(mp3));
            Decoder dec = new Decoder();
            ByteArrayOutputStream pcm = new ByteArrayOutputStream(mp3.length * 4);

            float sampleRate = 44100f;
            int   channels   = 2;
            boolean first    = true;

            Header h;
            while ((h = bs.readFrame()) != null) {
                try {
                    SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                    if (first) {
                        sampleRate = h.frequency();
                        channels   = (h.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                        first      = false;
                    }
                    short[] buf = sb.getBuffer();
                    int     len = sb.getBufferLength(); // 交错样本总数
                    for (int i = 0; i < len; i++) {
                        pcm.write(buf[i] & 0xFF);         // 低字节
                        pcm.write((buf[i] >> 8) & 0xFF);  // 高字节
                    }
                } finally {
                    bs.closeFrame();
                }
            }
            bs.close();

            // signed=true, bigEndian=false（小端，与上面写法一致）
            AudioFormat fmt = new AudioFormat(sampleRate, 16, channels, true, false);
            return new AudioData(fmt, pcm.toByteArray());
        }
    }

    // ─────────────────────── 音频加载与播放 ──────────────────────────

    private static AudioData getAudio() {
        if (cachedAudio != null) return cachedAudio;
        synchronized (AUDIO_LOCK) {
            if (cachedAudio != null) return cachedAudio;
            try (InputStream is = ConnectedWindow.class.getResourceAsStream(PTT_SOUND_RESOURCE)) {
                if (is == null) {
                    System.err.println("[SimpleCom] 找不到资源: " + PTT_SOUND_RESOURCE);
                    return null;
                }
                cachedAudio = AudioData.from(is.readAllBytes());
            } catch (Exception e) {
                System.err.println("[SimpleCom] 音频解码失败: " + e.getMessage());
            }
            return cachedAudio;
        }
    }

    /** 播放一段 PCM 数据，自动打断上一个未播完的声音。 */
    private static void playPcm(byte[] pcm, AudioFormat format) {
        // 打断上一个还在播放的线程
        Thread old = currentSoundThread;
        if (old != null && old.isAlive()) old.interrupt();

        Thread t = new Thread(() -> {
            try {
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format);
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
                System.err.println("[SimpleCom] 音频设备不可用: " + e.getMessage());
            }
        }, "SimpleCom-PTT-Sound");
        t.setDaemon(true);
        currentSoundThread = t;
        t.start();
    }

    /** 按下 PTT 键时调用：正放 SAE.mp3 */
    public static void playPttSound() {
        AudioData audio = getAudio();
        if (audio == null) return;
        playPcm(audio.forward, audio.format);
    }

    /**
     * 松开 PTT 键时调用：倒放 SAE.mp3（尾音关闭时跳过）。
     * Called on PTT release: play reversed SAE.mp3 (skipped when tail sound is disabled).
     */
    public static void playPttReleaseSound() {
        if (!tailSoundEnabled) return;
        AudioData audio = getAudio();
        if (audio == null) return;
        playPcm(audio.reversed, audio.format);
    }

    // ─────────────────────── GUI 构建 ────────────────────────────────

    /**
     * @param server       服务器地址
     * @param username     已认证的 MC 用户名
     * @param wsClient     已认证的 WS 客户端（用于发送语音包）
     * @param onDisconnect 点击断开时调用 | Called when disconnect button is clicked
     */
    public static Scene createScene(String server, String username,
                                    SimpleComWsClient wsClient, Runnable onDisconnect) {
        // 延迟标签（右上角）
        Label latencyLabel = new Label(I18n.get("status.latency_na"));
        latencyLabel.setFont(Font.font(11));
        latencyLabel.setStyle("-fx-text-fill: #888888;");
        latencyLabel.setPadding(new Insets(6, 10, 0, 0));

        // 注册延迟回调
        if (wsClient != null) {
            wsClient.setOnLatencyUpdate(rtt -> Platform.runLater(() -> {
                String text = I18n.get("status.latency") + rtt + " ms";
                String color;
                if (rtt <= 100) {
                    color = "#388E3C"; // 绿
                } else if (rtt <= 300) {
                    color = "#F57F17"; // 黄
                } else {
                    color = "#C62828"; // 红
                }
                latencyLabel.setText(text);
                latencyLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }));
        }

        VBox root = new VBox(14);
        root.setPadding(new Insets(36, 40, 36, 40));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(400);

        Label checkmark = new Label("✓");
        checkmark.setFont(Font.font(56));
        checkmark.setStyle("-fx-text-fill: #388E3C;");

        Label msgLabel = new Label(I18n.get("connected.msg"));
        msgLabel.setFont(Font.font(20));
        msgLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #388E3C;");

        Label serverLabel = new Label(I18n.get("connected.server") + server);
        serverLabel.setStyle("-fx-text-fill: #333333;");

        Label userLabel = new Label(I18n.get("connected.user") + username);
        userLabel.setStyle("-fx-text-fill: #333333;");

        // 录音状态标签（默认隐藏）
        Label recordingLabel = new Label(I18n.get("status.recording"));
        recordingLabel.setFont(Font.font(14));
        recordingLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-weight: bold;");
        recordingLabel.setVisible(false);
        recordingLabel.setManaged(false);

        // ── 麦克风测试区域（需要在录音器之前创建，以便在回调中重置）──────────
        javafx.scene.control.ProgressBar volumeBar = new javafx.scene.control.ProgressBar(0);
        volumeBar.setPrefWidth(320);
        volumeBar.setPrefHeight(20);
        volumeBar.setStyle("-fx-accent: #388E3C;");

        javafx.scene.canvas.Canvas waveformCanvas = new javafx.scene.canvas.Canvas(320, 60);
        waveformCanvas.setStyle("-fx-border-color: #DDDDDD; -fx-border-width: 1;");
        javafx.scene.canvas.GraphicsContext gc = waveformCanvas.getGraphicsContext2D();
        gc.setFill(javafx.scene.paint.Color.web("#F5F5F5"));
        gc.fillRect(0, 0, 320, 60);

        // 录音器（绑定到 wsClient 的发送方法）
        VoiceRecorder voiceRecorder = new VoiceRecorder(
                username,
                packetBytes -> {
                    if (wsClient != null) wsClient.sendVoicePacket(packetBytes);
                },
                isRecording -> Platform.runLater(() -> {
                    recordingLabel.setVisible(isRecording);
                    recordingLabel.setManaged(isRecording);
                    // 松开 PTT 键时重置音量条和波形图
                    if (!isRecording) {
                        volumeBar.setProgress(0);
                        gc.setFill(javafx.scene.paint.Color.web("#F5F5F5"));
                        gc.fillRect(0, 0, 320, 60);
                    }
                })
        );

        // 按键绑定
        final KeyCode[] boundKey = {DEFAULT_PTT_KEY};
        PttKeyManager.getInstance().setKeyCode(DEFAULT_PTT_KEY.getCode());
        final boolean[] capturing = {false};
        Label keyLabel = new Label(I18n.get("label.ptt_key") + ": " + boundKey[0].getName());
        keyLabel.setStyle("-fx-text-fill: #333333;");
        Button changeKeyBtn = new Button(I18n.get("btn.change_key"));
        changeKeyBtn.setOnAction(e -> {
            capturing[0] = true;
            keyLabel.setText(I18n.get("hint.press_key"));
        });
        HBox keyRow = new HBox(10);
        keyRow.setAlignment(Pos.CENTER);
        keyRow.getChildren().addAll(keyLabel, changeKeyBtn);

        // ── 信道选择 ────────────────────────────────────────────────────
        Label channelTitle = new Label("Channel | 信道:");
        channelTitle.setStyle("-fx-text-fill: #555555; -fx-font-size: 11; -fx-font-weight: bold;");
        
        javafx.scene.control.ComboBox<Integer> channelCombo = new javafx.scene.control.ComboBox<>();
        for (int i = 0; i <= 100; i++) channelCombo.getItems().add(i);
        channelCombo.setValue(wsClient != null ? wsClient.getCurrentChannel() : 1);
        channelCombo.setPrefWidth(80);
        channelCombo.setStyle("-fx-font-size: 11;");
        
        Label channelHint = new Label("(0 = Mute | 静音)");
        channelHint.setStyle("-fx-text-fill: #888888; -fx-font-size: 10;");
        
        Label channelStatus = new Label("Channel: " + (wsClient != null ? wsClient.getCurrentChannel() : 1));
        channelStatus.setStyle("-fx-text-fill: #388E3C; -fx-font-size: 11;");
        
        channelCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (wsClient != null) {
                wsClient.switchChannel(newVal);
                
                // 信道 0 特殊处理：停止录音监听，清空用户列表
                if (newVal == 0) {
                    channelStatus.setText("Channel: 0 (Muted | 已静音)");
                    channelStatus.setStyle("-fx-text-fill: #888888; -fx-font-size: 11;");
                    // 如果正在录音，停止录音
                    voiceRecorder.stopRecording();
                } else {
                    channelStatus.setText("Channel: " + newVal + " (Pending... | 等待确认)");
                    channelStatus.setStyle("-fx-text-fill: #F57F17; -fx-font-size: 11;");
                }
            }
        });
        
        // 注册信道确认回调
        if (wsClient != null) {
            wsClient.setOnChannelConfirmed(ch -> Platform.runLater(() -> {
                if (ch == 0) {
                    channelStatus.setText("Channel: 0 (Muted | 已静音)");
                    channelStatus.setStyle("-fx-text-fill: #888888; -fx-font-size: 11;");
                } else if (ch > 100) {
                    channelStatus.setText("Channel: " + ch + " (Encrypted | 加密信道)");
                    channelStatus.setStyle("-fx-text-fill: #F57F17; -fx-font-size: 11;");
                } else {
                    channelStatus.setText("Channel: " + ch + " (Active | 已激活)");
                    channelStatus.setStyle("-fx-text-fill: #388E3C; -fx-font-size: 11;");
                }
            }));
        }
        
        HBox channelRow = new HBox(8);
        channelRow.setAlignment(Pos.CENTER_LEFT);
        channelRow.getChildren().addAll(channelTitle, channelCombo, channelHint);
        
        // 加密信道按钮
        Button encryptedChannelBtn = new Button("Encrypted Channels | 加密信道");
        encryptedChannelBtn.setStyle("-fx-font-size: 11;");
        encryptedChannelBtn.setOnAction(e -> {
            if (wsClient != null) {
                showEncryptedChannelList(wsClient, channelCombo);
            }
        });
        
        VBox channelSection = new VBox(4, channelRow, channelStatus, encryptedChannelBtn);

        // ── 服务器状态面板 ──────────────────────────────────────────────
        Label serverStatusTitle = new Label("Server Status | 服务器状态:");
        serverStatusTitle.setStyle("-fx-text-fill: #555555; -fx-font-size: 11; -fx-font-weight: bold;");

        Label compressionLabel = new Label("Compression Encoder | 压缩编码器: --");
        compressionLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        Label lowLatencyLabel = new Label("Low Latency | 低延迟: --");
        lowLatencyLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        VBox serverStatusSection = new VBox(4, serverStatusTitle, compressionLabel, lowLatencyLabel);

        // 注册服务器状态回调
        if (wsClient != null) {
            wsClient.setOnServerStatus(status -> Platform.runLater(() -> {
                boolean compressionEncoder = status[0];
                boolean lowLatency = status[1];
                
                String compressionText = compressionEncoder 
                    ? "Compression Encoder | 压缩编码器: ✓ Enabled | 启用"
                    : "Compression Encoder | 压缩编码器: ✗ Disabled | 关闭";
                String compressionColor = compressionEncoder ? "#388E3C" : "#888888";
                compressionLabel.setText(compressionText);
                compressionLabel.setStyle("-fx-text-fill: " + compressionColor + "; -fx-font-size: 11;");
                
                String lowLatencyText = lowLatency
                    ? "Low Latency | 低延迟: ✓ Enabled | 启用"
                    : "Low Latency | 低延迟: ✗ Disabled | 关闭";
                String lowLatencyColor = lowLatency ? "#F57F17" : "#888888";
                lowLatencyLabel.setText(lowLatencyText);
                lowLatencyLabel.setStyle("-fx-text-fill: " + lowLatencyColor + "; -fx-font-size: 11;");
                
                // 如果启用压缩，设置 VoiceRecorder 的 Opus 编码器
                if (compressionEncoder) {
                    try {
                        io.github.jaredmdobson.concentus.OpusEncoder encoder = com.xiaofan.audio.OpusCodec.createEncoder();
                        voiceRecorder.setOpusEncoder(encoder);
                        System.out.println("[SimpleCom] VoiceRecorder Opus 编码器已设置");
                    } catch (io.github.jaredmdobson.concentus.OpusException e) {
                        System.err.println("[SimpleCom] 创建 Opus 编码器失败: " + e.getMessage());
                    }
                }
                
                // 设置低延迟模式
                voiceRecorder.setLowLatencyMode(lowLatency);
            }));
        }

        // ── 在线用户列表面板 ──────────────────────────────────────────────
        Label onlineTitle = new Label(I18n.get("label.online_users") + ":");
        onlineTitle.setStyle("-fx-text-fill: #555555; -fx-font-size: 11; -fx-font-weight: bold;");

        Label onlineHint = new Label(I18n.get("online_users.hint"));
        onlineHint.setStyle("-fx-text-fill: #888888; -fx-font-size: 10; -fx-font-style: italic;");

        // FlowPane 用于横向流式排列用户名标签，自动换行
        FlowPane usersPane = new FlowPane(6, 4);
        usersPane.setPadding(new Insets(4, 8, 4, 8));
        usersPane.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #DDDDDD; -fx-border-radius: 4; -fx-background-radius: 4;");
        usersPane.setPrefWrapLength(320);

        Label emptyLabel = new Label(I18n.get("online_users.empty"));
        emptyLabel.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 11;");
        usersPane.getChildren().add(emptyLabel);

        VBox onlineSection = new VBox(4, onlineTitle, onlineHint, usersPane);

        // 用户名标签缓存：用户名小写 → Label
        java.util.Map<String, Label> userLabels = new java.util.LinkedHashMap<>();

        // 注册在线用户列表更新回调
        if (wsClient != null) {
            wsClient.setOnOnlineUsersUpdate(users -> {
                System.out.println("[SimpleCom] UI 收到用户列表更新回调，用户数: " + (users == null ? "null" : users.length));
                if (users != null && users.length > 0) {
                    System.out.println("[SimpleCom] 用户列表内容: " + java.util.Arrays.toString(users));
                }
                Platform.runLater(() -> {
                    System.out.println("[SimpleCom] 开始更新 UI");
                    userLabels.clear();
                    usersPane.getChildren().clear();
                    if (users == null || users.length == 0) {
                        System.out.println("[SimpleCom] 显示空列表提示");
                        usersPane.getChildren().add(emptyLabel);
                        return;
                    }
                    for (String u : users) {
                        if (u == null || u.isBlank()) continue;
                        String userName = u.trim();
                        Label lbl = new Label(userName);
                        
                        // 检查是否被屏蔽，设置初始样式
                        boolean blocked = wsClient.isUserBlocked(userName);
                        if (blocked) {
                            lbl.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12; -fx-font-weight: bold; "
                                    + "-fx-background-color: #D32F2F; -fx-padding: 2 8 2 8; "
                                    + "-fx-background-radius: 10; -fx-cursor: hand;");
                        } else {
                            lbl.setStyle("-fx-text-fill: #333333; -fx-font-size: 12; "
                                    + "-fx-background-color: #E8E8E8; -fx-padding: 2 8 2 8; "
                                    + "-fx-background-radius: 10; -fx-cursor: hand;");
                        }
                        
                        // 右键点击切换屏蔽状态
                        lbl.setOnMouseClicked(event -> {
                            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                                boolean nowBlocked = wsClient.toggleBlockUser(userName);
                                if (nowBlocked) {
                                    // 已屏蔽：红色
                                    lbl.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12; -fx-font-weight: bold; "
                                            + "-fx-background-color: #D32F2F; -fx-padding: 2 8 2 8; "
                                            + "-fx-background-radius: 10; -fx-cursor: hand;");
                                } else {
                                    // 取消屏蔽：恢复默认
                                    lbl.setStyle("-fx-text-fill: #333333; -fx-font-size: 12; "
                                            + "-fx-background-color: #E8E8E8; -fx-padding: 2 8 2 8; "
                                            + "-fx-background-radius: 10; -fx-cursor: hand;");
                                }
                            }
                        });
                        
                        userLabels.put(userName.toLowerCase(), lbl);
                        usersPane.getChildren().add(lbl);
                        System.out.println("[SimpleCom] 添加用户标签: " + userName);
                    }
                    if (usersPane.getChildren().isEmpty()) {
                        System.out.println("[SimpleCom] usersPane 为空，添加空列表提示");
                        usersPane.getChildren().add(emptyLabel);
                    }
                    System.out.println("[SimpleCom] UI 更新完成，usersPane 子节点数: " + usersPane.getChildren().size());
                });
            });

            // 共享的高亮恢复调度器（复用，避免每次语音都新建线程池）
            java.util.concurrent.ScheduledExecutorService highlightScheduler =
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "SimpleCom-HighlightReset");
                        t.setDaemon(true);
                        return t;
                    });

            // 每个用户的高亮超时任务：用户名小写 → ScheduledFuture
            java.util.Map<String, java.util.concurrent.ScheduledFuture<?>> highlightTimeouts = new java.util.concurrent.ConcurrentHashMap<>();

            // 注册语音播放回调：收到语音包立即高亮，持续收到就刷新超时，停止收到后 1 秒熄灭
            wsClient.setOnVoiceSpeaking(speakingUsername -> {
                String key = speakingUsername.trim().toLowerCase();
                Platform.runLater(() -> {
                    Label lbl = userLabels.get(key);
                    if (lbl == null) return;
                    
                    // 绿色高亮
                    lbl.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12; -fx-font-weight: bold; "
                            + "-fx-background-color: #388E3C; -fx-padding: 2 8 2 8; "
                            + "-fx-background-radius: 10; -fx-cursor: hand;");
                    
                    // 取消之前的超时任务
                    java.util.concurrent.ScheduledFuture<?> oldTask = highlightTimeouts.get(key);
                    if (oldTask != null) oldTask.cancel(false);
                    
                    // 设置新的超时任务：1 秒后熄灭（如果 1 秒内没有新包，说明该用户停止说话）
                    java.util.concurrent.ScheduledFuture<?> newTask = highlightScheduler.schedule(() -> Platform.runLater(() -> {
                        Label current = userLabels.get(key);
                        if (current != null) {
                            current.setStyle("-fx-text-fill: #333333; -fx-font-size: 12; "
                                    + "-fx-background-color: #E8E8E8; -fx-padding: 2 8 2 8; "
                                    + "-fx-background-radius: 10; -fx-cursor: hand;");
                        }
                        highlightTimeouts.remove(key);
                    }), 1, java.util.concurrent.TimeUnit.SECONDS);
                    
                    highlightTimeouts.put(key, newTask);
                });
            });

            // 注册被屏蔽用户尝试说话回调：橙色提示，3秒后恢复红色
            wsClient.setOnBlockedUserSpeaking(blockedUsername -> {
                String key = blockedUsername.trim().toLowerCase();
                Platform.runLater(() -> {
                    Label lbl = userLabels.get(key);
                    if (lbl == null) return;
                    // 橙色提示（表示被屏蔽的用户尝试说话）
                    lbl.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12; -fx-font-weight: bold; "
                            + "-fx-background-color: #F57F17; -fx-padding: 2 8 2 8; "
                            + "-fx-background-radius: 10; -fx-cursor: hand;");
                    // 3秒后恢复红色（屏蔽状态）
                    highlightScheduler.schedule(() -> Platform.runLater(() -> {
                        Label current = userLabels.get(key);
                        if (current != null && wsClient.isUserBlocked(blockedUsername)) {
                            current.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12; -fx-font-weight: bold; "
                                    + "-fx-background-color: #D32F2F; -fx-padding: 2 8 2 8; "
                                    + "-fx-background-radius: 10; -fx-cursor: hand;");
                        }
                    }), 3, java.util.concurrent.TimeUnit.SECONDS);
                });
            });
        }

        // 尾音开关
        Label tailLabel = new Label(I18n.get("label.tail_sound") + ":");
        tailLabel.setStyle("-fx-text-fill: #333333;");
        ToggleGroup tailGroup = new ToggleGroup();
        ToggleButton tailOn  = new ToggleButton(I18n.get("tail_sound.on"));
        ToggleButton tailOff = new ToggleButton(I18n.get("tail_sound.off"));
        tailOn.setToggleGroup(tailGroup);
        tailOff.setToggleGroup(tailGroup);
        tailOn.setSelected(tailSoundEnabled);
        tailOff.setSelected(!tailSoundEnabled);
        tailOn.setStyle("-fx-font-size: 11;");
        tailOff.setStyle("-fx-font-size: 11;");
        tailGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) { oldT.setSelected(true); return; } // 防止全部取消选中
            tailSoundEnabled = (newT == tailOn);
        });
        HBox tailRow = new HBox(6);
        tailRow.setAlignment(Pos.CENTER);
        tailRow.getChildren().addAll(tailLabel, tailOn, tailOff);

        Label noteLabel = new Label(I18n.get("connected.note"));
        noteLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11;");
        noteLabel.setWrapText(true);

        // ── 麦克风测试区域标签和布局 ──────────────────────────────────────
        Label micTestLabel = new Label("Microphone Test | 麦克风测试:");
        micTestLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11; -fx-font-weight: bold;");

        VBox micTestSection = new VBox(4, micTestLabel, volumeBar, waveformCanvas);

        // 音频数据处理：计算音量和绘制波形
        voiceRecorder.setOnAudioData(pcmChunk -> {
            // 计算 RMS 音量
            double rms = calculateRMS(pcmChunk);
            double volume = Math.min(1.0, rms / 3000.0); // 归一化到 0-1

            // 提取波形样本（每隔 N 个样本取一个）
            int sampleCount = 160; // 显示 160 个点
            short[] samples = extractSamples(pcmChunk, sampleCount);

            Platform.runLater(() -> {
                // 更新音量条
                volumeBar.setProgress(volume);

                // 绘制波形
                gc.setFill(javafx.scene.paint.Color.web("#F5F5F5"));
                gc.fillRect(0, 0, 320, 60);
                gc.setStroke(javafx.scene.paint.Color.web("#388E3C"));
                gc.setLineWidth(1.5);

                double width = 320.0;
                double height = 60.0;
                double centerY = height / 2;
                double xStep = width / sampleCount;

                for (int i = 0; i < samples.length - 1; i++) {
                    double x1 = i * xStep;
                    double x2 = (i + 1) * xStep;
                    double y1 = centerY + (samples[i] / 32768.0) * (height / 2);
                    double y2 = centerY + (samples[i + 1] / 32768.0) * (height / 2);
                    gc.strokeLine(x1, y1, x2, y2);
                }
            });
        });

        Button disconnectBtn = new Button(I18n.get("btn.disconnect"));
        disconnectBtn.setMaxWidth(Double.MAX_VALUE);
        disconnectBtn.setOnAction(e -> {
            voiceRecorder.stopRecording();
            if (onDisconnect != null) onDisconnect.run();
        });

        root.getChildren().addAll(
                checkmark, msgLabel, new Separator(),
                serverLabel, userLabel, recordingLabel,
                channelSection, new Separator(),
                serverStatusSection, new Separator(),
                onlineSection, new Separator(),
                keyRow, tailRow, noteLabel,
                new Separator(), micTestSection,
                disconnectBtn
        );

        // 用 BorderPane 将延迟标签固定在右上角
        BorderPane outerPane = new BorderPane();
        outerPane.setCenter(root);
        StackPane topRight = new StackPane(latencyLabel);
        StackPane.setAlignment(latencyLabel, Pos.TOP_RIGHT);
        outerPane.setTop(topRight);

        Scene scene = new Scene(outerPane);
        PttKeyManager.getInstance().setKeyCode(boundKey[0].getCode());

        // 手动追踪按键状态，避免触发重复 KEY_PRESSED 事件（JavaFX 17 无 isRepeat()）
        final boolean[] pttDown = {false};

        // 按下：正放 SAE.mp3 + 开始录音（仅首次按下触发，忽略长按重复）
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (capturing[0]) {
                boundKey[0] = ev.getCode();
                keyLabel.setText(I18n.get("label.ptt_key") + ": " + boundKey[0].getName());
                PttKeyManager.getInstance().setKeyCode(boundKey[0].getCode());
                capturing[0] = false;
                pttDown[0] = false;
                ev.consume();
                return;
            }
            if (ev.getCode() == boundKey[0] && !pttDown[0]) {
                // 信道 0（静音）：不触发录音
                if (wsClient != null && wsClient.getCurrentChannel() == 0) {
                    return;
                }
                pttDown[0] = true;
                playPttSound();
                voiceRecorder.startRecording();
                ev.consume();
            }
        });

        // 松开：结束录音 + 倒放 SAE.mp3
        root.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            if (!capturing[0] && ev.getCode() == boundKey[0]) {
                pttDown[0] = false;
                voiceRecorder.stopRecording();
                playPttReleaseSound();
                ev.consume();
            }
        });

        // Windows 全局热键回调也需要触发录音
        PttKeyManager.getInstance().setOnKeyPressed(() -> {
            if (!pttDown[0]) {
                pttDown[0] = true;
                playPttSound();
                voiceRecorder.startRecording();
            }
        });
        PttKeyManager.getInstance().setOnKeyReleased(() -> {
            pttDown[0] = false;
            voiceRecorder.stopRecording();
            playPttReleaseSound();
        });

        return scene;
    }

    // ─────────────────────── 音频分析辅助方法 ────────────────────────────

    /**
     * 计算 PCM 数据的 RMS（均方根）音量。
     * Calculate RMS volume of PCM data.
     *
     * @param pcm 16-bit 小端有符号 PCM 数据
     * @return RMS 值
     */
    private static double calculateRMS(byte[] pcm) {
        if (pcm == null || pcm.length < 2) return 0;
        long sum = 0;
        int count = 0;
        for (int i = 0; i < pcm.length - 1; i += 2) {
            // 小端 16-bit 有符号
            int low = pcm[i] & 0xFF;
            int high = pcm[i + 1];
            short sample = (short) ((high << 8) | low);
            sum += (long) sample * sample;
            count++;
        }
        if (count == 0) return 0;
        return Math.sqrt((double) sum / count);
    }

    /**
     * 从 PCM 数据中提取固定数量的样本点（用于波形显示）。
     * Extract fixed number of samples from PCM data (for waveform display).
     *
     * @param pcm         16-bit 小端有符号 PCM 数据
     * @param sampleCount 需要提取的样本数
     * @return 样本数组
     */
    private static short[] extractSamples(byte[] pcm, int sampleCount) {
        if (pcm == null || pcm.length < 2) return new short[sampleCount];
        int totalSamples = pcm.length / 2;
        if (totalSamples <= sampleCount) {
            // 样本数不足，直接全部提取
            short[] result = new short[totalSamples];
            for (int i = 0; i < totalSamples; i++) {
                int low = pcm[i * 2] & 0xFF;
                int high = pcm[i * 2 + 1];
                result[i] = (short) ((high << 8) | low);
            }
            return result;
        }
        // 均匀采样
        short[] result = new short[sampleCount];
        double step = (double) totalSamples / sampleCount;
        for (int i = 0; i < sampleCount; i++) {
            int idx = (int) (i * step);
            if (idx * 2 + 1 < pcm.length) {
                int low = pcm[idx * 2] & 0xFF;
                int high = pcm[idx * 2 + 1];
                result[i] = (short) ((high << 8) | low);
            }
        }
        return result;
    }

    // ─────────────────────── 加密信道 GUI ────────────────────────────────

    /**
     * 显示加密信道列表窗口。
     * Show encrypted channel list window.
     */
    private static void showEncryptedChannelList(SimpleComWsClient wsClient, javafx.scene.control.ComboBox<Integer> channelCombo) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Encrypted Channels | 加密信道");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(400);

        Label titleLabel = new Label("Loading... | 加载中...");
        titleLabel.setFont(Font.font(14));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // 信道列表容器（最多5个按钮）
        VBox channelListBox = new VBox(8);
        channelListBox.setAlignment(Pos.CENTER);

        // 分页控制
        final int[] currentPage = {0};
        final java.util.List<String> channelNames = new java.util.ArrayList<>();

        HBox pageControl = new HBox(10);
        pageControl.setAlignment(Pos.CENTER);
        Button prevBtn = new Button("◀ Previous | 上一页");
        Button nextBtn = new Button("Next | 下一页 ▶");
        Label pageLabel = new Label("Page 1 | 第 1 页");
        pageControl.getChildren().addAll(prevBtn, pageLabel, nextBtn);
        pageControl.setVisible(false);

        // 更新页面显示
        Runnable updatePage = () -> {
            channelListBox.getChildren().clear();
            int start = currentPage[0] * 5;
            int end = Math.min(start + 5, channelNames.size());
            
            if (channelNames.isEmpty()) {
                Label emptyLabel = new Label("No encrypted channels | 暂无加密信道");
                emptyLabel.setStyle("-fx-text-fill: #888888;");
                channelListBox.getChildren().add(emptyLabel);
            } else {
                for (int i = start; i < end; i++) {
                    String name = channelNames.get(i);
                    Button btn = new Button(name);
                    btn.setMaxWidth(Double.MAX_VALUE);
                    btn.setOnAction(e -> showPasswordInput(wsClient, name, channelCombo, stage));
                    channelListBox.getChildren().add(btn);
                }
            }
            
            int totalPages = (int) Math.ceil(channelNames.size() / 5.0);
            pageLabel.setText("Page " + (currentPage[0] + 1) + " / " + totalPages + " | 第 " + (currentPage[0] + 1) + " / " + totalPages + " 页");
            prevBtn.setDisable(currentPage[0] == 0);
            nextBtn.setDisable(currentPage[0] >= totalPages - 1);
            pageControl.setVisible(channelNames.size() > 5);
        };

        prevBtn.setOnAction(e -> {
            if (currentPage[0] > 0) {
                currentPage[0]--;
                updatePage.run();
            }
        });

        nextBtn.setOnAction(e -> {
            int totalPages = (int) Math.ceil(channelNames.size() / 5.0);
            if (currentPage[0] < totalPages - 1) {
                currentPage[0]++;
                updatePage.run();
            }
        });

        // 底部按钮
        HBox bottomButtons = new HBox(10);
        bottomButtons.setAlignment(Pos.CENTER);
        Button backBtn = new Button("Back | 返回");
        Button createBtn = new Button("Create New | 新建加密信道");
        backBtn.setOnAction(e -> stage.close());
        createBtn.setOnAction(e -> {
            stage.close();
            showCreateEncryptedChannel(wsClient, channelCombo);
        });
        bottomButtons.getChildren().addAll(backBtn, createBtn);

        root.getChildren().addAll(titleLabel, new Separator(), channelListBox, pageControl, new Separator(), bottomButtons);

        // 请求加密信道列表
        wsClient.requestEncryptedChannelList(names -> Platform.runLater(() -> {
            channelNames.clear();
            if (names != null) {
                channelNames.addAll(java.util.Arrays.asList(names));
            }
            titleLabel.setText("Encrypted Channels | 加密信道列表");
            currentPage[0] = 0;
            updatePage.run();
        }));

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * 显示创建加密信道窗口。
     * Show create encrypted channel window.
     */
    private static void showCreateEncryptedChannel(SimpleComWsClient wsClient, javafx.scene.control.ComboBox<Integer> channelCombo) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Create Encrypted Channel | 新建加密信道");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(400);

        Label titleLabel = new Label("Create Encrypted Channel | 新建加密信道");
        titleLabel.setFont(Font.font(14));
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label nameLabel = new Label("Channel Name | 信道名称:");
        javafx.scene.control.TextField nameField = new javafx.scene.control.TextField();
        nameField.setPromptText("Enter channel name | 输入信道名称");

        Label passwordLabel = new Label("Password | 密码:");
        javafx.scene.control.PasswordField passwordField = new javafx.scene.control.PasswordField();
        passwordField.setPromptText("Enter password | 输入密码");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #D32F2F;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);
        Button cancelBtn = new Button("Cancel | 取消");
        Button createBtn = new Button("Create | 创建");
        
        cancelBtn.setOnAction(e -> stage.close());
        createBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String password = passwordField.getText();
            
            if (name.isEmpty()) {
                statusLabel.setText("Channel name cannot be empty | 信道名称不能为空");
                return;
            }
            if (password.isEmpty()) {
                statusLabel.setText("Password cannot be empty | 密码不能为空");
                return;
            }
            
            // 计算密码 SHA-256 哈希
            String passwordHash = sha256(password);
            
            // 发送创建请求
            wsClient.createEncryptedChannel(name, passwordHash, (success, tempChannel) -> Platform.runLater(() -> {
                if (success) {
                    stage.close();
                    // 直接切换到临时信道（不通过 ComboBox，因为它只支持 0-100）
                    wsClient.switchChannel(tempChannel);
                    showInfo("Success | 成功", "Encrypted channel created and connected | 加密信道已创建并连接");
                } else {
                    statusLabel.setText("Failed to create channel | 创建信道失败");
                }
            }));
        });
        
        buttons.getChildren().addAll(cancelBtn, createBtn);

        root.getChildren().addAll(titleLabel, new Separator(), nameLabel, nameField, passwordLabel, passwordField, statusLabel, new Separator(), buttons);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * 显示密码输入窗口。
     * Show password input window.
     */
    private static void showPasswordInput(SimpleComWsClient wsClient, String channelName, javafx.scene.control.ComboBox<Integer> channelCombo, javafx.stage.Stage listStage) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Enter Password | 输入密码");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setPrefWidth(350);

        Label titleLabel = new Label("Channel: " + channelName);
        titleLabel.setFont(Font.font(14));
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label passwordLabel = new Label("Password | 密码:");
        javafx.scene.control.PasswordField passwordField = new javafx.scene.control.PasswordField();
        passwordField.setPromptText("Enter password | 输入密码");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #D32F2F;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);
        Button cancelBtn = new Button("Cancel | 取消");
        Button confirmBtn = new Button("Confirm | 确认");
        
        cancelBtn.setOnAction(e -> stage.close());
        confirmBtn.setOnAction(e -> {
            String password = passwordField.getText();
            
            if (password.isEmpty()) {
                statusLabel.setText("Password cannot be empty | 密码不能为空");
                return;
            }
            
            // 计算密码 SHA-256 哈希
            String passwordHash = sha256(password);
            
            // 发送连接请求
            wsClient.connectEncryptedChannel(channelName, passwordHash, (success, tempChannel) -> Platform.runLater(() -> {
                if (success) {
                    stage.close();
                    listStage.close();
                    // 直接切换到临时信道（不通过 ComboBox，因为它只支持 0-100）
                    wsClient.switchChannel(tempChannel);
                    showInfo("Success | 成功", "Connected to encrypted channel | 已连接到加密信道");
                } else {
                    statusLabel.setText("Wrong password | 密码错误");
                }
            }));
        });
        
        buttons.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(titleLabel, new Separator(), passwordLabel, passwordField, statusLabel, new Separator(), buttons);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * 显示信息对话框。
     * Show info dialog.
     */
    private static void showInfo(String title, String message) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle(title);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);

        Button okBtn = new Button("OK | 确定");
        okBtn.setOnAction(e -> stage.close());

        root.getChildren().addAll(messageLabel, okBtn);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * 计算字符串的 SHA-256 哈希。
     * Calculate SHA-256 hash of a string.
     */
    private static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
