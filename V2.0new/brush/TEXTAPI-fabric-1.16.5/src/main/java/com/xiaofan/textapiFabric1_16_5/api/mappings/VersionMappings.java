package com.xiaofan.textapiFabric1_16_5.api.mappings;

/**
 * Minecraft 版本类名映射表。
 * 为不同版本的 Minecraft 提供正确的类名和方法签名映射。
 * 
 * 注意：由于使用 Mojang mappings（官方映射），类名在不同版本间通常是相同的。
 * 但某些包路径和方法签名可能不同。
 */
public final class VersionMappings {
    
    /**
     * Minecraft 版本枚举
     */
    public enum MinecraftVersion {
        V1_16_5("1.16.5"),
        V1_17_1("1.17.1"),
        V1_18_2("1.18.2"),
        V1_19_2("1.19.2"),
        V1_20_1("1.20.1"),
        V1_21_1("1.21.1"),
        V1_21_2("1.21.2"),
        V1_21_3("1.21.3"),
        V1_21_4("1.21.4"),
        V1_21_5("1.21.5"),
        V1_21_6("1.21.6"),
        V1_21_7("1.21.7"),
        V1_21_8("1.21.8"),
        V1_21_9("1.21.9"),
        V1_21_10("1.21.10"),
        V1_21_11("1.21.11");
        
        private final String version;
        
        MinecraftVersion(String version) {
            this.version = version;
        }
        
        public String getVersion() {
            return version;
        }
    }
    
    /**
     * 类名映射配置
     */
    public static class ClassMapping {
        // 1.16.5 - 1.18.2 的类名
        public static final String SERVER_PLAYER_ENTITY_1_16_5 = "net.minecraft.entity.player.ServerPlayerEntity";
        public static final String TEXT_COMPONENT_1_16_5 = "net.minecraft.util.text.ITextComponent";
        public static final String STRING_TEXT_COMPONENT_1_16_5 = "net.minecraft.util.text.StringTextComponent";
        
        // 1.19+ 的类名（如果不同）
        public static final String SERVER_PLAYER_ENTITY_1_19 = "net.minecraft.server.network.ServerPlayerEntity";
        public static final String TEXT_COMPONENT_1_19 = "net.minecraft.text.Component";
        public static final String STRING_TEXT_COMPONENT_1_19 = "net.minecraft.text.Text"; // 1.19+ 使用 Component.literal()
    }
    
    /**
     * 方法签名映射配置
     */
    public static class MethodMapping {
        /**
         * sendMessage 方法签名
         * 1.16.5 - 1.18.2: sendMessage(ITextComponent component)
         * 1.19+: sendMessage(Component component)
         */
        
        /**
         * getName() 方法返回类型
         * 1.16.5 - 1.18.2: ITextComponent
         * 1.19+: Component
         */
        
        /**
         * 创建文本组件的方法
         * 1.16.5 - 1.18.2: new StringTextComponent(String)
         * 1.19+: Component.literal(String)
         */
    }
    
    /**
     * 包路径映射配置
     */
    public static class PackageMapping {
        // 1.16.5 - 1.18.2
        public static final String ENTITY_PLAYER_OLD = "net.minecraft.entity.player";
        public static final String UTIL_TEXT_OLD = "net.minecraft.util.text";
        
        // 1.19+
        public static final String SERVER_NETWORK_NEW = "net.minecraft.server.network";
        public static final String TEXT_NEW = "net.minecraft.text";
    }
    
    /**
     * 根据版本判断是否使用旧版 API（1.16.5-1.18.2）
     */
    public static boolean isLegacyVersion(MinecraftVersion version) {
        return version == MinecraftVersion.V1_16_5 ||
               version == MinecraftVersion.V1_17_1 ||
               version == MinecraftVersion.V1_18_2;
    }
    
