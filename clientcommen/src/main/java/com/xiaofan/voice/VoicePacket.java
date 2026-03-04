package com.xiaofan.voice;

import java.util.Objects;

/**
 * 语音数据包格式：MC用户名 + 信道名 + 当前包序号/总包数 + 语音数据
 */
public final class VoicePacket {

    public static final String CHANNEL_PLACEHOLDER = "1";
    public static final int CHUNK_SIZE = 30 * 1024; // 30KB

    private final String username;
    private final String channel;
    private final int currentIndex;
    private final int totalCount;
    private final byte[] audioData;

    public VoicePacket(String username, String channel, int currentIndex, int totalCount, byte[] audioData) {
        this.username = username != null ? username : "";
        this.channel = channel != null ? channel : CHANNEL_PLACEHOLDER;
        this.currentIndex = Math.max(0, currentIndex);
        this.totalCount = Math.max(1, totalCount);
        this.audioData = audioData != null ? audioData : new byte[0];
    }

    public String getUsername() {
        return username;
    }

    public String getChannel() {
        return channel;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public boolean isComplete() {
        return currentIndex >= totalCount - 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoicePacket that = (VoicePacket) o;
        return currentIndex == that.currentIndex && totalCount == that.totalCount
                && Objects.equals(username, that.username) && Objects.equals(channel, that.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, channel, currentIndex, totalCount);
    }
}
