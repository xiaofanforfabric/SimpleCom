package com.xiaofan.simplecomServerAll1_20_1;

/**
 * SimpleCom 服务端模组入口（移植自 Bukkit 插件）。
 * 仅在服务端启动时由平台调用 init(bridge)，并持有 logic 以便在服务端关闭时调用 stop()。
 */
public final class SimplecomServerAll1_20_1 {
    public static final String MOD_ID = "simplecom_server_all_1_20_1";

    /**
     * 在服务端启动时由 Fabric/Forge 调用。
     * 延迟约 2 秒后启动代理与 WebSocket，并注册玩家事件监听。
     *
     * @param bridge 平台桥接（提供配置目录、发消息、注册事件、日志）
     * @return 服务端逻辑实例，用于在服务端关闭时调用 {@link SimpleComServerLogic#stop()}
     */
    public static SimpleComServerLogic initServer(SimpleComPlatformBridge bridge) {
        SimpleComServerLogic logic = new SimpleComServerLogic(bridge);
        bridge.registerListeners(logic);

        // 延迟 2 秒启动，确保 MC 服务端 25565 已绑定
        Thread starter = new Thread(() -> {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            logic.start();
        }, "SimpleCom-ProxyStarter");
        starter.setDaemon(false);
        starter.start();

        return logic;
    }
}
