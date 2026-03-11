# TEXTAPI - 跨版本抽象 API (1.16.5)

## 概述

TEXTAPI 是一个纯抽象 API 库，提供跨版本的玩家事件监听和聊天消息发送功能。

**当前版本**: 1.16.5  
**支持平台**: Fabric、Forge、NeoForge  
**依赖**: 仅依赖 Minecraft 1.16.5 原生类，不依赖任何第三方库

## 特性

- ✅ **直接调用**: 直接使用 1.16.5 的 Minecraft API，不使用反射
- ✅ **跨平台支持**: Fabric、Forge、NeoForge
- ✅ **零第三方依赖**: 仅依赖 Minecraft 原生类
- ✅ **类型安全**: 使用 `ServerPlayerEntity` 和 `ITextComponent` 等具体类型

## 快速开始

### 1. 初始化

**Fabric:**
```java
import com.xiaofan.textapi.Textapi;
import com.xiaofan.textapi.fabric.TextapiFabric;

public class YourMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Textapi.init();
        
        // 注册你的监听器
        registerListeners();
    }
}
```

**Forge:**
```java
import com.xiaofan.textapi.Textapi;
import net.minecraftforge.fml.common.Mod;

@Mod("yourmod")
public class YourMod {
    public YourMod() {
        Textapi.init();
        
        // 注册你的监听器
        registerListeners();
    }
}
```

### 2. 使用玩家事件 API

```java
import com.xiaofan.textapi.api.player.PlayerEventAPI_1_16_5;
import com.xiaofan.textapi.api.chat.ChatMessageAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;

// 注册玩家加入事件
PlayerEventAPI_1_16_5.onPlayerJoin(player -> {
    String name = player.getName().getString();
    System.out.println("玩家加入: " + name);
    
    // 发送欢迎消息
    ChatMessageAPI_1_16_5.sendMessage(player, "§6欢迎加入服务器！");
});

// 注册玩家退出事件
PlayerEventAPI_1_16_5.onPlayerQuit(player -> {
    String name = player.getName().getString();
    System.out.println("玩家退出: " + name);
});
```

### 3. 使用聊天消息 API

```java
import com.xiaofan.textapi.api.chat.ChatMessageAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;

// 发送单条消息
ChatMessageAPI_1_16_5.sendMessage(player, "§6这是一条消息");

// 发送多条消息
ChatMessageAPI_1_16_5.sendMessages(player,
    "§6━━━━━━━ §b标题§6 ━━━━━━━",
    "§e消息内容",
    "§7支持 § 颜色代码",
    "§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
);

// 发送 ITextComponent
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

ITextComponent component = new StringTextComponent("自定义消息");
ChatMessageAPI_1_16_5.sendMessage(player, component);
```

## API 文档

### PlayerEventAPI_1_16_5

#### `onPlayerJoin(Consumer<ServerPlayerEntity> listener)`
注册玩家加入事件监听器。

#### `onPlayerQuit(Consumer<ServerPlayerEntity> listener)`
注册玩家退出事件监听器。

#### `firePlayerJoin(ServerPlayerEntity player)` / `firePlayerQuit(ServerPlayerEntity player)`
触发玩家事件（由平台特定代码调用）。

### ChatMessageAPI_1_16_5

#### `sendMessage(ServerPlayerEntity player, String message)`
向玩家发送单条聊天消息。

#### `sendMessages(ServerPlayerEntity player, String... messages)`
向玩家发送多条聊天消息。

#### `sendMessage(ServerPlayerEntity player, ITextComponent component)`
向玩家发送 ITextComponent。

## 版本信息

- **Minecraft 版本**: 1.16.5
- **文本组件**: `net.minecraft.util.text.StringTextComponent`
- **玩家类型**: `net.minecraft.entity.player.ServerPlayerEntity`

## 平台支持

- **Fabric**: 使用 `ServerPlayConnectionEvents`，在 `TextapiFabric` 中自动注册
- **Forge**: 使用 `PlayerEvent.PlayerLoggedInEvent/OutEvent`，在 `TextapiForge` 中手动注册

## 依赖

- **运行时**: 仅依赖 Minecraft 1.16.5 原生类
- **Fabric**: 需要 Fabric API（用于 `ServerPlayConnectionEvents`）
- **构建时**: Architectury Loom（仅用于构建，不包含在运行时）

## 许可证

MIT License
