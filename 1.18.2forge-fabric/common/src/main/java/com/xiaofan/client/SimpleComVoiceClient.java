package com.xiaofan.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * 语音对讲：按键按住录音，松开发送
 * 物品栏上方显示：正在录音 / 正在对讲
 */
@Environment(EnvType.CLIENT)
public final class SimpleComVoiceClient {

    public static final String MSG_RECORDING = "正在录音...";
    public static final String MSG_RECORD_DONE = "录音完成，共%dKB";
    public static final String MSG_SPEAKING = "%s正在对讲";

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

    public static String getSpeakingMessage(String username) {
        return String.format(MSG_SPEAKING, username);
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
