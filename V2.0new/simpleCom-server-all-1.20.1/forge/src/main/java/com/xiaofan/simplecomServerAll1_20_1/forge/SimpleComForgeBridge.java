package com.xiaofan.simplecomServerAll1_20_1.forge;

import com.xiaofan.simplecomServerAll1_20_1.SimpleComPlatformBridge;
import com.xiaofan.simplecomServerAll1_20_1.SimpleComServerLogic;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Forge 端平台桥接：配置目录、发消息。
 * 玩家事件由 Forge 主类在 EVENT_BUS 上统一注册，不在此处注册。
 */
public final class SimpleComForgeBridge implements SimpleComPlatformBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("SimpleCom");

    private final MinecraftServer server;

    public SimpleComForgeBridge(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public File getConfigDir() {
        return server.getFile(".");
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
        // Forge 端在 SimplecomServerAll1_20_1Forge 中通过 EVENT_BUS 统一注册，此处无需注册
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
}
