package com.xiaofan.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音对讲：按键按住录音，松开发送
 * 物品栏上方显示：正在录音 / 正在对讲
 */
@Environment(EnvType.CLIENT)
public final class SimpleComVoiceClient {

    public static final String MSG_RECORDING = "正在录音...";
    public static final String MSG_RECORD_DONE = "录音完成，共%dKB";
    public static final String MSG_SPEAKING = "%s正在对讲";
    /** 多人同时讲话时物品栏文案后缀 */
    public static final String MSG_SPEAKING_MULTI_SUFFIX = " 正在对讲";
    private static final long SPEAKING_DURATION_MS = 2500;
    private static final int MAX_SPEAKING_NAMES = 5;
    private static final Map<String, Long> speakingUntil = new ConcurrentHashMap<>();

    private static final String MSG_CONNECT_SUCCESS = "[简单的通讯器]：§a服务器握手成功，服务器类型：%s，按住 %s 录音";

    /** 语音通信按键，默认 V (GLFW_KEY_V=86) */
    public static final KeyBinding VOICE_KEY = new KeyBinding(
            "key.simplecom.voice",
            InputUtil.Type.KEYSYM,
            86, // V
            "category.simplecom"
    );

    /** 信道设置按键，默认 C (GLFW_KEY_C=67) */
    public static final KeyBinding CHANNEL_KEY = new KeyBinding(
            "key.simplecom.channel",
            InputUtil.Type.KEYSYM,
            67, // C
            "category.simplecom"
    );

    /** 平台注入的发送器 */
    private static volatile VoiceDataSender voiceDataSender;

    /** 平台注入的 UI 回调：显示物品栏上方文字 */
    private static volatile ActionBarCallback actionBarCallback;

    private SimpleComVoiceClient() {
    }

    public static void setVoiceDataSender(VoiceDataSender sender) {
        voiceDataSender = sender;
    }

    public static void setActionBarCallback(ActionBarCallback callback) {
        actionBarCallback = callback;
    }

    public static void sendVoiceData(byte[] data) {
        if (voiceDataSender != null && data != null && data.length > 0) {
            voiceDataSender.send(data);
        }
    }

    public static void showActionBar(String text) {
        if (actionBarCallback != null) {
            actionBarCallback.show(text);
        }
    }

    /** 按键是否按下（按住） */
    public static boolean isVoiceKeyHeld() {
        return VOICE_KEY.isPressed();
    }

    /** 按键是否刚按下 */
    public static boolean isVoiceKeyPressed() {
        return VOICE_KEY.wasPressed();
    }

    /** 信道键是否刚按下 */
    public static boolean isChannelKeyPressed() {
        return CHANNEL_KEY.wasPressed();
    }

    private static volatile Runnable channelScreenOpener;

    /** 设置打开信道界面的回调，由 Fabric/Forge 注入 */
    public static void setChannelScreenOpener(Runnable opener) {
        channelScreenOpener = opener;
    }

    /** 尝试打开信道界面（若已注入回调） */
    public static void openChannelScreenIfAvailable() {
        if (channelScreenOpener != null) {
            channelScreenOpener.run();
        }
    }

    private static volatile java.util.function.Consumer<Integer> channelChangeCallback;

    /** 设置信道变更回调（切换信道时向服务端发送），由 Fabric/Forge 注入 */
    public static void setChannelChangeCallback(java.util.function.Consumer<Integer> callback) {
        channelChangeCallback = callback;
    }

    /** 信道变更时调用（GUI 确认后），通知平台向服务端发送 */
    public static void onChannelChanged(int newChannel) {
        if (channelChangeCallback != null) {
            channelChangeCallback.accept(newChannel);
        }
    }

    public static String getConnectSuccessMessage(String serverType) {
        return String.format(MSG_CONNECT_SUCCESS, serverType != null && !serverType.isEmpty() ? serverType : "未知", getVoiceKeyDisplayName());
    }

    /** 握手成功后根据服务端是否启用压缩，返回聊天栏提示文案 */
    public static String getCompressionStatusMessage(boolean useCompressionEncoder) {
        return useCompressionEncoder
                ? "[简单的通讯器]：服务端启用了压缩编码器，这可以减少流量带宽，同时也会带来一些声音问题"
                : "[简单的通讯器]：服务端采用直接转发，这保证了音质，但同时会占用较高的流量带宽";
    }

    /** 握手成功后根据服务端是否启用低延迟，返回聊天栏提示文案 */
    public static String getLowLatencyStatusMessage(boolean lowLatency) {
        return lowLatency
                ? "[简单的通讯器]：服务端启用了低延迟通信，可能会导致声音卡顿，视网络情况而定，如有问题请联系服务器管理员"
                : "[简单的通讯器]：服务端采用原版通信模式。";
    }

    public static String getSpeakingMessage(String username) {
        return String.format(MSG_SPEAKING, username);
    }

    /** 收到某玩家的语音包时调用，用于多人对讲列表 */
    public static void reportSpeaking(String username) {
        if (username == null || username.isEmpty()) return;
        speakingUntil.put(username, System.currentTimeMillis() + SPEAKING_DURATION_MS);
    }

    /** 取当前正在对讲的玩家列表文案，如 "张三，李四，王五... 正在对讲"，无人在讲则返回空字符串 */
    public static String getSpeakingActionBarMessage() {
        long now = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Long> e : speakingUntil.entrySet()) {
            if (e.getValue() > now) names.add(e.getKey());
        }
        speakingUntil.entrySet().removeIf(e -> e.getValue() <= now);
        if (names.isEmpty()) return "";
        int show = Math.min(names.size(), MAX_SPEAKING_NAMES);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < show; i++) {
            if (i > 0) sb.append("，");
            sb.append(names.get(i));
        }
        if (names.size() > MAX_SPEAKING_NAMES) sb.append("...");
        sb.append(MSG_SPEAKING_MULTI_SUFFIX);
        return sb.toString();
    }

    private static String getVoiceKeyDisplayName() {
        return "V";
    }

    @FunctionalInterface
    public interface VoiceDataSender {
        void send(byte[] data);
    }

    @FunctionalInterface
    public interface ActionBarCallback {
        void show(String text);
    }
}