    /**
     * 根据版本判断是否使用新版 API（1.19+）
     */
    public static boolean isModernVersion(MinecraftVersion version) {
        return version == MinecraftVersion.V1_19_2 ||
               version == MinecraftVersion.V1_20_1 ||
               version == MinecraftVersion.V1_21_1 ||
               version == MinecraftVersion.V1_21_2 ||
               version == MinecraftVersion.V1_21_3 ||
               version == MinecraftVersion.V1_21_4 ||
               version == MinecraftVersion.V1_21_5 ||
               version == MinecraftVersion.V1_21_6 ||
               version == MinecraftVersion.V1_21_7 ||
               version == MinecraftVersion.V1_21_8 ||
               version == MinecraftVersion.V1_21_9 ||
               version == MinecraftVersion.V1_21_10 ||
               version == MinecraftVersion.V1_21_11;
    }
    
    /**
     * 从版本字符串解析 MinecraftVersion
     */
    public static MinecraftVersion parseVersion(String versionString) {
        if (versionString == null) return MinecraftVersion.V1_16_5;
        
        for (MinecraftVersion v : MinecraftVersion.values()) {
            if (v.getVersion().equals(versionString)) {
                return v;
            }
        }
        
        // 尝试匹配主版本号
        if (versionString.startsWith("1.16")) return MinecraftVersion.V1_16_5;
        if (versionString.startsWith("1.17")) return MinecraftVersion.V1_17_1;
        if (versionString.startsWith("1.18")) return MinecraftVersion.V1_18_2;
        if (versionString.startsWith("1.19")) return MinecraftVersion.V1_19_2;
        if (versionString.startsWith("1.20")) return MinecraftVersion.V1_20_1;
        if (versionString.startsWith("1.21")) {
            if (versionString.equals("1.21.11")) return MinecraftVersion.V1_21_11;
            if (versionString.equals("1.21.10")) return MinecraftVersion.V1_21_10;
            if (versionString.equals("1.21.9")) return MinecraftVersion.V1_21_9;
            if (versionString.equals("1.21.8")) return MinecraftVersion.V1_21_8;
            if (versionString.equals("1.21.7")) return MinecraftVersion.V1_21_7;
            if (versionString.equals("1.21.6")) return MinecraftVersion.V1_21_6;
            if (versionString.equals("1.21.5")) return MinecraftVersion.V1_21_5;
            if (versionString.equals("1.21.4")) return MinecraftVersion.V1_21_4;
            if (versionString.equals("1.21.3")) return MinecraftVersion.V1_21_3;
            if (versionString.equals("1.21.2")) return MinecraftVersion.V1_21_2;
            if (versionString.equals("1.21.1")) return MinecraftVersion.V1_21_1;
            return MinecraftVersion.V1_21_1; // 默认返回 1.21.1
        }
        
        return MinecraftVersion.V1_16_5; // 默认
    }
    
    /**
     * 获取当前 Minecraft 版本
     * 注意：这个方法需要在运行时检测，或者通过构建时注入版本号
     */
    public static MinecraftVersion getCurrentVersion() {
        // 这里可以通过系统属性或配置文件获取版本
        // 例如：System.getProperty("minecraft.version")
        // 或者从 fabric.mod.json 中读取
        return MinecraftVersion.V1_16_5; // 默认返回 1.16.5
    }
    
    /**
     * 根据版本获取 ServerPlayerEntity 的完整类名
     */
    public static String getServerPlayerEntityClass(MinecraftVersion version) {
        switch (version) {
            case V1_16_5:
            case V1_17_1:
            case V1_18_2:
                return ClassMapping.SERVER_PLAYER_ENTITY_1_16_5;
            case V1_19_2:
            case V1_20_1:
            case V1_21_1:
            case V1_21_2:
            case V1_21_3:
            case V1_21_4:
            case V1_21_5:
            case V1_21_6:
            case V1_21_7:
            case V1_21_8:
            case V1_21_9:
            case V1_21_10:
            case V1_21_11:
                return ClassMapping.SERVER_PLAYER_ENTITY_1_19;
            default:
                return ClassMapping.SERVER_PLAYER_ENTITY_1_16_5;
        }
    }
    
