# 快速开始 - 版本化构建

## 快速构建命令

### 构建特定版本

```bash
# 1.16.5
gradlew build1165

# 1.17.1
gradlew build1171

# 1.18.2
gradlew build182

# 1.19.2
gradlew build192

# 1.20.1
gradlew build1201

# 1.21.1
gradlew build1211

# 1.21.2
gradlew build1212

# 1.21.3
gradlew build1213

# 1.21.4
gradlew build1214

# 1.21.5
gradlew build1215

# 1.21.6
gradlew build1216

# 1.21.7
gradlew build1217

# 1.21.8
gradlew build1218

# 1.21.9
gradlew build1219

# 1.21.10
gradlew build12110

# 1.21.11
gradlew build21111
```

### 查看所有支持的版本

```bash
gradlew listVersions
```

### 构建所有版本

```bash
gradlew buildAllVersions
```

## 任务命名规则

版本号去掉所有点号：
- `1.16.5` → `build1165`
- `1.17.1` → `build1171`
- `1.18.2` → `build182`
- `1.19.2` → `build192`
- `1.20.1` → `build1201`
- `1.21.1` → `build1211`
- `1.21.11` → `build21111`

## 工作原理

1. 运行 `gradlew build1165` 时，会启动新的 Gradle 进程
2. 新进程接收参数：`-Ptarget_version=1.16.5 -Ptarget_loader_version=0.18.4`
3. `build.gradle` 根据 `target_version` 选择 `src/main/java-1.16/` 源代码目录
4. 使用对应的 Minecraft 1.16.5 和 Fabric Loader 0.18.4 依赖
5. 编译并构建模组

## 构建输出

构建产物保存在：
```
build/libs/textapi-fabric-1-16-5-1.0.jar
```

## 更多信息

- `BUILD_TASKS.md` - 详细的构建任务文档
- `CONDITIONAL_COMPILATION.md` - 条件编译说明
- `VERSIONED_API_SUMMARY.md` - 版本化 API 系统总结
