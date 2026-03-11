package com.xiaofan.config;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 纯 Java 配置加载：在配置根目录下 SimpleComConfig/config.yml，首次启动写入默认配置后读取。
 * 应由根入口（Bukkit 插件 / Fabric 模组）在 load() 前调用 setConfigBaseDir(服务器根目录)，
 * 否则会回退到「当前类所在 JAR 的父目录或 user.dir」，解压到临时目录时用户可能找不到配置。
 * No Bukkit dependency.
 */
public final class SimpleComConfigLoader {

    private static final String CONFIG_DIR_NAME = "SimpleComConfig";
    private static final String CONFIG_FILE_NAME = "config.yml";

    /** 由 Bukkit/Fabric 传入的配置根目录（如服务器根目录、游戏目录），未设置则回退到 JAR/user.dir。 */
    private static volatile File configBaseDir;

    private static final String DEFAULT_YAML =
            "# SimpleCom Port-Multiplexing Proxy Config\n" +
            "# \u914d\u7f6e\u6839\u76ee\u5f55\u4e0b SimpleComConfig/config.yml | config.yml under config base dir\n" +
            "\n" +
            "# Enable/disable the port-multiplex proxy (default: true).\n" +
            "# \u542f\u7528\u6216\u7981\u7528\u7aef\u53e3\u590d\u7528\u4ee3\u7406\uff08\u9ed8\u8ba4\u542f\u7528\uff09\u3002\n" +
            "# \u9002\u7528\u4e8e\u5355\u7aef\u53e3\u5f00\u653e\u7684\u670d\u52a1\u5668\uff08\u5982\u5185\u7f51\u7a7f\u900f\u3001\u4fbf\u5b9c\u4e91\u670d\u52a1\u5668\uff09\u65e0\u6cd5\u91c7\u7528\u591a\u7aef\u53e3\u4f20\u8f93\u65f6\u7684\u89e3\u51b3\u65b9\u6848\u3002\n" +
            "# \u7f3a\u70b9\uff1a\u65e0\u6cd5\u56de\u6e90\u771f\u6b63 IP\uff0c\u6240\u6709\u6d41\u91cf\u8fc7\u4ee3\u7406\u4f1a\u5bfc\u81f4 MC \u6536\u5230\u7684\u662f 127.0.0.1\uff0c\u5c01 IP \u4f1a\u5931\u6548\u3002\n" +
            "# \u672a\u542f\u7528\u65f6\u9700\u516c\u5f00\u8bed\u97f3\u670d\u52a1\u5668\u5730\u5740\uff08ws-port\uff09\u4f9b\u5ba2\u6237\u7aef\u76f4\u63a5\u8fde\u63a5\u3002\n" +
            "# When disabled, WS server still runs; clients must connect to ws-port directly.\n" +
            "proxy-enabled: true\n" +
            "\n" +
            "proxy:\n" +
            "  bind-address: \"0.0.0.0\"\n" +
            "  listen-port: 25566\n" +
            "  mc-backend-host: 127.0.0.1\n" +
            "  mc-backend-port: 25565\n" +
            "  ws-port: 25567\n" +
            "  require-proxy-connection: true\n" +
            "  allowed-source-addresses:\n" +
            "    - \"127.0.0.1\"\n" +
            "    - \"::1\"\n" +
            "  # Compression encoder: reduces network bandwidth but may cause audio artifacts due to lossy compression.\n" +
            "  # We do not accept any issues regarding audio quality when this is enabled.\n" +
            "  # \u662f\u5426\u542f\u7528\u538b\u7f29\u7f16\u7801\u5668\uff1f\u542f\u7528\u540e\u4f1a\u51cf\u5c11\u7f51\u7edc\u6d41\u91cf\u5e26\u5bbd\uff0c\u4f46\u540c\u65f6\u4e5f\u4f1a\u51fa\u73b0\u58f0\u97f3\u5947\u602a\uff0c\u8fd9\u662f\u7531\u4e8e\u538b\u7f29\u4e22\u5931\u5bfc\u81f4\u7684\uff0c\u6211\u4eec\u4e0d\u63a5\u53d7\u4efb\u4f55\u5173\u4e8e\u58f0\u97f3\u95ee\u9898\u7684issue\n" +
            "  compression-encoder: true\n" +
            "  # Low latency mode (EXPERIMENTAL): enables real-time communication but may cause audio stuttering depending on network conditions.\n" +
            "  # We do not accept any issues regarding this feature as it goes against our original design philosophy.\n" +
            "  # \u5b9e\u9a8c\u6027\u529f\u80fd\uff01\u5982\u679c\u4f60\u8ffd\u6c42\u5b9e\u65f6\u901a\u4fe1\uff0c\u53ef\u4ee5\u542f\u7528\uff0c\u4f46\u662f\u53ef\u80fd\u4f1a\u51fa\u73b0\u58f0\u97f3\u5361\u987f\u7b49\u60c5\u51b5\uff0c\u89c6\u7f51\u7edc\u60c5\u51b5\u800c\u5b9a\uff01\u6211\u4eec\u4e0d\u63a5\u53d7\u4efb\u4f55\u5173\u4e8e\u6b64\u529f\u80fd\u7684issue\uff0c\u56e0\u4e3a\u8fdd\u80cc\u4e86\u6211\u4eec\u7684\u521d\u8877\uff01\n" +
            "  low-latency: false\n" +
            "\n" +
            "# Voice Stream API: allows external plugins to connect to other voice service providers (e.g., KOOK, Discord).\n" +
            "# If enabled, you MUST configure a token, otherwise the plugin will stop with an error.\n" +
            "# This API uses WebSocket streaming, bound to localhost by default. You can change to 0.0.0.0 for external access\n" +
            "# (but this significantly reduces security and is NOT recommended).\n" +
            "# \u8bed\u97f3\u6d41API\uff01\u53ef\u4ee5\u4f7f\u7528\u5916\u90e8\u63d2\u4ef6\u8fde\u63a5\u5176\u4ed6\u8bed\u97f3\u670d\u52a1\u5546\u7684API\uff0c\u5982KOOK\u3001Discord\u3002\n" +
            "# \u4f46\u662f\u542f\u7528\u4e00\u5b9a\u8981\u914d\u7f6etoken\uff0c\u5426\u5219\u63d2\u4ef6\u5c06\u505c\u6b62\uff0c\u6b64API\u91c7\u7528WebSocket\u63a8\u6d41\uff0c\u9ed8\u8ba4\u7ed1\u5b9a\u5728\u672c\u5730\u56de\u73af\u5730\u5740\uff0c\n" +
            "# \u53ef\u4ee5\u6539\u4e3a0.0.0.0\u5f00\u542f\u5916\u90e8\u8bbf\u95ee\uff08\u4f46\u662f\u4f1a\u663e\u8457\u964d\u4f4e\u5b89\u5168\u6027\uff0c\u4e0d\u5efa\u8bae\uff09\u3002\n" +
            "voice-api:\n" +
            "  enabled: false\n" +
            "  token: \"\"\n" +
            "  port: 25500\n" +
            "  bind: \"127.0.0.1\"\n";