    /**
     * 根据版本获取文本组件接口的完整类名
     */
    public static String getTextComponentClass(MinecraftVersion version) {
        switch (version) {
            case V1_16_5:
            case V1_17_1:
            case V1_18_2:
                return ClassMapping.TEXT_COMPONENT_1_16_5;
            case V1_19_2:
            case V1_20_1:
            case V1_21_1:
            case V1_21_2:
            case V1_21_3:
            case V1_21_4:
            case V1_21_5:
            case V1_21_6:
            case V1_21_7:
            case V1_21_8:
            case V1_21_9:
            case V1_21_10:
            case V1_21_11:
                return ClassMapping.TEXT_COMPONENT_1_19;
            default:
                return ClassMapping.TEXT_COMPONENT_1_16_5;
        }
    }
    
    /**
     * 根据版本获取字符串文本组件类的完整类名
     */
    public static String getStringTextComponentClass(MinecraftVersion version) {
        switch (version) {
            case V1_16_5:
            case V1_17_1:
            case V1_18_2:
                return ClassMapping.STRING_TEXT_COMPONENT_1_16_5;
            case V1_19_2:
            case V1_20_1:
            case V1_21_1:
            case V1_21_2:
            case V1_21_3:
            case V1_21_4:
            case V1_21_5:
            case V1_21_6:
            case V1_21_7:
            case V1_21_8:
            case V1_21_9:
            case V1_21_10:
            case V1_21_11:
                return ClassMapping.STRING_TEXT_COMPONENT_1_19;
            default:
                return ClassMapping.STRING_TEXT_COMPONENT_1_16_5;
        }
    }
    
    /**
     * 获取版本对应的包路径前缀
     * @param version Minecraft 版本
     * @return 包路径前缀（如 "net.minecraft.util.text" 或 "net.minecraft.text"）
     */
    public static String getTextPackage(MinecraftVersion version) {
        if (isLegacyVersion(version)) {
            return PackageMapping.UTIL_TEXT_OLD;
        } else {
            return PackageMapping.TEXT_NEW;
        }
    }
    
    /**
     * 获取版本对应的玩家实体包路径
     * @param version Minecraft 版本
     * @return 包路径（如 "net.minecraft.entity.player" 或 "net.minecraft.server.network"）
     */
    public static String getPlayerEntityPackage(MinecraftVersion version) {
        if (isLegacyVersion(version)) {
            return PackageMapping.ENTITY_PLAYER_OLD;
        } else {
            return PackageMapping.SERVER_NETWORK_NEW;
        }
    }
    
    /**
     * 获取版本信息摘要
     * @param version Minecraft 版本
     * @return 版本信息字符串
     */
    public static String getVersionInfo(MinecraftVersion version) {
        StringBuilder info = new StringBuilder();
        info.append("Minecraft ").append(version.getVersion()).append("\n");
        info.append("ServerPlayerEntity: ").append(getServerPlayerEntityClass(version)).append("\n");
        info.append("TextComponent: ").append(getTextComponentClass(version)).append("\n");
        info.append("StringTextComponent: ").append(getStringTextComponentClass(version)).append("\n");
        info.append("API Type: ").append(isLegacyVersion(version) ? "Legacy (1.16-1.18)" : "Modern (1.19+)");
        return info.toString();
    }
    
    /**
     * 检查两个版本是否使用相同的 API
     * @param v1 版本1
     * @param v2 版本2
     * @return 如果使用相同的 API 返回 true
     */
    public static boolean useSameAPI(MinecraftVersion v1, MinecraftVersion v2) {
        return isLegacyVersion(v1) == isLegacyVersion(v2);
    }
}
