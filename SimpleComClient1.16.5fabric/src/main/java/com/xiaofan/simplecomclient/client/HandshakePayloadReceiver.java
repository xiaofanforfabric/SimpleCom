package com.xiaofan.simplecomclient.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

/**
 * 接收服务端握手 payload，用于判断服务端是否安装 SimpleCom 插件
 */
public final class HandshakePayloadReceiver {

    /** 通道 ID，需与服务端 SimpleComChannels.HANDSHAKE 一致 */
    public static final Identifier HANDSHAKE_CHANNEL = new Identifier("simplecom", "handshake");

    /** 是否已收到服务端握手（表示服务端已安装插件） */
    private static volatile boolean serverHasPlugin = false;

    /** 服务端插件版本（收到握手后有效） */
    private static volatile String serverPluginVersion = "";

    /** 服务端插件名称（收到握手后有效） */
    private static volatile String serverPluginName = "";

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HANDSHAKE_CHANNEL, (client, handler, buf, responseSender) -> {
            buf.readByte(); // Forge SimpleChannel discriminator
            byte protocolVersion = buf.readByte();
            String version = buf.readString(32767);
            String name = buf.readString(32767);

            client.execute(() -> {
                serverHasPlugin = true;
                serverPluginVersion = version;
                serverPluginName = name;
                if (client.player != null) {
                    client.player.sendMessage(new LiteralText("§a服务器握手成功，开始连接语音服务器"), false);
                }
            });
        });
    }

    /** 服务端是否已安装 SimpleCom 插件 */
    public static boolean hasServerPlugin() {
        return serverHasPlugin;
    }

    /** 获取服务端插件版本 */
    public static String getServerPluginVersion() {
        return serverPluginVersion;
    }

    /** 获取服务端插件名称 */
    public static String getServerPluginName() {
        return serverPluginName;
    }

    /** 断开连接时重置状态 */
    public static void reset() {
        serverHasPlugin = false;
        serverPluginVersion = "";
        serverPluginName = "";
    }

    private HandshakePayloadReceiver() {
    }
}
