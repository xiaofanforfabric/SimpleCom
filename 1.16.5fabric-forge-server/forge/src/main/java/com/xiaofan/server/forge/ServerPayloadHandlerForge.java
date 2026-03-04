package com.xiaofan.server.forge;

import com.xiaofan.server.payload.SimpleComChannels;
import com.xiaofan.server.payload.ServerPayloadHandler;
import com.xiaofan.server.payload.VarIntUtil;
import com.xiaofan.server.payload.VoicePacketHeaderParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.event.EventNetworkChannel;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Forge 服务端 payload 处理
 */
public final class ServerPayloadHandlerForge extends ServerPayloadHandler {

    private static final Logger LOGGER = LogManager.getLogger("SimpleCom-Server");
    private static final String PROTOCOL = "1";
    private final Map<UUID, int[]> handshakeTasks = new HashMap<>();

    public void register() {
        // 服务端发送的通道也需注册，否则 Forge 连接握手会因版本校验失败而断开
        NetworkRegistry.newEventChannel(
                SimpleComChannels.HANDSHAKE,
                () -> PROTOCOL,
                v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
                v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
        );
        NetworkRegistry.newEventChannel(
                SimpleComChannels.CHANNEL_SWITCH_ACK,
                () -> PROTOCOL,
                v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
                v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
        );

        EventNetworkChannel handshakeAck = NetworkRegistry.newEventChannel(
                SimpleComChannels.HANDSHAKE_ACK,
                () -> PROTOCOL,
                v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
                v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
        );
        handshakeAck.addListener(ev -> onHandshakeAck((net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent) ev));

        EventNetworkChannel voiceData = NetworkRegistry.newEventChannel(
                SimpleComChannels.VOICE_DATA,
                () -> PROTOCOL,
                v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
                v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
        );
        voiceData.addListener(ev -> onVoiceData((net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent) ev));

        EventNetworkChannel channelSwitch = NetworkRegistry.newEventChannel(
                SimpleComChannels.CHANNEL_SWITCH,
                () -> PROTOCOL,
                v -> PROTOCOL.equals(v) || NetworkRegistry.ACCEPTVANILLA.equals(v),
                v -> PROTOCOL.equals(v) || NetworkRegistry.ABSENT.equals(v)
        );
        channelSwitch.addListener(ev -> onChannelSwitch((net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent) ev));

        LOGGER.info("[SimpleCom] Forge 服务端 payload 已注册");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long tick = server.getOverworld().getTime();
        for (Iterator<Map.Entry<UUID, int[]>> it = handshakeTasks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, int[]> e = it.next();
            int[] v = e.getValue();
            if (tick >= v[1]) {
                sendOneHandshake(server, e.getKey(), v[0]);
                v[0]++;
                v[1] = (int) tick + HANDSHAKE_INTERVAL_TICKS;
                if (v[0] >= HANDSHAKE_COUNT) it.remove();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            handshakeTasks.put(event.getPlayer().getUuid(), new int[]{0, 0});
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        handshakeTasks.remove(event.getPlayer().getUuid());
    }

    private void sendOneHandshake(MinecraftServer server, UUID playerId, int count) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.networkHandler.getConnection().isOpen()) return;
        try {
            byte[] data = buildHandshakePayload("1.0.0", "SimpleCom-Server", "forge");
            sendToPlayer(playerId, SimpleComChannels.HANDSHAKE, data);
            LOGGER.info("[SimpleCom] 发送握手 #{} 给 {}", count + 1, getPlayerName(playerId));
        } catch (IOException e) {
            logWarning("发送握手失败: " + e.getMessage());
        }
    }

    private void onHandshakeAck(net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent event) {
        event.getSource().get().enqueueWork(() -> {
            ServerPlayerEntity player = event.getSource().get().getSender();
            if (player == null) return;
            PacketByteBuf buf = event.getPayload();
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            int ch = 1;
            try {
                if (data.length > 1) data = Arrays.copyOfRange(data, 1, data.length);
                ch = VarIntUtil.readVarInt(data != null ? data : new byte[0]);
                ch = Math.max(0, Math.min(100, ch));
            } catch (Exception ignored) {}
            onHandshakeAck(player.getUuid(), ch);
        });
        event.getSource().get().setPacketHandled(true);
    }

