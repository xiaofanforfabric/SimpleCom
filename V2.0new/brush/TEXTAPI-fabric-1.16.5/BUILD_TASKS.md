# 版本化构建任务使用指南

## 概述

TEXTAPI 为每个支持的 Minecraft 版本创建了独立的构建任务，可以通过简单的命令构建特定版本。

## 支持的版本和任务

| 版本 | 构建任务 | 源代码目录 | Fabric Loader |
|------|---------|-----------|--------------|
| 1.16.5 | `build1165` | `java-1.16` | 0.18.4 |
| 1.17 | `build117` | `java-1.17` | 0.11.7 |
| 1.17.1 | `build1171` | `java-1.17` | 0.11.7 |
| 1.18 | `build118` | `java-1.18` | 0.12.12 |
| 1.18.1 | `build1181` | `java-1.18` | 0.12.12 |
| 1.18.2 | `build182` | `java-1.18` | 0.13.3 |
| 1.19 | `build119` | `java-1.19` | 0.14.10 |
| 1.19.1 | `build1191` | `java-1.19` | 0.14.10 |
| 1.19.2 | `build192` | `java-1.19` | 0.14.21 |
| 1.19.3 | `build193` | `java-1.19` | 0.14.23 |
| 1.19.4 | `build194` | `java-1.19` | 0.14.25 |
| 1.20 | `build120` | `java-1.20` | 0.14.22 |
| 1.20.1 | `build1201` | `java-1.20` | 0.14.22 |
| 1.20.2 | `build1202` | `java-1.20` | 0.14.24 |
| 1.20.4 | `build1204` | `java-1.20` | 0.14.26 |
| 1.20.6 | `build1206` | `java-1.20` | 0.15.7 |
| 1.21 | `build121` | `java-1.21` | 0.16.0 |
| 1.21.1 | `build1211` | `java-1.21` | 0.16.0 |
| 1.21.2 | `build1212` | `java-1.21` | 0.16.0 |
| 1.21.3 | `build1213` | `java-1.21` | 0.16.0 |
| 1.21.4 | `build1214` | `java-1.21` | 0.16.0 |
| 1.21.5 | `build1215` | `java-1.21` | 0.16.0 |
| 1.21.6 | `build1216` | `java-1.21` | 0.16.0 |
| 1.21.7 | `build1217` | `java-1.21` | 0.16.0 |
| 1.21.8 | `build1218` | `java-1.21` | 0.16.0 |
| 1.21.9 | `build1219` | `java-1.21` | 0.16.0 |
| 1.21.10 | `build12110` | `java-1.21` | 0.16.0 |
| 1.21.11 | `build21111` | `java-1.21` | 0.16.0 |

## 使用方法

### 方法 1: 使用版本化构建任务（推荐）

```bash
# 构建 1.16.5 版本
gradlew build1165

# 构建 1.17.1 版本
gradlew build1171

# 构建 1.18.2 版本
gradlew build182

# 构建 1.19.2 版本
gradlew build192

# 构建 1.20.1 版本
gradlew build1201

# 构建 1.21.1 版本
gradlew build1211

# 构建 1.21.11 版本
gradlew build21111
```

### 方法 2: 使用命令行参数

```bash
# 通过命令行参数指定版本
gradlew build -Ptarget_version=1.16.5 -Ptarget_loader_version=0.18.4
gradlew build -Ptarget_version=1.19.2 -Ptarget_loader_version=0.14.21
gradlew build -Ptarget_version=1.21.11 -Ptarget_loader_version=0.16.0
```

### 方法 3: 修改 gradle.properties

在 `gradle.properties` 中设置版本，然后运行 `gradlew build`：

```properties
minecraft_version=1.16.5
loader_version=0.18.4
```

## 特殊任务

### 列出所有支持的版本

```bash
gradlew listVersions
```

输出示例：
```
========================================
支持的 Minecraft 版本:
========================================
  build1165        - Minecraft 1.16.5   (Loader 0.18.4)
  build117         - Minecraft 1.17     (Loader 0.11.7)
  build1171        - Minecraft 1.17.1   (Loader 0.11.7)
  ...
========================================
```

### 构建所有版本

```bash
gradlew buildAllVersions
```

这会依次构建所有支持的版本。

## 工作原理

1. **版本化构建任务**: 每个 `buildXXX` 任务会：
   - 设置 `target_version` 项目属性
   - 设置 `target_loader_version` 项目属性
   - 执行 `build` 任务

2. **条件编译**: `build.gradle` 会根据 `target_version`（如果存在）或 `minecraft_version` 选择：
   - 正确的源代码目录（`java-1.16`, `java-1.17`, 等）
   - 正确的 Minecraft 版本依赖
   - 正确的 Fabric Loader 版本

3. **源代码选择**: 
   - Legacy API (1.16-1.18): 使用 `java-1.16`, `java-1.17`, `java-1.18`
   - Modern API (1.19+): 使用 `java-1.19`, `java-1.20`, `java-1.21`

## 示例

### 示例 1: 构建 1.16.5 版本

```bash
gradlew build1165
```

这会：
1. 使用 `src/main/java-1.16/` 中的源代码
2. 使用 Minecraft 1.16.5 依赖
3. 使用 Fabric Loader 0.18.4

### 示例 2: 构建 1.19.2 版本

```bash
gradlew build192
```

这会：
1. 使用 `src/main/java-1.19/` 中的源代码
2. 使用 Minecraft 1.19.2 依赖
3. 使用 Fabric Loader 0.14.21

### 示例 3: 构建 1.21.11 版本

```bash
gradlew build21111
```

这会：
1. 使用 `src/main/java-1.21/` 中的源代码
2. 使用 Minecraft 1.21.11 依赖
3. 使用 Fabric Loader 0.16.0

## 注意事项

1. **任务名称**: 任务名称是版本号去掉点号，例如 `1.16.5` → `build1165`
2. **源代码目录**: 每个版本使用对应的源代码目录，确保代码正确
3. **依赖版本**: 每个版本使用对应的 Fabric Loader 版本
4. **构建输出**: 构建产物会保存在 `build/libs/` 目录

## 故障排除

### 问题: 找不到源代码目录

**解决方案**: 确保对应的源代码目录存在：
- `src/main/java-1.16/` 用于 1.16.x
- `src/main/java-1.17/` 用于 1.17.x
- 等等

### 问题: 依赖版本不匹配

**解决方案**: 检查 `build.gradle` 中的 `versionConfigs` 配置，确保 Fabric Loader 版本正确。

### 问题: 构建失败

**解决方案**: 
1. 检查 `gradle.properties` 中的配置
2. 确保对应的源代码目录中有正确的 API 类
3. 运行 `gradlew listVersions` 查看支持的版本

## 扩展支持新版本

要添加新版本支持：

1. 在 `build.gradle` 的 `versionConfigs` 中添加新版本配置
2. 创建对应的源代码目录（如 `src/main/java-1.22/`）
3. 在新目录中创建对应版本的 API 类
4. 运行 `gradlew listVersions` 验证新任务已创建
