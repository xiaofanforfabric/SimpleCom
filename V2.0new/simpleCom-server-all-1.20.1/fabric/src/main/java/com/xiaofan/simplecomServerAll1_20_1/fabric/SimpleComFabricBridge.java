package com.xiaofan.simplecomServerAll1_20_1.fabric;

import com.xiaofan.simplecomServerAll1_20_1.SimpleComPlatformBridge;
import com.xiaofan.simplecomServerAll1_20_1.SimpleComServerLogic;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

/**
 * Fabric 端平台桥接：配置目录、发消息、注册玩家事件。
 */
public final class SimpleComFabricBridge implements SimpleComPlatformBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("SimpleCom");

    private final MinecraftServer server;

    public SimpleComFabricBridge(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public File getConfigDir() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    @Override
    public void sendMessageToPlayer(String playerName, List<String> lines) {
        if (server == null || lines == null) return;
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) return;
        for (String line : lines) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    @Override
    public void registerListeners(SimpleComServerLogic logic) {
        // 连接初始化时检查是否来自代理，否则踢出
        // Mojang 映射下 ServerGamePacketListenerImpl.connection 是 private，
        // 但 handler.player 是 public，ServerPlayer.connection 也是 public，通过 player.connection 访问
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            String address = handler.player != null
                    ? getAddressString(handler.player.connection.getRemoteAddress())
                    : "";
            Optional<String> kick = logic.onPlayerLogin(address);
            if (kick.isPresent()) {
                handler.disconnect(Component.literal(kick.get()));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player == null) return;
            String name = player.getName().getString();
            // 直接用事件里的 player 对象发消息，避免按名字查找的时序问题
            String code = logic.generateCodeForPlayer(name);
            if (code == null) return;
            for (String line : SimpleComServerLogic.buildCodeMessage(code)) {
                player.sendSystemMessage(Component.literal(line));
            }
            LOGGER.info("[SimpleCom] 已为 {} 生成语音验证码 | Voice code generated for: {}", name, name);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() != null) {
                logic.onPlayerQuit(handler.getPlayer().getName().getString());
            }
        });
    }

    @Override
    public boolean isPlayerOnline(String playerName) {
        if (server == null || playerName == null) return false;
        return server.getPlayerList().getPlayerByName(playerName) != null;
    }

    @Override
    public void logInfo(String message) {
        LOGGER.info("[SimpleCom] {}", message);
    }

    @Override
    public void logWarning(String message) {
        LOGGER.warn("[SimpleCom] {}", message);
    }

    private static String getAddressString(java.net.SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress().getHostAddress();
        }
        return address != null ? address.toString() : "";
    }
}
