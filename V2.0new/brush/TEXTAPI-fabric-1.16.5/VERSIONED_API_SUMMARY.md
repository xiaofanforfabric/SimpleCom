# 版本化 API 系统总结

## ✅ 已完成的工作

### 1. 版本映射表系统
- ✅ 创建了 `VersionMappings.java` 版本映射表
- ✅ 支持版本：1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.11
- ✅ 提供了版本检测、类名映射、包路径获取等功能

### 2. 版本化 API 类
为每个版本创建了独立的 API 类：

#### Legacy API (1.16.5 - 1.18.2)
- ✅ `ChatMessageAPI_1_16_5.java` - 1.16.5 版本
- ✅ `ChatMessageAPI_1_17_1.java` - 1.17.1 版本
- ✅ `ChatMessageAPI_1_18_2.java` - 1.18.2 版本
- ✅ `PlayerEventAPI_1_16_5.java` - 1.16.5 版本
- ✅ `PlayerEventAPI_1_17_1.java` - 1.17.1 版本
- ✅ `PlayerEventAPI_1_18_2.java` - 1.18.2 版本

#### Modern API (1.19.2+)
- ✅ `ChatMessageAPI_1_19_2.java` - 1.19.2 版本
- ✅ `ChatMessageAPI_1_20_1.java` - 1.20.1 版本
- ✅ `ChatMessageAPI_1_21_1.java` - 1.21.1 版本
- ✅ `ChatMessageAPI_1_21_11.java` - 1.21.11 版本
- ✅ `PlayerEventAPI_1_19_2.java` - 1.19.2 版本
- ✅ `PlayerEventAPI_1_20_1.java` - 1.20.1 版本
- ✅ `PlayerEventAPI_1_21_1.java` - 1.21.1 版本
- ✅ `PlayerEventAPI_1_21_11.java` - 1.21.11 版本

### 3. 条件编译配置
- ✅ 在 `build.gradle` 中添加了条件编译逻辑
- ✅ 根据 `minecraft_version` 自动选择对应的源代码目录
- ✅ 支持版本：1.16.x, 1.17.x, 1.18.x, 1.19.x, 1.20.x, 1.21.x

### 4. 目录结构
```
src/main/
├── java/                          # 通用代码
│   └── com/xiaofan/textapiFabric1_16_5/
│       ├── api/
│       │   ├── mappings/
│       │   │   └── VersionMappings.java
│       │   └── ExampleUsage.java
│       └── TextapiFabric1_16_5.java
├── java-1.16/                     # 1.16.5 专用
├── java-1.17/                     # 1.17.1 专用
├── java-1.18/                     # 1.18.2 专用
├── java-1.19/                     # 1.19.2 专用
├── java-1.20/                     # 1.20.1 专用
└── java-1.21/                     # 1.21.1 和 1.21.11 专用
```

## 使用方法

### 步骤 1: 设置目标版本

在 `gradle.properties` 中设置：
```properties
minecraft_version=1.16.5  # 或任何支持的版本
```

### 步骤 2: 构建项目

```bash
gradlew build
```

Gradle 会自动：
1. 读取 `minecraft_version`
2. 选择对应的源代码目录（如 `java-1.16`）
3. 编译对应版本的 API 类

### 步骤 3: 使用 API

根据编译的版本使用对应的 API：

**Legacy (1.16-1.18):**
```java
import com.xiaofan.textapiFabric1_16_5.api.chat.ChatMessageAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
```

**Modern (1.19+):**
```java
import com.xiaofan.textapiFabric1_16_5.api.chat.ChatMessageAPI_1_19_2;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Component;
```

## 版本差异对照表

| 特性 | Legacy (1.16-1.18) | Modern (1.19+) |
|------|-------------------|----------------|
| ServerPlayerEntity | `net.minecraft.entity.player.ServerPlayerEntity` | `net.minecraft.server.network.ServerPlayerEntity` |
| 文本组件接口 | `net.minecraft.util.text.ITextComponent` | `net.minecraft.text.Component` |
| 文本组件实现 | `net.minecraft.util.text.StringTextComponent` | `Component.literal()` |
| 创建文本 | `new StringTextComponent(String)` | `Component.literal(String)` |

## 文件清单

### 版本映射表
- `src/main/java/com/xiaofan/textapiFabric1_16_5/api/mappings/VersionMappings.java`

### Legacy API (1.16-1.18)
- `src/main/java-1.16/.../ChatMessageAPI_1_16_5.java`
- `src/main/java-1.16/.../PlayerEventAPI_1_16_5.java`
- `src/main/java-1.17/.../ChatMessageAPI_1_17_1.java`
- `src/main/java-1.17/.../PlayerEventAPI_1_17_1.java`
- `src/main/java-1.18/.../ChatMessageAPI_1_18_2.java`
- `src/main/java-1.18/.../PlayerEventAPI_1_18_2.java`

### Modern API (1.19+)
- `src/main/java-1.19/.../ChatMessageAPI_1_19_2.java`
- `src/main/java-1.19/.../PlayerEventAPI_1_19_2.java`
- `src/main/java-1.20/.../ChatMessageAPI_1_20_1.java`
- `src/main/java-1.20/.../PlayerEventAPI_1_20_1.java`
- `src/main/java-1.21/.../ChatMessageAPI_1_21_1.java`
- `src/main/java-1.21/.../ChatMessageAPI_1_21_11.java`
- `src/main/java-1.21/.../PlayerEventAPI_1_21_1.java`
- `src/main/java-1.21/.../PlayerEventAPI_1_21_11.java`

### 配置文件
- `build.gradle` - 条件编译配置
- `gradle.properties` - 版本配置
- `CONDITIONAL_COMPILATION.md` - 条件编译使用指南
- `VERSION_MAPPINGS_GUIDE.md` - 版本映射表使用指南

## 优势

1. **类型安全**: 每个版本使用正确的类名，编译时检查
2. **无反射**: 直接调用 Minecraft API，性能最佳
3. **易于维护**: 每个版本的代码独立，互不干扰
4. **自动选择**: Gradle 根据配置自动选择正确的源代码
5. **易于扩展**: 添加新版本只需创建新目录和类

## 下一步

1. ✅ 所有版本的 API 类已创建
2. ✅ 条件编译已配置
3. ✅ 版本映射表已完善
4. ⏭️ 测试各个版本的编译
5. ⏭️ 根据需要添加更多版本的 API 类

## 相关文档

- `CONDITIONAL_COMPILATION.md` - 条件编译详细说明
- `VERSION_MAPPINGS_GUIDE.md` - 版本映射表使用指南
- `api/mappings/MAPPINGS_README.md` - 映射表 API 文档
