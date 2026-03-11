# TEXTAPI 版本映射表系统使用指南

## 概述

TEXTAPI 提供了一个完整的版本映射表系统，用于处理不同 Minecraft 版本之间的类名和方法签名差异。这对于需要支持多个 Minecraft 版本的模组非常有用。

## 系统结构

```
api/
├── mappings/
│   ├── VersionMappings.java          # 版本映射表核心类
│   └── MAPPINGS_README.md            # 映射表详细文档
├── chat/
│   └── ChatMessageAPI_1_16_5.java   # 聊天消息 API（1.16.5 版本）
├── player/
│   └── PlayerEventAPI_1_16_5.java    # 玩家事件 API（1.16.5 版本）
└── ExampleUsage.java                 # 使用示例
```

## 核心功能

### 1. 版本枚举

`VersionMappings.MinecraftVersion` 枚举定义了所有支持的版本：

- `V1_16_5` - Minecraft 1.16.5
- `V1_17_1` - Minecraft 1.17.1
- `V1_18_2` - Minecraft 1.18.2
- `V1_19_2` - Minecraft 1.19.2
- `V1_20_1` - Minecraft 1.20.1
- `V1_21_1` - Minecraft 1.21.1

### 2. 类名映射

提供了以下关键类的版本映射：

- **ServerPlayerEntity**: 服务器玩家实体类
- **TextComponent**: 文本组件接口
- **StringTextComponent**: 字符串文本组件实现类

### 3. 版本检测

- `isLegacyVersion()` - 检查是否为旧版 API (1.16-1.18)
- `isModernVersion()` - 检查是否为新版 API (1.19+)
- `parseVersion()` - 从字符串解析版本
- `getCurrentVersion()` - 获取当前版本

### 4. 类名获取

- `getServerPlayerEntityClass()` - 获取 ServerPlayerEntity 的完整类名
- `getTextComponentClass()` - 获取文本组件接口的完整类名
- `getStringTextComponentClass()` - 获取字符串文本组件类的完整类名

### 5. 包路径获取

- `getTextPackage()` - 获取文本组件的包路径
- `getPlayerEntityPackage()` - 获取玩家实体的包路径

## 主要版本差异

### 1.16.5 - 1.18.2 (Legacy API)

```java
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

// 创建文本组件
ITextComponent component = new StringTextComponent("Hello");

// 发送消息
player.sendMessage(component);
```

### 1.19+ (Modern API)

```java
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Component;

// 创建文本组件
Component component = Component.literal("Hello");

// 发送消息
player.sendMessage(component);
```

## 使用场景

### 场景 1: 为每个版本创建单独的 API 类

这是最推荐的方式，因为 Java 是静态类型语言：

```
api/
├── chat/
│   ├── ChatMessageAPI_1_16_5.java
│   ├── ChatMessageAPI_1_19_2.java
│   └── ChatMessageAPI_1_21_1.java
└── player/
    ├── PlayerEventAPI_1_16_5.java
    ├── PlayerEventAPI_1_19_2.java
    └── PlayerEventAPI_1_21_1.java
```

### 场景 2: 使用 Gradle 条件编译

在 `build.gradle` 中根据版本选择不同的源代码：

```gradle
sourceSets {
    main {
        java {
            if (project.minecraft_version.startsWith("1.16") || 
                project.minecraft_version.startsWith("1.17") || 
                project.minecraft_version.startsWith("1.18")) {
                srcDirs = ['src/main/java-legacy']
            } else {
                srcDirs = ['src/main/java-modern']
            }
        }
    }
}
```

### 场景 3: 运行时版本检测（不推荐）

如果需要运行时检测版本，可以使用反射，但这会降低性能：

```java
VersionMappings.MinecraftVersion version = detectVersionAtRuntime();
if (VersionMappings.isLegacyVersion(version)) {
    // 使用旧版 API
} else {
    // 使用新版 API
}
```

## 快速开始

### 1. 导入映射表

```java
import com.xiaofan.textapiFabric1_16_5.api.mappings.VersionMappings;
```

### 2. 获取版本信息

```java
VersionMappings.MinecraftVersion version = VersionMappings.getCurrentVersion();
```

### 3. 检查版本类型

```java
if (VersionMappings.isLegacyVersion(version)) {
    // 使用 1.16-1.18 API
} else {
    // 使用 1.19+ API
}
```

### 4. 获取类名映射

```java
String playerClass = VersionMappings.getServerPlayerEntityClass(version);
String textComponentClass = VersionMappings.getTextComponentClass(version);
```

## 示例代码

查看 `ExampleUsage.java` 获取完整的使用示例：

```java
// 注册玩家事件监听器
PlayerEventAPI_1_16_5.onPlayerJoin(player -> {
    String name = player.getName().getString();
    ChatMessageAPI_1_16_5.sendMessage(player, "欢迎, " + name + "!");
});

// 演示版本映射表使用
ExampleUsage.demonstrateVersionMappings();
```

## 扩展映射表

如果需要添加新版本或新的类映射：

1. 在 `MinecraftVersion` 枚举中添加新版本
2. 在 `ClassMapping` 中添加对应的类名常量
3. 在相关方法中添加新的 case 分支
4. 更新文档

## 注意事项

1. **编译时映射**: 映射表主要用于编译时选择正确的类名
2. **Mojang Mappings**: 本映射表基于 Mojang mappings（官方映射）
3. **性能**: 避免在运行时频繁调用映射表方法
4. **类型安全**: Java 是静态类型语言，不同版本的类不能直接互换

## 相关文档

- `api/mappings/MAPPINGS_README.md` - 详细的映射表文档
- `api/ExampleUsage.java` - 使用示例代码
- `api/chat/ChatMessageAPI_1_16_5.java` - 聊天消息 API 实现
- `api/player/PlayerEventAPI_1_16_5.java` - 玩家事件 API 实现

## 总结

版本映射表系统为 TEXTAPI 提供了强大的跨版本支持能力。通过使用映射表，你可以：

- ✅ 清晰地了解不同版本间的差异
- ✅ 为每个版本创建专门的实现
- ✅ 在编译时选择正确的类名
- ✅ 轻松扩展支持新版本

建议采用"为每个版本创建单独的 API 类"的方式，这样可以获得最佳的类型安全性和性能。
