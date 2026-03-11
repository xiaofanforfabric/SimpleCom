package com.xiaofan.ws;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已认证 WebSocket 连接注册表：用户名（小写）→ WsSender。
 * 玩家退出 MC 时，可据此主动关闭其 WS 连接。
 *
 * Registry of authenticated WS connections: lowercase username → WsSender.
 * When a player quits MC, call closeForUser() to forcefully close their WS connection.
 */
public final class WsConnectionRegistry {

    /**
     * 平台提供的玩家在线检查器。
     * 由各平台（Bukkit/Fabric/Forge）在启动时注册，供核心模块查询玩家是否仍在 MC 服务器上。
     * Platform-provided player online checker.
     * Registered by each platform (Bukkit/Fabric/Forge) at startup.
     */
    public interface PlayerOnlineChecker {
        /** 返回该玩家是否当前在线（大小写不敏感） */
        boolean isPlayerOnline(String username);
    }

    /**
     * WS 断线后的重连通知器：若玩家仍在线，生成新验证码并通过 MC 聊天发送。
     * Reconnect notifier: if player is still online after WS disconnect, generate new code and send via MC chat.
     */
    public interface ReconnectNotifier {
        /**
         * 向指定玩家发送新验证码消息。
         * @param username 玩家名
         * @param newCode  新生成的验证码
         */
        void sendReconnectCode(String username, String newCode);
    }

    private static final WsConnectionRegistry INSTANCE = new WsConnectionRegistry();

    /** key: 小写用户名 | lowercase username */
    private final ConcurrentHashMap<String, WsSender> usernameToSender = new ConcurrentHashMap<>();
    
    /** key: 小写用户名 → 信道（0-100）| username → channel */
    private final ConcurrentHashMap<String, Integer> usernameToChannel = new ConcurrentHashMap<>();

    /** 平台注册的玩家在线检查器（可为 null，未注册时跳过重连通知） */
    private volatile PlayerOnlineChecker playerOnlineChecker;

    /** 平台注册的重连通知器（可为 null） */
    private volatile ReconnectNotifier reconnectNotifier;

    private WsConnectionRegistry() {}

    public static WsConnectionRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册平台提供的玩家在线检查器。
     * Register the platform-provided player online checker.
     */
    public void setPlayerOnlineChecker(PlayerOnlineChecker checker) {
        this.playerOnlineChecker = checker;
    }

    /**
     * 注册平台提供的重连通知器。
     * Register the platform-provided reconnect notifier.
     */
    public void setReconnectNotifier(ReconnectNotifier notifier) {
        this.reconnectNotifier = notifier;
    }

    /**
     * 查询玩家是否在 MC 服务器上在线（需平台已注册 PlayerOnlineChecker）。
     * Check if a player is online in MC (requires platform to have registered a PlayerOnlineChecker).
     *
     * @return true=在线，false=不在线或未注册检查器
     */
    public boolean isPlayerOnline(String username) {
        if (username == null || username.isEmpty()) return false;
        PlayerOnlineChecker checker = this.playerOnlineChecker;
        return checker != null && checker.isPlayerOnline(username);
    }

    /** 认证成功后注册 | Register after successful authentication */
    public void register(String username, WsSender sender) {
        if (username != null && !username.isEmpty() && sender != null) {
            usernameToSender.put(username.toLowerCase(), sender);
            usernameToChannel.put(username.toLowerCase(), 1); // 默认信道 1
        }
    }

    /**
     * 设置用户信道。
     * Set channel for user.
     *
     * @param username 用户名（小写）
     * @param channel  信道（0-100 为普通信道，>100 为加密信道）
     */
    public void setChannel(String username, int channel) {
        if (username == null || username.isEmpty()) return;
        usernameToChannel.put(username.toLowerCase(), Math.max(0, channel));
    }

    /**
     * 获取用户当前信道。
     * Get channel for user.
     *
     * @param username 用户名（小写）
     * @return 信道（0-100），未注册时返回 1
     */
    public int getChannel(String username) {
        if (username == null || username.isEmpty()) return 1;
        return usernameToChannel.getOrDefault(username.toLowerCase(), 1);
    }

