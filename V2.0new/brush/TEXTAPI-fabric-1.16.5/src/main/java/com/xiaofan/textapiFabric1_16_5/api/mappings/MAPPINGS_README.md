# Minecraft 版本映射表使用指南

## 概述

`VersionMappings` 类提供了不同 Minecraft 版本之间的类名和方法签名映射，帮助开发者编写跨版本的代码。

## 支持的版本

- **Legacy API (1.16.5 - 1.18.2)**:
  - `V1_16_5` - Minecraft 1.16.5
  - `V1_17_1` - Minecraft 1.17.1
  - `V1_18_2` - Minecraft 1.18.2

- **Modern API (1.19+)**:
  - `V1_19_2` - Minecraft 1.19.2
  - `V1_20_1` - Minecraft 1.20.1
  - `V1_21_1` - Minecraft 1.21.1

## 主要类名差异

### ServerPlayerEntity（服务器玩家实体）

| 版本范围 | 完整类名 |
|---------|---------|
| 1.16.5 - 1.18.2 | `net.minecraft.entity.player.ServerPlayerEntity` |
| 1.19+ | `net.minecraft.server.network.ServerPlayerEntity` |

### 文本组件（Text Component）

| 版本范围 | 接口类名 | 实现类名 | 创建方式 |
|---------|---------|---------|---------|
| 1.16.5 - 1.18.2 | `net.minecraft.util.text.ITextComponent` | `net.minecraft.util.text.StringTextComponent` | `new StringTextComponent(String)` |
| 1.19+ | `net.minecraft.text.Component` | `net.minecraft.text.Text` | `Component.literal(String)` |

## 使用方法

### 1. 获取当前版本

```java
import com.xiaofan.textapiFabric1_16_5.api.mappings.VersionMappings;

VersionMappings.MinecraftVersion version = VersionMappings.getCurrentVersion();
```

### 2. 解析版本字符串

```java
VersionMappings.MinecraftVersion version = VersionMappings.parseVersion("1.19.2");
```

### 3. 获取类名映射

```java
// 获取 ServerPlayerEntity 的完整类名
String playerClass = VersionMappings.getServerPlayerEntityClass(version);

// 获取文本组件接口的完整类名
String textComponentClass = VersionMappings.getTextComponentClass(version);

// 获取字符串文本组件类的完整类名
String stringTextComponentClass = VersionMappings.getStringTextComponentClass(version);
```

### 4. 检查版本类型

```java
// 检查是否为旧版 API（1.16-1.18）
if (VersionMappings.isLegacyVersion(version)) {
    // 使用旧版 API
    import net.minecraft.util.text.ITextComponent;
    import net.minecraft.util.text.StringTextComponent;
}

// 检查是否为新版 API（1.19+）
if (VersionMappings.isModernVersion(version)) {
    // 使用新版 API
    import net.minecraft.text.Component;
}
```

### 5. 获取包路径

```java
// 获取文本组件的包路径
String textPackage = VersionMappings.getTextPackage(version);
// 1.16-1.18: "net.minecraft.util.text"
// 1.19+: "net.minecraft.text"

// 获取玩家实体的包路径
String playerPackage = VersionMappings.getPlayerEntityPackage(version);
// 1.16-1.18: "net.minecraft.entity.player"
// 1.19+: "net.minecraft.server.network"
```

## 实际应用示例

### 示例 1: 根据版本创建文本组件

```java
import com.xiaofan.textapiFabric1_16_5.api.mappings.VersionMappings;

VersionMappings.MinecraftVersion version = VersionMappings.getCurrentVersion();

if (VersionMappings.isLegacyVersion(version)) {
    // 1.16.5 - 1.18.2
    import net.minecraft.util.text.ITextComponent;
    import net.minecraft.util.text.StringTextComponent;
    
    ITextComponent component = new StringTextComponent("Hello");
} else {
    // 1.19+
    import net.minecraft.text.Component;
    
    Component component = Component.literal("Hello");
}
```

### 示例 2: 版本信息输出

```java
VersionMappings.MinecraftVersion version = VersionMappings.parseVersion("1.19.2");
String info = VersionMappings.getVersionInfo(version);
System.out.println(info);
```

输出：
```
Minecraft 1.19.2
ServerPlayerEntity: net.minecraft.server.network.ServerPlayerEntity
TextComponent: net.minecraft.text.Component
StringTextComponent: net.minecraft.text.Text
API Type: Modern (1.19+)
```

## 注意事项

1. **编译时映射**: 这个映射表主要用于编译时选择正确的类名。由于 Java 是静态类型语言，你仍然需要为每个版本创建单独的源代码文件或使用条件编译。

2. **运行时检测**: `getCurrentVersion()` 方法默认返回 `V1_16_5`。如果需要运行时检测版本，需要：
   - 通过系统属性传递版本号
   - 从 `fabric.mod.json` 读取版本
   - 使用反射检测 Minecraft 类

3. **Mojang Mappings**: 本映射表基于 Mojang mappings（官方映射）。如果使用 Yarn 或其他映射，类名可能不同。

## 推荐的使用模式

### 模式 1: 为每个版本创建单独的 API 类

```
api/
├── chat/
│   ├── ChatMessageAPI_1_16_5.java    # 1.16.5 专用
│   ├── ChatMessageAPI_1_19_2.java    # 1.19.2 专用
│   └── ChatMessageAPI_1_21_1.java    # 1.21.1 专用
└── player/
    ├── PlayerEventAPI_1_16_5.java
    ├── PlayerEventAPI_1_19_2.java
    └── PlayerEventAPI_1_21_1.java
```

### 模式 2: 使用 Gradle 条件编译

在 `build.gradle` 中根据版本选择不同的源代码目录：

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

## 扩展映射表

如果需要添加更多版本的映射，只需：

1. 在 `MinecraftVersion` 枚举中添加新版本
2. 在 `ClassMapping` 中添加对应的类名常量
3. 在相关方法中添加新的 case 分支

## 相关文件

- `VersionMappings.java` - 版本映射表主类
- `ChatMessageAPI_1_16_5.java` - 聊天消息 API（1.16.5 版本）
- `PlayerEventAPI_1_16_5.java` - 玩家事件 API（1.16.5 版本）
