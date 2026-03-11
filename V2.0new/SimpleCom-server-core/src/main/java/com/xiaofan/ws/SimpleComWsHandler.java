package com.xiaofan.ws;

/**
 * 服务端 WebSocket 认证处理器（核心模块，供代理调用）。
 * Server-side WebSocket auth handler (core module, called by the proxy).
 *
 * 认证逻辑 | Auth logic:
 * 1. 客户端连接后发送 {"username":"xxx","code":"123456"}
 * 2. 服务端查询 VerificationCodeStore，核验验证码
 *    - 无记录 / 已过期 → NOT_FOUND  (close code 4004)
 *    - 验证码错误      → CODE_MISMATCH (close code 4003)
 *    - 验证码正确      → OK，验证码立即消费（不可复用）
 */
public final class SimpleComWsHandler {

    private SimpleComWsHandler() {}

    /**
     * 验证 WebSocket 连接的合法性（基于验证码）。
     * Validate a WebSocket connection using verification code.
     *
     * @param username  客户端提供的 MC 用户名 | MC username from the WS client
     * @param code      客户端提交的 6 位验证码 | 6-digit code submitted by the client
     * @return 验证结果 | Auth result
     */
    public static WsAuthResult authenticate(String username, String code) {
        if (username == null || username.isEmpty()) return WsAuthResult.NOT_FOUND;
        if (code == null || code.isEmpty()) return WsAuthResult.CODE_MISMATCH;

        VerificationCodeStore.ValidateResult r =
                VerificationCodeStore.getInstance().validateCode(username, code);
        switch (r) {
            case NOT_FOUND:   return WsAuthResult.NOT_FOUND;
            case WRONG_CODE:  return WsAuthResult.CODE_MISMATCH;
            default:          return WsAuthResult.OK;
        }
    }

    /**
     * 从 WS 文本帧中解析 "username" 字段（轻量 JSON 解析）。
     * Parse the "username" field from WS text frame (lightweight JSON parsing).
     *
     * @param text 帧内容，期望格式 {"username":"xxx","code":"nnnnnn"}
     * @return 用户名，解析失败返回 null
     */
    public static String parseUsername(String text) {
        return parseStringField(text, "username");
    }

    /**
     * 从 WS 文本帧中解析 "code" 字段（轻量 JSON 解析）。
     * Parse the "code" field from WS text frame (lightweight JSON parsing).
     *
     * @param text 帧内容，期望格式 {"username":"xxx","code":"nnnnnn"}
     * @return 验证码字符串，解析失败返回 null
     */
    public static String parseCode(String text) {
        return parseStringField(text, "code");
    }

    // ─── internal ─────────────────────────────────────────────────────────────

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
}
