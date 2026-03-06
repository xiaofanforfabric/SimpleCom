package com.xiaofan.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 密码哈希：SHA-256 后转十六进制字符串，与服务端一致 */
public final class PasswordHash {

    private static final String ALG = "SHA-256";

    private PasswordHash() {
    }

    public static String hash(String password) {
        if (password == null) password = "";
        try {
            MessageDigest md = MessageDigest.getInstance(ALG);
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(ALG + " not available", e);
        }
    }
}
