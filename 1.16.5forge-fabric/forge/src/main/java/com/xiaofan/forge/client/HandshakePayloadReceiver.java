package com.xiaofan.forge.client;

import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.event.EventNetworkChannel;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.xiaofan.client.SimpleComVoiceClient;
import com.xiaofan.client.gui.ChannelScreen;
import com.xiaofan.config.SimpleComConfig;
import com.xiaofan.payload.HandshakePayloadAdapter;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Forge 端接收插件 payload：握手、语音数据
 */
public final class HandshakePayloadReceiver {

    private static final Logger LOGGER = LogManager.getLogger("SimpleCom");
    private static final String PROTOCOL = "1";
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 20;
    private static final String MSG_TIMEOUT = "[简单的通讯器]：连接服务器超时，服务器可能没有安装简单的语音插件，请联系管理员或重进试试";

    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    private static volatile ScheduledFuture<?> handshakeTimeoutTask;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            HandshakePayloadAdapter.HANDSHAKE_CHANNEL,
            () -> PROTOCOL,
            v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
    );

    public static final SimpleChannel ACK_CHANNEL = NetworkRegistry.newSimpleChannel(
            HandshakePayloadAdapter.HANDSHAKE_ACK_CHANNEL,
            () -> PROTOCOL,
            v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
    );

    public static final EventNetworkChannel VOICE_CHANNEL = NetworkRegistry.newEventChannel(
            HandshakePayloadAdapter.VOICE_DATA_CHANNEL,
            () -> PROTOCOL,
            v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
    );

    public static final EventNetworkChannel CHANNEL_SWITCH_CHANNEL = NetworkRegistry.newEventChannel(
            HandshakePayloadAdapter.CHANNEL_SWITCH_CHANNEL,
            () -> PROTOCOL,
            v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
    );

    public static final EventNetworkChannel CHANNEL_SWITCH_ACK_CHANNEL = NetworkRegistry.newEventChannel(
            HandshakePayloadAdapter.CHANNEL_SWITCH_ACK_CHANNEL,
            () -> PROTOCOL,
            v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
            v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
    );

    private static int packetId = 0;

    public static void register() {
        SimpleComConfig.setConfigDir(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
        ClientRegistry.registerKeyBinding(SimpleComVoiceClient.VOICE_KEY);
        ClientRegistry.registerKeyBinding(SimpleComVoiceClient.CHANNEL_KEY);
        SimpleComVoiceClient.setChannelScreenOpener(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null && mc.player != null) {
                mc.openScreen(new ChannelScreen(mc.currentScreen));
            }
        });
        SimpleComVoiceClient.setChannelChangeCallback(ch -> {
            net.minecraft.client.network.ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
            if (conn != null) {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeVarInt(ch);
                conn.getConnection().send(new CustomPayloadC2SPacket(HandshakePayloadAdapter.CHANNEL_SWITCH_CHANNEL, buf));
            }
        });

        SimpleComVoiceClient.setVoiceDataSender(data -> {
            if (data != null && data.length > 0) {
                net.minecraft.client.network.ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
                if (conn != null) {
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(data));
                    conn.getConnection().send(new CustomPayloadC2SPacket(HandshakePayloadAdapter.VOICE_DATA_CHANNEL, buf));
                }
            }
        });
        SimpleComVoiceClient.setActionBarCallback(text -> {
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p != null) p.sendMessage(new LiteralText(text), true);
        });

        INSTANCE.registerMessage(
                packetId++,
                HandshakeMessage.class,
                HandshakeMessage::encode,
                HandshakeMessage::decode,
                HandshakeMessage::handle
        );
        ACK_CHANNEL.registerMessage(
                packetId++,
                HandshakeAckMessage.class,
                (msg, buf) -> buf.writeVarInt(msg.channel),
                buf -> new HandshakeAckMessage(buf.readVarInt()),
                (msg, ctx) -> ctx.get().setPacketHandled(true)
        );
        VOICE_CHANNEL.addListener(event -> {
            PacketByteBuf buf = event != null ? event.getPayload() : null;
            if (buf == null) return;
            NetworkEvent.Context ctx = event.getSource() != null ? event.getSource().get() : null;
            if (ctx == null) return;
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            ctx.enqueueWork(() -> onVoiceDataReceived(data));
            ctx.setPacketHandled(true);
        });
        CHANNEL_SWITCH_ACK_CHANNEL.addListener(event -> {
            if (event == null) return;
            PacketByteBuf buf = event.getPayload();
            if (buf == null) return;
            NetworkEvent.Context ctx = event.getSource() != null ? event.getSource().get() : null;
            if (ctx == null) return;
            int ch = 1;
            if (buf.readableBytes() > 0) {
                try {
                    ch = buf.readVarInt();
                } catch (Exception ignored) {
                }
            }
            int finalCh = ch;
            ctx.enqueueWork(() -> {
                ClientPlayerEntity p = MinecraftClient.getInstance().player;
                if (p != null) {
                    p.sendMessage(new LiteralText("信道已成功切换到" + finalCh), false);
                }
            });
            ctx.setPacketHandled(true);
        });
    }

    public static class HandshakeAckMessage {
        public final int channel;
        public HandshakeAckMessage(int channel) { this.channel = channel; }
    }

    public static class HandshakeMessage {
        private final byte protocolVersion;
        private final String version;
        private final String name;
        private final String serverType;
        private final boolean useCompressionEncoder;
        private final boolean lowLatency;

        public HandshakeMessage(byte protocolVersion, String version, String name, String serverType, boolean useCompressionEncoder, boolean lowLatency) {
            this.protocolVersion = protocolVersion;
            this.version = version;
            this.name = name;
            this.serverType = serverType;
            this.useCompressionEncoder = useCompressionEncoder;
            this.lowLatency = lowLatency;
        }

        public static void encode(HandshakeMessage msg, PacketByteBuf buf) {
            buf.writeByte(msg.protocolVersion);
            buf.writeString(msg.version, 32767);
            buf.writeString(msg.name, 32767);
            buf.writeString(msg.serverType != null ? msg.serverType : "", 32767);
        }

        public static HandshakeMessage decode(PacketByteBuf buf) {
            HandshakePayloadAdapter.HandshakeData data = HandshakePayloadAdapter.parseFromProtocol(buf);
            return new HandshakeMessage(data.protocolVersion, data.version, data.name, data.serverType, data.useCompressionEncoder, data.lowLatency);
        }

        public static void handle(HandshakeMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx != null ? ctx.get() : null;
            if (context == null) return;
            context.enqueueWork(() -> {
                try {
                    cancelHandshakeTimeout();
                    HandshakePayloadAdapter.onHandshakeReceived(
                            new HandshakePayloadAdapter.HandshakeData(msg.protocolVersion, msg.version, msg.name, msg.serverType, msg.useCompressionEncoder, msg.lowLatency));
                    forgeAssembler.setUseCompressionEncoder(msg.useCompressionEncoder);
                    forgeAssembler.setLowLatency(msg.lowLatency);
                    LOGGER.info("[SimpleCom] 收到服务端握手: {} v{} (compression={}, lowLatency={})", msg.name, msg.version, msg.useCompressionEncoder, msg.lowLatency);
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(new LiteralText(SimpleComVoiceClient.getConnectSuccessMessage(msg.serverType)), false);
                        player.sendMessage(new LiteralText(SimpleComVoiceClient.getCompressionStatusMessage(msg.useCompressionEncoder)), false);
                        player.sendMessage(new LiteralText(SimpleComVoiceClient.getLowLatencyStatusMessage(msg.lowLatency)), false);
                        ACK_CHANNEL.sendToServer(new HandshakeAckMessage(SimpleComConfig.getChannel()));
                    }
                } catch (Exception e) {
                    LOGGER.error("[SimpleCom] 处理握手失败", e);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private static final com.xiaofan.voice.VoiceRecorder forgeRecorder = new com.xiaofan.voice.VoiceRecorder();
    private static final com.xiaofan.voice.VoicePacketAssembler forgeAssembler = new com.xiaofan.voice.VoicePacketAssembler();
    private static final int LOW_LATENCY_DRAIN_MS = 40;
    private static long lastLowLatencyDrainTime = 0;

    public static void tickVoiceKey() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (SimpleComVoiceClient.isChannelKeyPressed()) {
            SimpleComVoiceClient.openChannelScreenIfAvailable();
            return;
        }
        if (SimpleComConfig.getChannel() == 0) {
            if (forgeRecorder.isRecording()) forgeRecorder.stop();
            return;
        }
        boolean held = SimpleComVoiceClient.isVoiceKeyHeld();

        if (held && !forgeRecorder.isRecording()) {
            forgeRecorder.start();
            lastLowLatencyDrainTime = System.currentTimeMillis();
            SimpleComVoiceClient.showActionBar(SimpleComVoiceClient.MSG_RECORDING);
        } else if (held) {
            SimpleComVoiceClient.showActionBar(SimpleComVoiceClient.MSG_RECORDING);
            if (com.xiaofan.payload.HandshakePayloadAdapter.lowLatency()) {
                long now = System.currentTimeMillis();
                if (now - lastLowLatencyDrainTime >= LOW_LATENCY_DRAIN_MS) {
                    lastLowLatencyDrainTime = now;
                    short[] pcm = forgeRecorder.drainRecorded();
                    if (pcm != null && pcm.length > 0) {
                        net.minecraft.client.MinecraftClient client = mc;
                        String name = client.player.getGameProfile().getName();
                        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> sendVoiceChunks(client, name, pcm, false));
                    }
                }
            }
        } else if (!held && forgeRecorder.isRecording()) {
            short[] pcm = forgeRecorder.stop();
            if (pcm != null && pcm.length > 0) {
                net.minecraft.client.MinecraftClient client = mc;
                java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> sendVoiceChunks(client, client.player.getGameProfile().getName(), pcm, true));
            }
        } else {
            String speakingMsg = SimpleComVoiceClient.getSpeakingActionBarMessage();
            if (speakingMsg != null && !speakingMsg.isEmpty()) {
                SimpleComVoiceClient.showActionBar(speakingMsg);
            }
        }
    }

    private static void sendVoiceChunks(net.minecraft.client.MinecraftClient client, String username, short[] pcm, boolean showDoneMessage) {
        if (SimpleComConfig.getChannel() == 0) return;
        try {
            String channel = String.valueOf(SimpleComConfig.getChannel());
            boolean useCompression = HandshakePayloadAdapter.useCompressionEncoder();
            boolean lowLatency = HandshakePayloadAdapter.lowLatency();
            com.xiaofan.voice.VoiceSession session = new com.xiaofan.voice.VoiceSession(username, channel, useCompression, lowLatency);
            java.util.List<byte[]> chunks = session.encodeAndChunk(pcm);
            int totalBytes = 0;
            for (byte[] chunk : chunks) {
                SimpleComVoiceClient.sendVoiceData(chunk);
                totalBytes += chunk.length;
            }
            if (showDoneMessage) {
                int totalKb = (totalBytes + 512) / 1024;
                String msg = String.format(SimpleComVoiceClient.MSG_RECORD_DONE, totalKb);
                client.execute(() -> SimpleComVoiceClient.showActionBar(msg));
            }
        } catch (java.io.IOException e) {
            LOGGER.warn("[SimpleCom] 编码语音失败", e);
        }
    }

    private static void onVoiceDataReceived(byte[] data) {
        if (data == null || data.length == 0) return;
        if (SimpleComConfig.getChannel() == 0) return;
        try {
            com.xiaofan.voice.VoicePacket packet = com.xiaofan.voice.VoicePacketCodec.decode(data);
            if (packet != null) {
                String speaker = forgeAssembler.feed(packet);
                if (speaker != null) {
                    SimpleComVoiceClient.reportSpeaking(speaker);
                }
            }
        } catch (java.io.IOException e) {
            LOGGER.warn("[SimpleCom] 解析语音包失败", e);
        }
    }

    public static void scheduleHandshakeTimeout() {
        cancelHandshakeTimeout();
        handshakeTimeoutTask = scheduler.schedule(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (!HandshakePayloadAdapter.hasServerPlugin() && client.player != null) {
                    client.player.sendMessage(new LiteralText(MSG_TIMEOUT), false);
                }
            });
        }, HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static void clearAssembler() {
        forgeAssembler.clear();
    }

    public static void cancelHandshakeTimeout() {
        if (handshakeTimeoutTask != null) {
            handshakeTimeoutTask.cancel(false);
            handshakeTimeoutTask = null;
        }
    }
}
