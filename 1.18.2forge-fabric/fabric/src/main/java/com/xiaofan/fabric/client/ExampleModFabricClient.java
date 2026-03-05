package com.xiaofan.fabric.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.xiaofan.client.SimpleComVoiceClient;
import com.xiaofan.client.gui.ChannelScreen;
import com.xiaofan.config.SimpleComConfig;
import com.xiaofan.payload.HandshakePayloadAdapter;
import com.xiaofan.voice.VoicePacket;
import com.xiaofan.voice.VoicePacketAssembler;
import com.xiaofan.voice.VoicePacketCodec;
import com.xiaofan.voice.VoiceRecorder;
import com.xiaofan.voice.VoiceSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ExampleModFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("SimpleCom");
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 20;
    private static final String MSG_TIMEOUT = "[简单的通讯器]：连接服务器超时，服务器可能没有安装简单的语音插件，请联系管理员或重进试试";

    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    private static volatile ScheduledFuture<?> handshakeTimeoutTask;

    private static volatile String actionBarText = "";
    private static volatile long actionBarExpire = 0;
    private static final VoiceRecorder recorder = new VoiceRecorder();
    private static final VoicePacketAssembler assembler = new VoicePacketAssembler();

    @Override
    public void onInitializeClient() {
        SimpleComConfig.setConfigDir(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        KeyBindingHelper.registerKeyBinding(SimpleComVoiceClient.VOICE_KEY);
        KeyBindingHelper.registerKeyBinding(SimpleComVoiceClient.CHANNEL_KEY);
        SimpleComVoiceClient.setChannelScreenOpener(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null && mc.player != null) {
                mc.setScreen(new ChannelScreen(mc.currentScreen));
            }
        });
        SimpleComVoiceClient.setChannelChangeCallback(ch -> {
            if (ClientPlayNetworking.canSend(HandshakePayloadAdapter.CHANNEL_SWITCH_CHANNEL)) {
                io.netty.buffer.ByteBuf buf = Unpooled.buffer();
                net.minecraft.network.PacketByteBuf pb = new PacketByteBuf(buf);
                pb.writeVarInt(ch);
                ClientPlayNetworking.send(HandshakePayloadAdapter.CHANNEL_SWITCH_CHANNEL, pb);
            }
        });

        SimpleComVoiceClient.setVoiceDataSender(data -> {
            if (ClientPlayNetworking.canSend(HandshakePayloadAdapter.VOICE_DATA_CHANNEL)) {
                ClientPlayNetworking.send(HandshakePayloadAdapter.VOICE_DATA_CHANNEL, new PacketByteBuf(Unpooled.wrappedBuffer(data)));
            }
        });

        SimpleComVoiceClient.setActionBarCallback(text -> {
            actionBarText = text;
            actionBarExpire = System.currentTimeMillis() + 2000;
        });

        HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
            if (actionBarText.isEmpty() || System.currentTimeMillis() >= actionBarExpire) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null && mc.getWindow() != null && mc.textRenderer != null) {
                int w = mc.getWindow().getScaledWidth();
                int h = mc.getWindow().getScaledHeight();
                int x = (w - mc.textRenderer.getWidth(actionBarText)) / 2;
                int y = h - 59;
                DrawableHelper.drawStringWithShadow(matrices, mc.textRenderer, actionBarText, x, y, 0xFFFFFF);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakePayloadAdapter.HANDSHAKE_CHANNEL, (client, handler, buf, responseSender) -> {
            try {
                HandshakePayloadAdapter.HandshakeData data = HandshakePayloadAdapter.parse(buf);
                client.execute(() -> {
                    cancelHandshakeTimeout();
                    HandshakePayloadAdapter.onHandshakeReceived(data);
                    LOGGER.info("[SimpleCom] 收到服务端握手: {} v{}", data.name, data.version);
                    if (client.player != null) {
                        client.player.sendMessage(new LiteralText(SimpleComVoiceClient.getConnectSuccessMessage(data.serverType)), false);
                        PacketByteBuf ackBuf = new PacketByteBuf(Unpooled.buffer());
                        ackBuf.writeByte(0);
                        ackBuf.writeVarInt(SimpleComConfig.getChannel());
                        ClientPlayNetworking.send(HandshakePayloadAdapter.HANDSHAKE_ACK_CHANNEL, ackBuf);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[SimpleCom] 解析握手 payload 失败", e);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakePayloadAdapter.VOICE_DATA_CHANNEL, (client, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            client.execute(() -> onVoiceDataReceived(data));
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakePayloadAdapter.CHANNEL_SWITCH_ACK_CHANNEL, (client, handler, buf, responseSender) -> {
            int ch = 1;
            if (buf.readableBytes() > 0) {
                try {
                    ch = buf.readVarInt();
                } catch (Exception ignored) {
                }
            }
            int finalCh = ch;
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(new LiteralText("信道已成功切换到" + finalCh), false);
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (SimpleComVoiceClient.isChannelKeyPressed()) {
                SimpleComVoiceClient.openChannelScreenIfAvailable();
                return;
            }
            if (SimpleComConfig.getChannel() == 0) {
                if (recorder.isRecording()) recorder.stop();
                return;
            }
            boolean held = SimpleComVoiceClient.isVoiceKeyHeld();

            if (held && !recorder.isRecording()) {
                recorder.start();
                SimpleComVoiceClient.showActionBar(SimpleComVoiceClient.MSG_RECORDING);
            } else if (held) {
                SimpleComVoiceClient.showActionBar(SimpleComVoiceClient.MSG_RECORDING);
            } else if (!held && recorder.isRecording()) {
                short[] pcm = recorder.stop();
                if (pcm != null && pcm.length > 0) {
                    scheduler.execute(() -> sendVoiceChunks(client.player.getGameProfile().getName(), pcm));
                }
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.info("[SimpleCom] 已连接服务器，等待握手...");
            scheduleHandshakeTimeout(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            cancelHandshakeTimeout();
            assembler.clear();
            if (!HandshakePayloadAdapter.hasServerPlugin()) {
                LOGGER.info("[SimpleCom] 断开连接，未收到服务端握手（可能服务端未安装 SimpleCom 插件）");
            }
            HandshakePayloadAdapter.reset();
        });
    }

    private static void sendVoiceChunks(String username, short[] pcm) {
        if (SimpleComConfig.getChannel() == 0) return;
        try {
            String channel = String.valueOf(SimpleComConfig.getChannel());
            VoiceSession session = new VoiceSession(username, channel);
            List<byte[]> chunks = session.encodeAndChunk(pcm);
            int totalBytes = 0;
            for (byte[] chunk : chunks) {
                SimpleComVoiceClient.sendVoiceData(chunk);
                totalBytes += chunk.length;
            }
            int totalKb = (totalBytes + 512) / 1024;
            SimpleComVoiceClient.showActionBar(String.format(SimpleComVoiceClient.MSG_RECORD_DONE, totalKb));
        } catch (IOException e) {
            LOGGER.warn("[SimpleCom] 编码语音失败", e);
        }
    }

    private static void onVoiceDataReceived(byte[] data) {
        if (data == null || data.length == 0) return;
        if (SimpleComConfig.getChannel() == 0) return;
        try {
            VoicePacket packet = VoicePacketCodec.decode(data);
            if (packet != null) {
                String speaker = assembler.feed(packet);
                if (speaker != null) {
                    SimpleComVoiceClient.showActionBar(SimpleComVoiceClient.getSpeakingMessage(speaker));
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[SimpleCom] 解析语音包失败", e);
        }
    }

    private static void scheduleHandshakeTimeout(MinecraftClient client) {
        cancelHandshakeTimeout();
        handshakeTimeoutTask = scheduler.schedule(() -> {
            client.execute(() -> {
                if (!HandshakePayloadAdapter.hasServerPlugin() && client.player != null) {
                    client.player.sendMessage(new LiteralText(MSG_TIMEOUT), false);
                }
            });
        }, HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void cancelHandshakeTimeout() {
        if (handshakeTimeoutTask != null) {
            handshakeTimeoutTask.cancel(false);
            handshakeTimeoutTask = null;
        }
    }
}
