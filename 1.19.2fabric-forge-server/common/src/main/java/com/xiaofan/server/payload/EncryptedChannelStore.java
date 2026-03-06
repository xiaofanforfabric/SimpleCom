package com.xiaofan.server.payload;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化加密信道：名称 -> (密码哈希, 信道ID)
 * 存储格式：每行 "name\tpasswordHash\tchannelId"
 */
public final class EncryptedChannelStore {

    private static final String FILE_NAME = "encrypted_channels.txt";
    private static final String SEP = "\t";

    private final Path filePath;
    private final Map<String, Entry> byName = new ConcurrentHashMap<>();
    private volatile int nextId = SimpleComChannels.ENCRYPTED_CHANNEL_ID_START;

    public static final class Entry {
        public final String passwordHash;
        public final int channelId;

        public Entry(String passwordHash, int channelId) {
            this.passwordHash = passwordHash;
            this.channelId = channelId;
        }
    }

    public EncryptedChannelStore(Path configDir) {
        this.filePath = configDir.resolve("simplecom-server").resolve(FILE_NAME);
        load();
    }

    public void load() {
        if (!Files.isRegularFile(filePath)) return;
        byName.clear();
        int maxId = SimpleComChannels.ENCRYPTED_CHANNEL_ID_START - 1;
        try (BufferedReader r = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(SEP, 3);
                if (parts.length < 3) continue;
                String name = parts[0];
                String hash = parts[1];
                int id;
                try {
                    id = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                byName.put(name, new Entry(hash, id));
                if (id > maxId) maxId = id;
            }
            nextId = maxId + 1;
        } catch (IOException ignored) {
        }
    }

    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Entry> e : byName.entrySet()) {
                    w.write(e.getKey());
                    w.write(SEP);
                    w.write(e.getValue().passwordHash);
                    w.write(SEP);
                    w.write(String.valueOf(e.getValue().channelId));
                    w.write('\n');
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** 创建加密信道，名称已存在则返回 null */
    public synchronized Integer create(String name, String passwordHash) {
        if (name == null || name.trim().isEmpty()) return null;
        name = name.trim();
        if (byName.containsKey(name)) return null;
        int id = nextId++;
        byName.put(name, new Entry(passwordHash != null ? passwordHash : "", id));
        save();
        return id;
    }

    public Entry getByName(String name) {
        return name != null ? byName.get(name.trim()) : null;
    }

    public List<String> listNames() {
        return new ArrayList<>(byName.keySet());
    }

    /** 根据信道 ID 移除加密信道（无人时销毁），返回被移除的信道名称，未找到返回 null */
    public synchronized String removeByChannelId(int channelId) {
        for (Map.Entry<String, Entry> e : new ArrayList<>(byName.entrySet())) {
            if (e.getValue().channelId == channelId) {
                byName.remove(e.getKey());
                save();
                return e.getKey();
            }
        }
        return null;
    }
}
