package com.xiaofan.config;

import java.util.Collections;
import java.util.List;

/**
 * 端口复用代理配置（纯数据，不依赖 Bukkit）。
 * Proxy/multiplex config (data only, no Bukkit).
 */
public final class ProxyConfig {

    private final String bindAddress;
    private final int listenPort;
    private final int wsPort;
    private final String mcBackendHost;
    private final int mcBackendPort;
    private final boolean requireProxyConnection;
    private final List<String> allowedSourceAddresses;
    private final boolean compressionEncoder;
    private final boolean lowLatency;
    
    // Voice API 配置
    private final boolean voiceApiEnabled;
    private final String voiceApiToken;
    private final int voiceApiPort;
    private final String voiceApiBind;

    /** 是否启用端口复用代理。false 时代理不启动，但 WS 服务器仍保持开启。
     *  Whether the port-multiplex proxy is enabled. When false, only the WS server starts. */
    private final boolean proxyEnabled;

    public ProxyConfig(String bindAddress, int listenPort, int wsPort,
                       String mcBackendHost, int mcBackendPort,
                       boolean requireProxyConnection, List<String> allowedSourceAddresses,
                       boolean compressionEncoder, boolean lowLatency,
                       boolean voiceApiEnabled, String voiceApiToken, int voiceApiPort, String voiceApiBind,
                       boolean proxyEnabled) {
        this.bindAddress = bindAddress != null ? bindAddress : "0.0.0.0";
        this.listenPort = listenPort;
        this.wsPort = wsPort;
        this.mcBackendHost = mcBackendHost != null ? mcBackendHost : "127.0.0.1";
        this.mcBackendPort = mcBackendPort;
        this.requireProxyConnection = requireProxyConnection;
        this.allowedSourceAddresses = allowedSourceAddresses != null && !allowedSourceAddresses.isEmpty()
                ? Collections.unmodifiableList(allowedSourceAddresses)
                : Collections.unmodifiableList(java.util.Arrays.asList("127.0.0.1", "::1"));
        this.compressionEncoder = compressionEncoder;
        this.lowLatency = lowLatency;
        this.voiceApiEnabled = voiceApiEnabled;
        this.voiceApiToken = voiceApiToken != null ? voiceApiToken.trim() : "";
        this.voiceApiPort = voiceApiPort;
        this.voiceApiBind = voiceApiBind != null ? voiceApiBind : "127.0.0.1";
        this.proxyEnabled = proxyEnabled;
    }

    public String getBindAddress() { return bindAddress; }
    public int getListenPort() { return listenPort; }
    public int getWsPort() { return wsPort; }
    public String getMcBackendHost() { return mcBackendHost; }
    public int getMcBackendPort() { return mcBackendPort; }
    public boolean isRequireProxyConnection() { return requireProxyConnection; }
    public List<String> getAllowedSourceAddresses() { return allowedSourceAddresses; }
    public boolean isCompressionEncoder() { return compressionEncoder; }
    public boolean isLowLatency() { return lowLatency; }
    
    public boolean isVoiceApiEnabled() { return voiceApiEnabled; }
    public String getVoiceApiToken() { return voiceApiToken; }
    public int getVoiceApiPort() { return voiceApiPort; }
    public String getVoiceApiBind() { return voiceApiBind; }

    /** 是否启用端口复用代理 | Whether the port-multiplex proxy is enabled */
    public boolean isProxyEnabled() { return proxyEnabled; }
}