    /**
     * 获取指定信道的所有用户名。
     * Get all usernames in the given channel.
     *
     * @param channel 信道（0-100）
     * @return 该信道的用户名列表
     */
    public java.util.List<String> getUsersInChannel(int channel) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (Map.Entry<String, Integer> entry : usernameToChannel.entrySet()) {
            if (entry.getValue() == channel) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 连接关闭时注销，并检查玩家是否仍在 MC 服务器上。
     * 若玩家仍在线（WS 超时/异常断线），自动生成新验证码并通过聊天栏通知玩家重连。
     *
     * Unregister when connection closes. If the player is still online in MC
     * (WS timeout/error disconnect), generate a new code and notify via chat.
     *
     * @param username        玩家名（原始大小写）
     * @param playerQuitMc    true 表示玩家主动退出 MC（此时不需要重发验证码）
     */
    public void unregister(String username, boolean playerQuitMc) {
        if (username == null || username.isEmpty()) return;
        usernameToSender.remove(username.toLowerCase());
        usernameToChannel.remove(username.toLowerCase());

        if (!playerQuitMc) {
            // WS 异常/超时断线：若玩家仍在线则重发验证码
            tryNotifyReconnect(username);
        }
    }

    /** 连接正常关闭时注销（不关闭 socket）— 兼容旧调用，默认视为 WS 断线（非玩家退出）*/
    public void unregister(String username) {
        unregister(username, false);
    }

    /**
     * 根据用户名获取 WsSender（大小写不敏感）。
     * Get the WsSender for the given username (case-insensitive).
     *
     * @return WsSender，未连接则为 null | WsSender, or null if not connected
     */
    public WsSender getSender(String username) {
        if (username == null || username.isEmpty()) return null;
        return usernameToSender.get(username.toLowerCase());
    }

    /** 检查用户是否已连接 WS | Check whether a user has an active WS connection */
    public boolean isConnected(String username) {
        if (username == null || username.isEmpty()) return false;
        WsSender s = usernameToSender.get(username.toLowerCase());
        return s != null && s.isOpen();
    }

    /**
     * 获取所有已认证连接的快照（用于广播）。
     * Get a snapshot of all authenticated connections (for broadcasting).
     *
     * @return 用户名（小写）→ WsSender 的不可变视图
     */
    public Map<String, WsSender> getAllSenders() {
        return java.util.Collections.unmodifiableMap(usernameToSender);
    }

    /**
     * 玩家退出时调用：主动关闭其 WS socket 并注销（不触发重连通知）。
     * Called on player quit: close their WS socket and unregister (no reconnect notification).
     */
    public void closeForUser(String username) {
        if (username == null || username.isEmpty()) return;
        WsSender sender = usernameToSender.remove(username.toLowerCase());
        usernameToChannel.remove(username.toLowerCase());
        if (sender != null) sender.closeSocket();
        // 玩家主动退出，不需要重发验证码
    }

    /**
     * 若玩家仍在 MC 服务器上，生成新验证码并通过重连通知器发送。
     * If the player is still online in MC, generate a new code and send via reconnect notifier.
     */
    private void tryNotifyReconnect(String username) {
        PlayerOnlineChecker checker = this.playerOnlineChecker;
        ReconnectNotifier notifier = this.reconnectNotifier;
        if (checker == null || notifier == null) return;

        // 在后台线程执行，避免阻塞帧循环
        Thread t = new Thread(() -> {
            try {
                // 稍等 500ms，给 MC 服务端时间处理可能的退出事件
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!checker.isPlayerOnline(username)) return;

            // 玩家仍在线，生成新验证码
            String newCode = VerificationCodeStore.getInstance().generate(username);
            if (newCode == null) return;

            System.out.println("[SimpleCom] WS 连接断开但玩家仍在线，已重新生成验证码 | WS disconnected but player still online, new code generated: " + username);
            notifier.sendReconnectCode(username, newCode);
        }, "SimpleCom-ReconnectNotify");
        t.setDaemon(true);
        t.start();
    }
}
