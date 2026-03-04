package com.xiaofan.simplecomclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 接收服务端握手 payload，用于判断服务端是否安装 SimpleCom 插件
 */
public final class HandshakePayloadReceiver {

    private static final String PROTOCOL = "1";
    private static final ResourceLocation CHANNEL = new ResourceLocation("simplecom", "handshake");

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            CHANNEL,
            () -> PROTOCOL,
            v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
    );

    private static volatile boolean serverHasPlugin = false;
    private static volatile String serverPluginVersion = "";
    private static volatile String serverPluginName = "";

    private static int packetId = 0;

    public static void register() {
        INSTANCE.registerMessage(
                packetId++,
                HandshakeMessage.class,
                HandshakeMessage::encode,
                HandshakeMessage::decode,
                HandshakeMessage::handle
        );
    }

    public static boolean hasServerPlugin() {
        return serverHasPlugin;
    }

    public static String getServerPluginVersion() {
        return serverPluginVersion;
    }

    public static String getServerPluginName() {
        return serverPluginName;
    }

    public static void reset() {
        serverHasPlugin = false;
        serverPluginVersion = "";
        serverPluginName = "";
    }

    public static class HandshakeMessage {
        private final byte protocolVersion;
        private final String version;
        private final String name;

        public HandshakeMessage(byte protocolVersion, String version, String name) {
            this.protocolVersion = protocolVersion;
            this.version = version;
            this.name = name;
        }

        public static void encode(HandshakeMessage msg, PacketBuffer buf) {
            buf.writeByte(msg.protocolVersion);
            buf.writeUtf(msg.version, 32767);
            buf.writeUtf(msg.name, 32767);
        }

        public static HandshakeMessage decode(PacketBuffer buf) {
            byte protocolVersion = buf.readByte();
            String version = buf.readUtf(32767);
            String name = buf.readUtf(32767);
            return new HandshakeMessage(protocolVersion, version, name);
        }

        public static void handle(HandshakeMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                serverHasPlugin = true;
                serverPluginVersion = msg.version;
                serverPluginName = msg.name;
                ClientPlayerEntity player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendMessage(new StringTextComponent("§a服务器握手成功，开始连接语音服务器"), Util.NIL_UUID);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private HandshakePayloadReceiver() {
    }
}
