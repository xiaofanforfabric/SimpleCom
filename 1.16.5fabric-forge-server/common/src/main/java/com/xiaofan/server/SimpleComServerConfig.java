package com.xiaofan.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 模组服务端配置，与 Bukkit config.yml 对齐
 * 配置项：use_compression_encoder, low_latency, version, name, server_type
 */
public final class SimpleComServerConfig {

    public static final String DEFAULT_VERSION = "1.0.0";
    public static final String DEFAULT_NAME = "SimpleCom-Server";
    public static final String CONFIG_NAME = "config.yml";

    private boolean useCompressionEncoder = false;
    private boolean lowLatency = false;
    private String version = DEFAULT_VERSION;
    private String name = DEFAULT_NAME;
    private String serverType = "fabric";

    public boolean isUseCompressionEncoder() {
        return useCompressionEncoder;
    }

    public boolean isLowLatency() {
        return lowLatency;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType != null ? serverType : "fabric";
    }

    /**
     * 从 config 目录加载 config.yml，若不存在则使用默认并写出默认文件
     * configDir 通常为 .minecraft/config 或 run/config
     */
    public static SimpleComServerConfig load(Path configDir, String serverType) {
        SimpleComServerConfig c = new SimpleComServerConfig();
        c.setServerType(serverType);
        Path dir = configDir.resolve("simplecom-server");
        Path file = dir.resolve(CONFIG_NAME);
        try {
            if (Files.isRegularFile(file)) {
                Map<String, String> map = parseYamlLike(file);
                if (map.containsKey("use_compression_encoder")) {
                    c.useCompressionEncoder = parseBoolean(map.get("use_compression_encoder"), false);
                }
                if (map.containsKey("low_latency")) {
                    c.lowLatency = parseBoolean(map.get("low_latency"), false);
                }
                if (map.containsKey("version")) c.version = map.get("version").trim();
                if (map.containsKey("name")) c.name = map.get("name").trim();
                if (map.containsKey("server_type")) c.serverType = map.get("server_type").trim();
            }
        } catch (Exception ignored) {
        }
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(dir);
                Files.write(file, defaultConfigContent(c.serverType).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
        return c;
    }

    private static Map<String, String> parseYamlLike(Path file) throws IOException {
        Map<String, String> map = new HashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                map.put(key, value);
            }
        }
        return map;
    }

    private static boolean parseBoolean(String s, boolean def) {
        if (s == null) return def;
        s = s.trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static String defaultConfigContent(String serverType) {
        return "# SimpleCom 模组服务端配置\n" +
                "# use_compression_encoder: false/true 使用压缩编码器\n" +
                "# low_latency: false/true 低延迟（边录边发）\n" +
                "use_compression_encoder: false\n" +
                "low_latency: false\n" +
                "version: " + DEFAULT_VERSION + "\n" +
                "name: " + DEFAULT_NAME + "\n" +
                "server_type: " + (serverType != null ? serverType : "fabric") + "\n";
    }
}
