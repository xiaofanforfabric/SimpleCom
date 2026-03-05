# SimpleCom / 简单的通讯器

---

## Overview / 概述

### English

**SimpleCom** is a Minecraft voice communication mod that enables in-game push-to-talk voice chat. It uses **Minecraft's built-in plugin message (payload) channels** instead of traditional UDP-based voice solutions, making it work reliably across complex network environments.

### 中文

**SimpleCom（简单的通讯器）** 是一款 Minecraft 语音对讲模组，支持游戏内按键说话。它使用 **Minecraft 自带的插件消息（payload）通道** 传输语音，而非传统的基于 UDP 的方案，从而在复杂网络环境下也能稳定工作。

---

## Motivation: Solving UDP's NAT Problems / 初衷：解决 UDP 的 NAT 问题

### English

Traditional voice mods typically use **UDP** for real-time audio streaming. However, UDP faces severe challenges in real-world networks:

| Problem | Description |
|---------|-------------|
| **Complex NAT** | Symmetric NAT, carrier-grade NAT, and multiple NAT layers can cause UDP packets to be dropped entirely or never reach their destination |
| **Single-port limitation** | UDP often requires opening additional firewall ports; many home/enterprise networks block or restrict this |
| **Connection failures** | P2P UDP hole-punching fails when both clients are behind strict NAT, making direct connections impossible |

**SimpleCom** takes a different approach: it piggybacks voice data on the **existing Minecraft TCP connection** via plugin message channels. Since players are already connected to the server, no extra ports or NAT traversal are needed. Voice data flows: **Client → Server → Other clients in the same channel**.

### 中文

传统语音模组通常使用 **UDP** 进行实时音频传输。但在实际网络中，UDP 面临诸多问题：

| 问题 | 说明 |
|------|------|
| **复杂 NAT** | 对称型 NAT、运营商级 NAT、多层 NAT 等会导致 UDP 包全部丢失或根本无法到达目标 |
| **单端口限制** | UDP 往往需要额外开放防火墙端口，许多家庭/企业网络会阻止或限制 |
| **连接失败** | 当双方都在严格 NAT 后时，UDP 打洞失败，无法建立直连 |

**SimpleCom** 采用不同思路：将语音数据通过 **已有的 Minecraft TCP 连接** 上的插件消息通道传输。玩家本就已连接服务器，无需额外端口或 NAT 穿透。语音流向：**客户端 → 服务器 → 同信道其他客户端**。

---

## Trade-off: Real-time vs. Reliability / 取舍：实时性 vs 可靠性

### English

| Aspect | UDP-based voice | SimpleCom (payload) |
|--------|-----------------|----------------------|
| **Latency** | Lower (direct P2P when possible) | Higher (server relay) |
| **Reliability** | Packet loss, connection failures | Uses existing game connection |
| **NAT/Firewall** | Often blocked or unreliable | Works wherever Minecraft works |
| **Setup** | May need port forwarding | Zero extra configuration |

**We sacrifice real-time performance for reliability.** Voice is transmitted through the game server, so there is additional latency compared to direct UDP. For casual in-game communication where reliability matters more than millisecond-level delay, this trade-off is acceptable.

### 中文

| 方面 | 基于 UDP 的语音 | SimpleCom（payload） |
|------|-----------------|----------------------|
| **延迟** | 较低（直连时） | 较高（经服务器转发） |
| **可靠性** | 易丢包、连接失败 | 复用游戏连接，稳定 |
| **NAT/防火墙** | 常被阻或不可靠 | 能进游戏就能用 |
| **配置** | 可能需要端口转发 | 零额外配置 |

**我们以牺牲实时性换取可靠性。** 语音经游戏服务器转发，相比直连 UDP 会有更高延迟。对于以稳定沟通为主、对毫秒级延迟不敏感的场景，这一取舍是可接受的。

---

## Features / 功能特性

### English

