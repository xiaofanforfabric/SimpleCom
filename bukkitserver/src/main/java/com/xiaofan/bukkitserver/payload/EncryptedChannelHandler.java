package com.xiaofan.bukkitserver.payload;

import com.xiaofan.servercommen.payload.SimpleComChannels;
import com.xiaofan.servercommen.payload.VarIntUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 处理加密信道 payload：创建、列表、加入
 * C2S 创建/加入：VarInt nameLen, UTF8 name, VarInt hashLen, UTF8 hashHex
 */
public final class EncryptedChannelHandler {

    private final EncryptedChannelStore store;
    private final PayloadChannelHandler payloadHandler;
    private final JavaPlugin plugin;

    public EncryptedChannelHandler(EncryptedChannelStore store, PayloadChannelHandler payloadHandler, JavaPlugin plugin) {
        this.store = store;
        this.payloadHandler = payloadHandler;
        this.plugin = plugin;
    }

    public void onEncryptedCreate(Player player, byte[] data) {
        if (player == null || data == null) return;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            int nameLen = VarIntUtil.readVarInt(bis);
            byte[] nameBytes = new byte[nameLen];
            readFully(bis, nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            int hashLen = VarIntUtil.readVarInt(bis);
            byte[] hashBytes = new byte[hashLen];
            readFully(bis, hashBytes);
            String passwordHash = new String(hashBytes, StandardCharsets.UTF_8);

            Integer channelId = store.create(name, passwordHash);
            if (channelId == null) {
                sendCreateResponse(player, false, 0);
                plugin.getLogger().info("[SimpleCom] " + player.getName() + " 创建加密信道失败（名称已存在）: " + name);
                return;
            }
            sendCreateResponse(player, true, channelId);
            payloadHandler.setPlayerChannel(player, channelId);
            plugin.getLogger().info("[SimpleCom] " + player.getName() + " 创建加密信道: " + name + " -> ID " + channelId);
        } catch (Exception e) {
            plugin.getLogger().warning("[SimpleCom] 解析加密信道创建请求失败: " + e.getMessage());
            sendCreateResponse(player, false, 0);
        }
    }

    /** 请求列表时始终回复：无信道时返回 VarInt(0)（1 字节），客户端不会卡在「正在加载」 */
    public void onEncryptedList(Player player) {
        if (player == null) return;
        try {
            List<String> names = store.listNames();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeVarInt(out, names.size());
            for (String name : names) {
                byte[] b = name.getBytes(StandardCharsets.UTF_8);
                writeVarInt(out, b.length);
                out.write(b);
            }
            player.sendPluginMessage(plugin, SimpleComChannels.ENCRYPTED_LIST, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[SimpleCom] 发送加密信道列表失败: " + e.getMessage());
        }
    }

    public void onEncryptedJoin(Player player, byte[] data) {
        if (player == null || data == null) return;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            int nameLen = VarIntUtil.readVarInt(bis);
            byte[] nameBytes = new byte[nameLen];
            readFully(bis, nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            int hashLen = VarIntUtil.readVarInt(bis);
            byte[] hashBytes = new byte[hashLen];
            readFully(bis, hashBytes);
            String passwordHash = new String(hashBytes, StandardCharsets.UTF_8);

            EncryptedChannelStore.Entry entry = store.getByName(name);
            if (entry == null) {
                sendJoinResponse(player, false, 0);
                plugin.getLogger().info("[SimpleCom] " + player.getName() + " 加入加密信道失败（不存在）: " + name);
                return;
            }
            if (!entry.passwordHash.equals(passwordHash)) {
                sendJoinResponse(player, false, 0);
                plugin.getLogger().info("[SimpleCom] " + player.getName() + " 加入加密信道失败（密码错误）: " + name);
                return;
            }
            sendJoinResponse(player, true, entry.channelId);
            payloadHandler.setPlayerChannel(player, entry.channelId);
            plugin.getLogger().info("[SimpleCom] " + player.getName() + " 加入加密信道: " + name + " -> ID " + entry.channelId);
        } catch (Exception e) {
            plugin.getLogger().warning("[SimpleCom] 解析加密信道加入请求失败: " + e.getMessage());
            sendJoinResponse(player, false, 0);
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r <= 0) throw new IOException("EOF");
            n += r;
        }
    }

    private void sendCreateResponse(Player player, boolean success, int channelId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(success ? 1 : 0);
            if (success) writeVarInt(out, channelId);
            player.sendPluginMessage(plugin, SimpleComChannels.ENCRYPTED_CREATE, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[SimpleCom] 发送创建响应失败: " + e.getMessage());
        }
    }

    private void sendJoinResponse(Player player, boolean success, int channelId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(success ? 1 : 0);
            if (success) writeVarInt(out, channelId);
            player.sendPluginMessage(plugin, SimpleComChannels.ENCRYPTED_JOIN, baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[SimpleCom] 发送加入响应失败: " + e.getMessage());
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