    private void onHandshakeAck(UUID playerId, int channel) {
        setPlayerChannel(playerId, channel);
        handshakeTasks.remove(playerId);
        logInfo(getPlayerName(playerId) + " 已确认握手，信道：" + channel);
    }

    private void onVoiceData(net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent event) {
        event.getSource().get().enqueueWork(() -> {
            ServerPlayerEntity player = event.getSource().get().getSender();
            if (player == null) return;
            PacketByteBuf buf = event.getPayload();
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            onVoiceData(player.getUuid(), data);
        });
        event.getSource().get().setPacketHandled(true);
    }

    private void onVoiceData(UUID senderId, byte[] data) {
        if (data == null || data.length == 0) return;
        String senderChannel = "1";
        try {
            VoicePacketHeaderParser.Header header = VoicePacketHeaderParser.parse(data);
            if (header != null) {
                senderChannel = header.channel;
                int targetCount = 0;
                for (UUID id : getOnlinePlayerIdsInChannel(senderId, senderChannel)) targetCount++;
                logInfo(String.format("收到%s的语音数据包，%d/%d,正在向%d个客户端转发",
                        header.username, header.current + 1, header.total, targetCount));
            }
        } catch (Exception ignored) {}
        broadcastVoiceData(senderId, data, senderChannel);
    }

    private void onChannelSwitch(net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent event) {
        event.getSource().get().enqueueWork(() -> {
            ServerPlayerEntity player = event.getSource().get().getSender();
            if (player == null) return;
            PacketByteBuf buf = event.getPayload();
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            onChannelSwitch(player.getUuid(), data);
        });
        event.getSource().get().setPacketHandled(true);
    }

    private void onChannelSwitch(UUID playerId, byte[] data) {
        if (data == null) return;
        try {
            int ch = VarIntUtil.readVarInt(data);
            ch = Math.max(0, Math.min(100, ch));
            setPlayerChannel(playerId, ch);
            logInfo(getPlayerName(playerId) + " 已切换到" + ch + "信道");
            byte[] ack = buildChannelSwitchAckPayload(ch);
            sendToPlayer(playerId, SimpleComChannels.CHANNEL_SWITCH_ACK, ack);
        } catch (Exception e) {
            logWarning("解析信道切换失败: " + e.getMessage());
        }
    }

    private void broadcastVoiceData(UUID senderId, byte[] data, String channel) {
        for (UUID targetId : getOnlinePlayerIdsInChannel(senderId, channel)) {
            sendToPlayer(targetId, SimpleComChannels.VOICE_DATA, data);
        }
    }

    @Override
    public void sendToPlayer(UUID playerId, Identifier channel, byte[] data) {
        if (data == null || data.length == 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null && player.networkHandler.getConnection().isOpen()) {
                player.networkHandler.sendPacket(new CustomPayloadS2CPacket(channel, new PacketByteBuf(Unpooled.wrappedBuffer(data))));
            }
        });
    }

    @Override
    public Iterable<UUID> getOnlinePlayerIdsInChannel(UUID excludeSender, String channel) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<UUID> list = new ArrayList<>();
        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p != null && p.networkHandler.getConnection().isOpen() && !p.getUuid().equals(excludeSender)) {
                    if (channel.equals(String.valueOf(getPlayerChannel(p.getUuid())))) {
                        list.add(p.getUuid());
                    }
                }
            }
        }
        return list;
    }

    @Override
    public String getPlayerName(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return playerId.toString();
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
        return p != null ? p.getName().getString() : playerId.toString();
    }

    @Override
    public void logInfo(String msg) {
        LOGGER.info("[SimpleCom] {}", msg);
    }

    @Override
    public void logWarning(String msg) {
        LOGGER.warn("[SimpleCom] {}", msg);
    }
}
