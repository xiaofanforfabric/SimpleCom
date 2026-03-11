package com.xiaofan.ws;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 6 位数字验证码的生成与验证。
 * 验证码仅在玩家上线期间有效：玩家连接语音成功或退出游戏后立即失效。
 *
 * Generates and validates 6-digit numeric verification codes.
 * Code is valid only while the player is online; it is invalidated as soon as
 * they connect to voice successfully or quit the game.
 */
public final class VerificationCodeStore {

    public enum ValidateResult {
        /** 验证码正确，已消费 | Code matched and consumed */
        OK,
        /** 用户名无记录（未上线、已退出或已连接过语音）| No entry: not online, quit, or already connected */
        NOT_FOUND,
        /** 验证码错误 | Code does not match */
        WRONG_CODE
    }

    private static final VerificationCodeStore INSTANCE = new VerificationCodeStore();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** key: 小写用户名, value: 6位验证码 | lowercase username -> 6-digit code */
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    private VerificationCodeStore() {}

    public static VerificationCodeStore getInstance() {
        return INSTANCE;
    }

    /**
     * 为指定玩家生成（或刷新）一个 6 位验证码。
     * 有效期：玩家上线期间；连接语音成功或退出游戏后由调用方 {@link #remove} / 核验成功时自动移除。
     *
     * Generate (or refresh) a 6-digit code for the given player.
     * Valid until: player connects voice (consumed on success) or quits (plugin calls remove).
     */
    public String generate(String username) {
        if (username == null || username.isEmpty()) return null;
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.put(username.toLowerCase(), code);
        return code;
    }

    /**
     * 核验验证码。核验成功后立即消费（不可复用）。
     * Validate the code. Consumed immediately on success (single-use).
     */
    public ValidateResult validateCode(String username, String code) {
        if (username == null || code == null) return ValidateResult.NOT_FOUND;
        String key = username.toLowerCase();
        String stored = store.get(key);
        if (stored == null) return ValidateResult.NOT_FOUND;
        if (!stored.equals(code.trim())) return ValidateResult.WRONG_CODE;
        store.remove(key);
        return ValidateResult.OK;
    }

    /**
     * 检查该玩家是否还有待使用的验证码。
     * Returns true if the player has a code waiting to be used.
     */
    public boolean hasCode(String username) {
        if (username == null) return false;
        return store.containsKey(username.toLowerCase());
    }

    /**
     * 使验证码立刻失效（玩家退出游戏时由插件调用）。
     * Invalidate the code immediately (called by plugin when player quits).
     */
    public void remove(String username) {
        if (username != null) store.remove(username.toLowerCase());
    }
}