    private SimpleComConfigLoader() {}

    /**
     * 设置配置根目录。由 Bukkit 插件（服务器根目录）或 Fabric 模组（游戏目录）在首次 load() 前调用。
     * 传入 null 表示使用默认解析（JAR 父目录或 user.dir）。
     */
    public static void setConfigBaseDir(File baseDir) {
        configBaseDir = baseDir;
    }

    /**
     * 解析并返回配置。若 config.yml 不存在则先写入默认内容再读取。
     * 配置路径：若已 setConfigBaseDir，则为 baseDir/SimpleComConfig/config.yml；否则为 JAR 同目录或 user.dir 下。
     */
    @SuppressWarnings("unchecked")
    public static ProxyConfig load() throws IOException {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try (OutputStream out = Files.newOutputStream(configFile.toPath())) {
                out.write(DEFAULT_YAML.getBytes(StandardCharsets.UTF_8));
            }
        }
        try (Reader r = new InputStreamReader(Files.newInputStream(configFile.toPath()), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(r);
            if (root == null) root = new HashMap<>();
            Map<String, Object> proxy = (Map<String, Object>) root.get("proxy");
            if (proxy == null) proxy = new HashMap<>();

            String bindAddress = getStr(proxy, "bind-address", "0.0.0.0");
            int listenPort = getInt(proxy, "listen-port", 25566);
            int wsPort = getInt(proxy, "ws-port", 25567);
            String mcBackendHost = getStr(proxy, "mc-backend-host", "127.0.0.1");
            int mcBackendPort = getInt(proxy, "mc-backend-port", 25565);
            boolean requireProxyConnection = getBool(proxy, "require-proxy-connection", true);
            List<String> allowed = getStrList(proxy, "allowed-source-addresses", Arrays.asList("127.0.0.1", "::1"));
            boolean compressionEncoder = getBool(proxy, "compression-encoder", true);
            boolean lowLatency = getBool(proxy, "low-latency", false);

            // 顶层 proxy-enabled 开关
            boolean proxyEnabled = getBool(root, "proxy-enabled", true);

            // Voice API 配置
            Map<String, Object> voiceApi = (Map<String, Object>) root.get("voice-api");
            if (voiceApi == null) voiceApi = new HashMap<>();
            
            boolean voiceApiEnabled = getBool(voiceApi, "enabled", false);
            String voiceApiToken = getStr(voiceApi, "token", "");
            int voiceApiPort = getInt(voiceApi, "port", 25500);
            String voiceApiBind = getStr(voiceApi, "bind", "127.0.0.1");
            
            // 验证：如果启用 Voice API 但 token 为空，抛出异常
            if (voiceApiEnabled && (voiceApiToken == null || voiceApiToken.trim().isEmpty())) {
                throw new IllegalStateException("ERROR: Voice API is enabled but token is empty! This is unsafe. Please configure a token or disable Voice API.");
            }

            return new ProxyConfig(bindAddress, listenPort, wsPort, mcBackendHost, mcBackendPort, 
                                   requireProxyConnection, allowed, compressionEncoder, lowLatency,
                                   voiceApiEnabled, voiceApiToken, voiceApiPort, voiceApiBind,
                                   proxyEnabled);
        }
    }

    /** 返回 SimpleComConfig 目录（用于日志等）。若无法解析则返回 null。 */
    public static File getConfigDir() {
        File f = getConfigFile();
        return f != null ? f.getParentFile() : null;
    }

    private static File getConfigFile() {
        File baseDir = configBaseDir != null ? configBaseDir : getJarOrWorkingDir();
        return new File(baseDir, CONFIG_DIR_NAME + File.separator + CONFIG_FILE_NAME);
    }

    private static File getJarOrWorkingDir() {
        try {
            java.net.URL location = SimpleComConfigLoader.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return new File(System.getProperty("user.dir"));
            File jarOrDir = new File(location.toURI());
            File parent = jarOrDir.getParentFile();
            return parent != null ? parent : new File(System.getProperty("user.dir"));
        } catch (URISyntaxException e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private static String getStr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : def;
    }

    private static int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static boolean getBool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStrList(Map<String, Object> m, String key, List<String> def) {
        Object v = m.get(key);
        if (v == null || !(v instanceof List)) return def;
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (o != null) out.add(o.toString().trim());
        }
        return out.isEmpty() ? def : out;
    }
}
