package com.xiaofan.ws;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice Stream API (WSAPI) 客户端连接注册表。
 * WSAPI 客户端仅接收服务端推送的语音二进制包与少量状态/心跳文本消息。
 */
public final class VoiceApiConnectionRegistry {

    private static final VoiceApiConnectionRegistry INSTANCE = new VoiceApiConnectionRegistry();

    /** key: connection id → sender */
    private final ConcurrentHashMap<UUID, WsSender> idToSender = new ConcurrentHashMap<>();

    private VoiceApiConnectionRegistry() {}

    public static VoiceApiConnectionRegistry getInstance() {
        return INSTANCE;
    }

    public UUID register(WsSender sender) {
        if (sender == null) return null;
        UUID id = UUID.randomUUID();
        idToSender.put(id, sender);
        return id;
    }

    public void unregister(UUID id) {
        if (id == null) return;
        idToSender.remove(id);
    }

    public Map<UUID, WsSender> getAllSenders() {
        return java.util.Collections.unmodifiableMap(idToSender);
    }
}

