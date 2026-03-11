package com.xiaofan.ws;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加密信道管理器（内存存储）。
 * Encrypted channel manager (in-memory storage).
 * 
 * 功能：
 * - 创建加密信道（名称 + 密码哈希）
 * - 验证密码并分配临时信道（>100）
 * - 当信道无用户时自动销毁
 */
public final class EncryptedChannelManager {

    private static final EncryptedChannelManager INSTANCE = new EncryptedChannelManager();
    
    /** 下一个可用的临时信道号（从 101 开始）*/
    private final AtomicInteger nextTempChannel = new AtomicInteger(101);
    
    /** 加密信道信息：信道名称（小写）→ EncryptedChannel */
    private final ConcurrentHashMap<String, EncryptedChannel> channels = new ConcurrentHashMap<>();
    
    /** 临时信道号 → 信道名称（用于反向查找）*/
    private final ConcurrentHashMap<Integer, String> tempChannelToName = new ConcurrentHashMap<>();

    private EncryptedChannelManager() {}

    public static EncryptedChannelManager getInstance() {
        return INSTANCE;
    }

    /**
     * 创建加密信道。
     * Create encrypted channel.
     *
     * @param name         信道名称（支持中英文和特殊字符）
     * @param passwordHash 密码 SHA-256 哈希
     * @return 分配的临时信道号，失败返回 -1
     */
    public int createChannel(String name, String passwordHash) {
        if (name == null || name.trim().isEmpty()) return -1;
        if (passwordHash == null || passwordHash.isEmpty()) return -1;
        
        String key = name.trim().toLowerCase();
        
        // 检查是否已存在
        if (channels.containsKey(key)) {
            System.out.println("[SimpleCom] 加密信道已存在: " + name);
            return -1;
        }
        
        // 分配临时信道号
        int tempChannel = nextTempChannel.getAndIncrement();
        
        // 创建信道
        EncryptedChannel channel = new EncryptedChannel(name.trim(), passwordHash, tempChannel);
        channels.put(key, channel);
        tempChannelToName.put(tempChannel, key);
        
        System.out.println("[SimpleCom] 创建加密信道: " + name + " (临时信道: " + tempChannel + ")");
        return tempChannel;
    }

    /**
     * 验证密码并获取临时信道号。
     * Verify password and get temp channel number.
     *
     * @param name         信道名称
     * @param passwordHash 密码 SHA-256 哈希
     * @return 临时信道号，验证失败返回 -1
     */
    public int verifyAndGetChannel(String name, String passwordHash) {
        if (name == null || name.trim().isEmpty()) return -1;
        if (passwordHash == null || passwordHash.isEmpty()) return -1;
        
        String key = name.trim().toLowerCase();
        EncryptedChannel channel = channels.get(key);
        
        if (channel == null) {
            System.out.println("[SimpleCom] 加密信道不存在: " + name);
            return -1;
        }
        
        if (!channel.passwordHash.equals(passwordHash)) {
            System.out.println("[SimpleCom] 加密信道密码错误: " + name);
            return -1;
        }
        
        System.out.println("[SimpleCom] 加密信道验证成功: " + name + " (临时信道: " + channel.tempChannel + ")");
        return channel.tempChannel;
    }

    /**
     * 获取所有加密信道名称列表。
     * Get all encrypted channel names.
     *
     * @return 信道名称数组
     */
    public String[] getAllChannelNames() {
        return channels.values().stream()
                .map(c -> c.name)
                .toArray(String[]::new);
    }

    /**
     * 检查并清理无用户的加密信道。
     * Check and cleanup encrypted channels with no users.
     *
     * @param tempChannel 临时信道号
     */
    public void checkAndCleanup(int tempChannel) {
        if (tempChannel <= 100) return;
        
        // 检查该信道是否还有用户
        WsConnectionRegistry registry = WsConnectionRegistry.getInstance();
        java.util.List<String> users = registry.getUsersInChannel(tempChannel);
        
        if (users.isEmpty()) {
            // 无用户，销毁信道
            String channelName = tempChannelToName.remove(tempChannel);
            if (channelName != null) {
                EncryptedChannel removed = channels.remove(channelName);
                if (removed != null) {
                    System.out.println("[SimpleCom] 销毁加密信道（无用户）: " + removed.name + " (临时信道: " + tempChannel + ")");
                }
            }
        }
    }

    /**
     * 加密信道信息。
     * Encrypted channel info.
     */
    private static final class EncryptedChannel {
        final String name;
        final String passwordHash;
        final int tempChannel;

        EncryptedChannel(String name, String passwordHash, int tempChannel) {
            this.name = name;
            this.passwordHash = passwordHash;
            this.tempChannel = tempChannel;
        }
    }
}
