# 条件编译使用指南

## 概述

TEXTAPI 使用 Gradle 条件编译来根据 `gradle.properties` 中的 `minecraft_version` 自动选择对应版本的源代码。

## 支持的版本

- **1.16.5** - Legacy API (使用 `ITextComponent`)
- **1.17.1** - Legacy API (使用 `ITextComponent`)
- **1.18.2** - Legacy API (使用 `ITextComponent`)
- **1.19.2** - Modern API (使用 `Component`)
- **1.20.1** - Modern API (使用 `Component`)
- **1.21.1** - Modern API (使用 `Component`)
- **1.21.11** - Modern API (使用 `Component`)

## 目录结构

```
src/
├── main/
│   ├── java/                          # 通用代码（VersionMappings 等）
│   ├── java-1.16/                     # 1.16.5 专用代码
│   │   └── com/xiaofan/textapiFabric1_16_5/api/
│   │       ├── chat/
│   │       │   └── ChatMessageAPI_1_16_5.java
│   │       └── player/
│   │           └── PlayerEventAPI_1_16_5.java
│   ├── java-1.17/                     # 1.17.1 专用代码
│   ├── java-1.18/                     # 1.18.2 专用代码
│   ├── java-1.19/                     # 1.19.2 专用代码
│   ├── java-1.20/                     # 1.20.1 专用代码
│   └── java-1.21/                     # 1.21.1 和 1.21.11 专用代码
```

## 使用方法

### 1. 修改 gradle.properties

在 `gradle.properties` 中设置目标 Minecraft 版本：

```properties
minecraft_version=1.16.5  # 或 1.17.1, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.11
```

### 2. 构建项目

Gradle 会自动根据 `minecraft_version` 选择对应的源代码目录：

```bash
gradlew build
```

### 3. 使用对应的 API

根据编译的版本，使用对应的 API 类：

**Legacy API (1.16.5-1.18.2):**
```java
import com.xiaofan.textapiFabric1_16_5.api.chat.ChatMessageAPI_1_16_5;
import com.xiaofan.textapiFabric1_16_5.api.player.PlayerEventAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
```

**Modern API (1.19.2+):**
```java
import com.xiaofan.textapiFabric1_16_5.api.chat.ChatMessageAPI_1_19_2;
import com.xiaofan.textapiFabric1_16_5.api.player.PlayerEventAPI_1_19_2;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Component;
```

## Build.gradle 配置说明

条件编译配置在 `build.gradle` 中：

```gradle
// 根据 Minecraft 版本选择源代码目录
def minecraftVersion = project.minecraft_version
def sourceDir = "src/main/java"

if (minecraftVersion.startsWith("1.16")) {
    sourceDir = "src/main/java-1.16"
} else if (minecraftVersion.startsWith("1.17")) {
    sourceDir = "src/main/java-1.17"
} else if (minecraftVersion.startsWith("1.18")) {
    sourceDir = "src/main/java-1.18"
} else if (minecraftVersion.startsWith("1.19")) {
    sourceDir = "src/main/java-1.19"
} else if (minecraftVersion.startsWith("1.20")) {
    sourceDir = "src/main/java-1.20"
} else if (minecraftVersion.startsWith("1.21")) {
    sourceDir = "src/main/java-1.21"
}

sourceSets {
    main {
        java {
            srcDirs = [sourceDir, "src/main/java"] // 包含版本特定目录和通用目录
        }
    }
}
```

## 版本差异

### Legacy API (1.16.5 - 1.18.2)

- **ServerPlayerEntity**: `net.minecraft.entity.player.ServerPlayerEntity`
- **文本组件**: `net.minecraft.util.text.ITextComponent`
- **创建方式**: `new StringTextComponent(String)`

### Modern API (1.19.2+)

- **ServerPlayerEntity**: `net.minecraft.server.network.ServerPlayerEntity`
- **文本组件**: `net.minecraft.text.Component`
- **创建方式**: `Component.literal(String)`

## 示例

### 示例 1: 编译 1.16.5 版本

1. 修改 `gradle.properties`:
   ```properties
   minecraft_version=1.16.5
   ```

2. 构建:
   ```bash
   gradlew build
   ```

3. 代码会自动使用 `src/main/java-1.16/` 中的类

### 示例 2: 编译 1.19.2 版本

1. 修改 `gradle.properties`:
   ```properties
   minecraft_version=1.19.2
   ```

2. 构建:
   ```bash
   gradlew build
   ```

3. 代码会自动使用 `src/main/java-1.19/` 中的类

## 注意事项

1. **版本匹配**: 确保 `gradle.properties` 中的版本与源代码目录匹配
2. **类名差异**: Legacy 和 Modern API 使用不同的类名，不能混用
3. **通用代码**: `src/main/java/` 中的代码（如 VersionMappings）会被所有版本使用
4. **IDE 支持**: 某些 IDE 可能需要刷新项目才能识别新的源代码目录

## 扩展支持新版本

要添加新版本支持：

1. 创建新的源代码目录（如 `src/main/java-1.22/`）
2. 在新目录中创建对应版本的 API 类
3. 在 `build.gradle` 中添加版本判断逻辑
4. 更新 `VersionMappings.java` 添加新版本枚举
