# Minecraft 版本类名映射表

本文档记录了不同 Minecraft 版本中关键类的完整类名和方法签名差异。

## 类名映射

### ServerPlayerEntity（服务器玩家实体）

| 版本 | 完整类名 |
|------|---------|
| 1.16.5 - 1.18.2 | `net.minecraft.entity.player.ServerPlayerEntity` |
| 1.19+ | `net.minecraft.server.network.ServerPlayerEntity` |

**注意**：在 Mojang mappings 中，1.16.5-1.18.2 和 1.19+ 的包路径不同。

### 文本组件（Text Component）

| 版本 | 接口类名 | 实现类名 | 创建方法 |
|------|---------|---------|---------|
| 1.16.5 - 1.18.2 | `net.minecraft.util.text.ITextComponent` | `net.minecraft.util.text.StringTextComponent` | `new StringTextComponent(String)` |
| 1.19+ | `net.minecraft.text.Component` | `net.minecraft.text.Text` | `Component.literal(String)` |

### 玩家名称获取

| 版本 | 方法签名 |
|------|---------|
| 1.16.5 - 1.18.2 | `player.getName()` 返回 `ITextComponent`，使用 `getString()` 获取字符串 |
| 1.19+ | `player.getName()` 返回 `Component`，使用 `getString()` 获取字符串 |

## 方法签名映射

### sendMessage（发送消息）

| 版本 | 方法签名 |
|------|---------|
| 1.16.5 - 1.18.2 | `sendMessage(ITextComponent component)` |
| 1.19+ | `sendMessage(Component component)` |

## 使用建议

1. **为每个版本创建单独的 API 类**：
   - `ChatMessageAPI_1_16_5.java` - 1.16.5 专用
   - `ChatMessageAPI_1_19_2.java` - 1.19.2 专用
   - 等等

2. **使用版本映射表**：
   - 创建 `VersionMappings.java` 存储所有版本的类名映射
   - 在编译时根据 `gradle.properties` 中的 `minecraft_version` 选择正确的类

3. **Gradle 条件编译**：
   - 使用 Gradle 的 source sets 为不同版本创建不同的源代码目录
   - 例如：`src/main/java-1.16.5/` 和 `src/main/java-1.19.2/`

## 示例代码

### 1.16.5 版本
```java
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

ITextComponent component = new StringTextComponent("Hello");
player.sendMessage(component);
```

### 1.19+ 版本
```java
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Component;

Component component = Component.literal("Hello");
player.sendMessage(component);
```

## 版本检测

使用 `VersionMappings.parseVersion()` 方法从版本字符串解析版本：

```java
String mcVersion = "1.16.5";
MinecraftVersion version = VersionMappings.parseVersion(mcVersion);

if (VersionMappings.isLegacyVersion(version)) {
    // 使用旧版 API
} else if (VersionMappings.isModernVersion(version)) {
    // 使用新版 API
}
```
