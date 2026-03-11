package com.xiaofan.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * 简单国际化工具，支持英文与中文。
 * Simple i18n utility supporting English and Chinese.
 */
public final class I18n {

    public enum Language {
        ENGLISH("English"),
        CHINESE("中文");

        public final String displayName;

        Language(String name) { this.displayName = name; }
    }

    private static Language current = Language.CHINESE;

    /** [0] = English, [1] = Chinese */
    private static final Map<String, String[]> STRINGS = new HashMap<>();

    static {
        STRINGS.put("app.title",          new String[]{"SimpleCom Voice",                             "SimpleCom 语音"});
        STRINGS.put("label.language",     new String[]{"Language",                                     "语言"});
        STRINGS.put("label.username",     new String[]{"Minecraft Username",                           "Minecraft 用户名"});
        STRINGS.put("label.server",       new String[]{"Server Address",                              "服务器地址"});
        STRINGS.put("hint.server",        new String[]{"e.g. play.example.com:25566",                 "例如 play.example.com:25566"});
        STRINGS.put("btn.connect",        new String[]{"Connect",                                      "连接"});
        STRINGS.put("btn.disconnect",     new String[]{"Disconnect",                                   "断开连接"});
        STRINGS.put("status.connecting",  new String[]{"Connecting...",                               "连接中..."});
        STRINGS.put("status.validating",  new String[]{"Validating identity...",                      "验证身份中..."});
        STRINGS.put("label.code",         new String[]{"Verification Code",                           "验证码"});
        STRINGS.put("hint.code",          new String[]{"6-digit code from Minecraft chat",            "Minecraft 聊天框中的 6 位数字"});
        STRINGS.put("err.username_empty", new String[]{"Please enter your Minecraft username.",       "请输入 Minecraft 用户名。"});
        STRINGS.put("err.server_empty",   new String[]{"Please enter the server address.",            "请输入服务器地址。"});
        STRINGS.put("err.code_empty",     new String[]{"Please enter the verification code.\n(Check your Minecraft chat)",
                                                        "请输入验证码。\n（查看 Minecraft 聊天框）"});
        STRINGS.put("err.not_found",      new String[]{"Code not found or expired.\nJoin the MC server and check chat for a new code.",
                                                        "验证码不存在或已过期。\n请进入 MC 服务器，从聊天框获取新验证码。"});
        STRINGS.put("err.code_wrong",     new String[]{"Wrong verification code.\nCheck Minecraft chat for your code.",
                                                        "验证码错误。\n请在 Minecraft 聊天框中确认验证码。"});
        STRINGS.put("err.connect_failed", new String[]{"Connection failed: ",                        "连接失败："});
        STRINGS.put("err.connection_closed", new String[]{"Connection closed by server.\n(e.g. you left the game)", "连接已断开：服务器主动关闭。\n（例如你退出了游戏）"});
        STRINGS.put("err.invalid_address",new String[]{"Invalid server address format.",              "服务器地址格式无效。"});
        STRINGS.put("label.ptt_key",      new String[]{"Push-to-talk key",                            "按键说话"});
        STRINGS.put("btn.change_key",     new String[]{"Change key",                                  "更换按键"});
        STRINGS.put("hint.press_key",     new String[]{"Press any key...",                            "按下任意键..."});
        STRINGS.put("connected.title",    new String[]{"SimpleCom — Connected",                      "SimpleCom — 已连接"});
        STRINGS.put("connected.msg",      new String[]{"Voice Connected!",                           "语音已连接！"});
        STRINGS.put("connected.server",   new String[]{"Server: ",                                   "服务器："});
        STRINGS.put("connected.user",     new String[]{"User: ",                                     "用户："});
        STRINGS.put("connected.note",     new String[]{"Keep this window open to stay connected.",   "保持此窗口开启以维持连接。"});
        STRINGS.put("status.recording",   new String[]{"● Recording...",                               "● 录音中..."});
        STRINGS.put("status.latency",      new String[]{"Latency: ",                                   "延迟："});
        STRINGS.put("status.latency_na",   new String[]{"Latency: --",                                 "延迟：--"});
        STRINGS.put("label.tail_sound",    new String[]{"Tail sound",                                  "尾音"});
        STRINGS.put("tail_sound.on",       new String[]{"On",                                          "开"});
        STRINGS.put("tail_sound.off",      new String[]{"Off",                                         "关"});
        STRINGS.put("label.online_users",  new String[]{"Online",                                      "在线用户"});
        STRINGS.put("online_users.empty",  new String[]{"(no other users)",                            "（暂无其他用户）"});
        STRINGS.put("online_users.hint",   new String[]{"Right-click to block/unblock",                "右键点击屏蔽/取消屏蔽"});
    }

    private I18n() {}

    public static void setLanguage(Language lang) {
        if (lang != null) current = lang;
    }

    public static Language getLanguage() {
        return current;
    }

    public static String get(String key) {
        String[] arr = STRINGS.get(key);
        if (arr == null) return key;
        return arr[current == Language.CHINESE ? 1 : 0];
    }
}
