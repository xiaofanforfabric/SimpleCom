package com.xiaofan.ws;

/**
 * WebSocket 身份验证结果。
 * WebSocket authentication result.
 */
public enum WsAuthResult {

    /**
     * 验证通过，允许连接。
     * Authentication passed; connection allowed.
     */
    OK,

    /**
     * 该玩家无待验证的验证码（未进入 MC 服务器，或验证码已过期）。
     * Player has no pending code (not logged into MC, or code expired). HTTP equivalent: 404.
     */
    NOT_FOUND,

    /**
     * 验证码不匹配。
     * Verification code does not match. HTTP equivalent: 403.
     */
    CODE_MISMATCH
}
