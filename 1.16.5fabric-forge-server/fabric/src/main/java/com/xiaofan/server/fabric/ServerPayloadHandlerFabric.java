package com.xiaofan.server.fabric;

import com.xiaofan.server.payload.SimpleComChannels;
import com.xiaofan.server.payload.ServerPayloadHandler;
import com.xiaofan.server.payload.VarIntUtil;
import com.xiaofan.server.payload.VoicePacketHeaderParser;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Fabric 服务端 payload 处理
 */
public final class ServerPayloadHandlerFabric extends ServerPayloadHandler {

    private static final Logger LOGGER = LogManager.getLogger("SimpleCom-Server");
    private final MinecraftServer server;
    private final Map<UUID, int[]> handshakeTasks = new HashMap<>(); // uuid -> [count, nextTick]

    public ServerPayloadHandlerFabric(MinecraftServer server) {
        this.server = server;
    }

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> handshakeTasks.remove(handler.player.getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            long tick = srv.getOverworld().getTime();
            for (Iterator<Map.Entry<UUID, int[]>> it = handshakeTasks.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, int[]> e = it.next();
                int[] v = e.getValue();
                if (tick >= v[1]) {
                    sendOneHandshake(e.getKey(), v[0]);
                    v[0]++;
                    v[1] = (int) tick + HANDSHAKE_INTERVAL_TICKS;
                    if (v[0] >= HANDSHAKE_COUNT) it.remove();
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SimpleComChannels.HANDSHAKE_ACK, (server, player, handler, buf, responseSender) -> {
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

        ServerPlayNetworking.registerGlobalReceiver(SimpleComChannels.VOICE_DATA, (server, player, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            onVoiceData(player.getUuid(), data);
        });

        ServerPlayNetworking.registerGlobalReceiver(SimpleComChannels.CHANNEL_SWITCH, (server, player, handler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            onChannelSwitch(player.getUuid(), data);
        });

        LOGGER.info("[SimpleCom] Fabric 服务端 payload 已注册");
    }

    private void onPlayerJoin(ServerPlayerEntity player) {
        handshakeTasks.put(player.getUuid(), new int[]{0, (int) server.getOverworld().getTime()});
    }

    private void sendOneHandshake(UUID playerId, int count) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.networkHandler.getConnection().isOpen()) return;
        try {
            byte[] data = buildHandshakePayload("1.0.0", "SimpleCom-Server", "fabric");
            sendToPlayer(playerId, SimpleComChannels.HANDSHAKE, data);
            LOGGER.info("[SimpleCom] 发送握手 #{} 给 {}", count + 1, getPlayerName(playerId));
        } catch (IOException e) {
            logWarning("发送握手失败: " + e.getMessage());
        }
    }

    private void onHandshakeAck(UUID playerId, int channel) {
        setPlayerChannel(playerId, channel);
        handshakeTasks.remove(playerId);
        logInfo(getPlayerName(playerId) + " 已确认握手，信道：" + channel);
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
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null && player.networkHandler.getConnection().isOpen() && ServerPlayNetworking.canSend(player, channel)) {
                ServerPlayNetworking.send(player, channel, new PacketByteBuf(Unpooled.wrappedBuffer(data)));
            }
        });
    }

    @Override
    public Iterable<UUID> getOnlinePlayerIdsInChannel(UUID excludeSender, String channel) {
        List<UUID> list = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p != null && p.networkHandler.getConnection().isOpen() && !p.getUuid().equals(excludeSender)) {
                if (channel.equals(String.valueOf(getPlayerChannel(p.getUuid())))) {
                    list.add(p.getUuid());
                }
            }
        }
        return list;
    }

    @Override
    public String getPlayerName(UUID playerId) {
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
