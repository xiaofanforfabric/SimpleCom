# TEXTAPI API - 1.16.5 版本

## 概述

TEXTAPI 提供跨平台的玩家事件监听和聊天消息发送 API，直接使用 Minecraft 1.16.5 的原生 API。

## API 使用

### 玩家事件

```java
import com.xiaofan.textapi.api.player.PlayerEventAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;

// 注册玩家加入事件
PlayerEventAPI_1_16_5.onPlayerJoin(player -> {
    String name = player.getName().getString();
    System.out.println("玩家加入: " + name);
});

// 注册玩家退出事件
PlayerEventAPI_1_16_5.onPlayerQuit(player -> {
    String name = player.getName().getString();
    System.out.println("玩家退出: " + name);
});
```

### 聊天消息

```java
import com.xiaofan.textapi.api.chat.ChatMessageAPI_1_16_5;
import net.minecraft.entity.player.ServerPlayerEntity;

// 发送单条消息
ChatMessageAPI_1_16_5.sendMessage(player, "§6欢迎加入服务器！");

// 发送多条消息
ChatMessageAPI_1_16_5.sendMessages(player,
    "§6━━━━━━━ §b标题§6 ━━━━━━━",
    "§e这是一条消息",
    "§7支持 § 颜色代码"
);

// 发送 ITextComponent
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

ITextComponent component = new StringTextComponent("自定义消息");
ChatMessageAPI_1_16_5.sendMessage(player, component);
```

## 初始化

在模组初始化时调用：

```java
import com.xiaofan.textapi.Textapi;

// Fabric
Textapi.init();

// Forge
Textapi.init();
```

## 版本信息

- **Minecraft 版本**: 1.16.5
- **玩家类型**: `net.minecraft.entity.player.ServerPlayerEntity`
- **文本组件**: `net.minecraft.util.text.StringTextComponent`
- **文本组件接口**: `net.minecraft.util.text.ITextComponent`

## 平台支持

- **Fabric**: 使用 `ServerPlayConnectionEvents`，在 `TextapiFabric` 中自动注册
- **Forge**: 使用 `PlayerEvent.PlayerLoggedInEvent/OutEvent`，在 `TextapiForge` 中手动注册