- **Push-to-talk**: Hold **V** to record, release to send
- **Channel system**: 100 channels (1–100); channel 0 = mute. Press **C** to open the channel settings GUI
- **Multi-loader**: Single JAR works on both **Forge** and **Fabric** (1.16.5)
- **Server plugin**: Bukkit/Spigot plugin relays voice between players in the same channel
- **Protocol**: VarInt + UTF-8, compatible with Minecraft `PacketByteBuf` format

### 中文

- **按键说话**：按住 **V** 录音，松开发送
- **信道系统**：100 个信道（1–100）；信道 0 为静音。按 **C** 打开信道设置界面
- **多加载器**：单个 JAR 同时支持 **Forge** 和 **Fabric**（1.16.5）
- **服务端插件**：Bukkit/Spigot 插件在同信道玩家间转发语音
- **协议**：VarInt + UTF-8，与 Minecraft `PacketByteBuf` 格式兼容

---

## Project Structure / 项目结构

### English

```
SimpleCom/
├── 1.16.5forge-fabric/     # Client mod (Forge + Fabric, Architectury)
│   ├── common/             # Shared logic
│   ├── forge/              # Forge platform
│   └── fabric/             # Fabric platform
├── bukkitserver/           # Bukkit/Spigot server plugin
├── clientcommen/           # Client shared (voice codec, config)
└── servercommen/            # Server shared (channels, VarInt utils)
```

### 中文

```
SimpleCom/
├── 1.16.5forge-fabric/     # 客户端模组（Forge + Fabric，Architectury）
│   ├── common/             # 共享逻辑
│   ├── forge/              # Forge 平台
│   └── fabric/             # Fabric 平台
├── bukkitserver/           # Bukkit/Spigot 服务端插件
├── clientcommen/           # 客户端共享（语音编解码、配置）
└── servercommen/           # 服务端共享（通道常量、VarInt 工具）
```

---

## Build / 构建

### English

**Recommended**: `cd` into the client mod directory and use the standard `build` task:

```bash
cd 1.16.5forge-fabric
./gradlew build
# Output: forge/build/libs/simplecom-forge-1.0.0.jar, fabric/build/libs/simplecom-fabric-1.0.0.jar
# Or run mergeJars for a single merged JAR: build/libs/simplecom-1.0.0-merged.jar
```

> Note: The root task `build1.16.5client` may have issues; prefer building from within the project directory.

### 中文

**推荐**：进入客户端模组目录后使用标准 `build` 任务：

```bash
cd 1.16.5forge-fabric
./gradlew build
# 输出：forge/build/libs/simplecom-forge-1.0.0.jar、fabric/build/libs/simplecom-fabric-1.0.0.jar
# 或执行 mergeJars 得到合并 JAR：build/libs/simplecom-1.0.0-merged.jar
```

> 说明：根目录的 `build1.16.5client` 任务可能存在问题，建议在项目目录内构建。

---

## Installation / 安装

### English

1. **Client**: Place `simplecom-1.0.0.jar` in `.minecraft/mods/` (requires Architectury)
2. **Server**: Install the Bukkit/Spigot plugin on your server

### 中文

1. **客户端**：将 `simplecom-1.0.0.jar` 放入 `.minecraft/mods/`（需安装 Architectury）
2. **服务端**：在服务器上安装 Bukkit/Spigot 插件

---

## Donate / 捐赠

### English

**Cryptocurrency (USDT):**

- **USDT on TRON:** `TDoJuuSBTztZFVQuVC7xeEESfFbktBUb9W`
- **USDT on Ethereum:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`
- **USDT on BNB Chain:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`
- **USDT on Polygon:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`
- **USDT on X Layer:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`

Thank you for your support.

### 中文

**微信 / 支付宝：** 占位，后续会上传收款二维码。

感谢支持。

---

## License / 许可证

GNU LESSER GENERAL PUBLIC LICENSE
