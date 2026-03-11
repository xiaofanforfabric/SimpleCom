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

## Version Compatibility / 版本兼容

### English

**Each release version is incompatible with other versions.** Client and server must use the same version.

- **V2.0** server (plugin/mod) only works with **V2.0** client. This version adds the **Voice Stream API (WSAPI)** and introduces a shared server core for modern platforms.
- **V1.1** server (mod or Bukkit plugin) only works with **V1.1** client.
- **V0.1** server only worked with **V1.0** client.

Do not mix versions (e.g. V2.0 client on a V1.1 server will not work).

### 中文

**每个版本与其它版本不兼容。** 客户端与服务端必须使用相同版本。

- **V2.0** 服务端（插件/模组）仅兼容 **V2.0** 客户端。本版本新增 **语音流 API（WSAPI）**，并为新平台引入统一的服务端核心库。
- **V1.1** 服务端（模组或 Bukkit 插件）仅兼容 **V1.1** 客户端。
- **V0.1** 服务端仅兼容 **V1.0** 客户端。

请勿混用版本（例如 V2.0 客户端连接 V1.1 服务端将无法正常工作）。

---

## What's New in V2.0 / V2.0 更新内容

### English

- **New shared server core (`SimpleCom-server-core`)**  
  A pure-Java core library that implements the port-multiplex proxy and WebSocket server used by Bukkit, Fabric and other server platforms, reducing duplicated logic and making maintenance easier.

- **Voice Stream API (WSAPI)**  
  A new, token-protected WebSocket endpoint that lets external services (e.g. KOOK/Discord bridges, bots, recording tools) subscribe to real-time voice packets from **non-encrypted channels**.  
  See `V2.0new/WSAPI.md` for the full protocol documentation.

- **Unified version string `V2.0`**  
  All main modules in the `V2.0new` branch (client, server, core) now use the same version string **`V2.0`**, simplifying deployment, bug reports and modpack integration.

### 中文

- **统一的服务端核心库（`SimpleCom-server-core`）**  
  将端口复用代理与 WebSocket 服务器实现抽取为独立的纯 Java 核心库，由 Bukkit 插件、Fabric 模组等多种平台共用，减少重复代码、方便后续维护与扩展。

- **语音流 API（WSAPI）**  
  新增带 Token 鉴权的 WebSocket 接口，允许外部服务（例如 KOOK / Discord 语音桥接、机器人、录音工具）实时订阅来自 **非加密信道** 的语音数据包。  
  详细协议说明见 `V2.0new/WSAPI.md`。

- **版本号统一为 `V2.0`**  
  `V2.0new` 分支下的主要模块（客户端、服务端、核心库）统一使用 **V2.0** 版本号，便于部署、多端排错以及整合进整合包。

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

#### V1.x client mod (payload channel)

**Recommended**: `cd` into the old client mod directory and use the standard `build` task:

```bash
cd 1.16.5forge-fabric
./gradlew build
# Output: forge/build/libs/simplecom-forge-1.1.jar, fabric/build/libs/simplecom-fabric-1.1.jar
# Or run mergeJars for a single merged JAR: build/libs/simplecom-1.1-merged.jar
```

> Note: The root task `build1.16.5client` may have issues; prefer building from within the project directory.

#### V2.0 FX WebSocket client

For **V2.0**, players should **download and use the released client package**.

If you are a developer (or you want to build from source), the V2.0 client can also be built as a standalone **JavaFX GUI** app that connects over WebSocket to the server’s proxy listen-port:

```bash
cd V2.0new
./gradlew :SimpleCom-core:shadowJar
# Output: SimpleCom-core/build/libs/SimpleCom-standalone-V2.0.jar
```

Then run it with Java 11+:

```bash
java -jar SimpleCom-core/build/libs/SimpleCom-standalone-V2.0.jar
```

### 中文

#### V1.x 客户端模组（payload 通道）

**推荐**：进入旧版客户端模组目录后使用标准 `build` 任务：

```bash
cd 1.16.5forge-fabric
./gradlew build
# 输出：forge/build/libs/simplecom-forge-1.1.jar、fabric/build/libs/simplecom-fabric-1.1.jar
# 或执行 mergeJars 得到合并 JAR：build/libs/simplecom-1.1-merged.jar
```

> 说明：根目录的 `build1.16.5client` 任务可能存在问题，建议在项目目录内构建。

#### V2.0 FX WebSocket 客户端

在 **V2.0** 中，玩家请直接**下载并使用发布的客户端包**。

如果你是开发者（或希望自行编译），也可以把 V2.0 客户端构建为独立的 **JavaFX 图形客户端**，通过 WebSocket 连接到服务端的代理监听端口：

```bash
cd V2.0new
./gradlew :SimpleCom-core:shadowJar
# 输出：SimpleCom-core/build/libs/SimpleCom-standalone-V2.0.jar
```

然后使用 Java 11+ 运行：

```bash
java -jar SimpleCom-core/build/libs/SimpleCom-standalone-V2.0.jar
```

---

## Installation / 安装

### English

#### V1.x (payload-based in-game client)

1. **Client**: Place `simplecom-1.1.jar` in `.minecraft/mods/` (requires Architectury)
2. **Server**: Install the Bukkit/Spigot plugin on your server

#### V2.0 (WebSocket + FX GUI + proxy)

1. **Server**:
   - Install the **V2.0 SimpleCom server plugin/mod**.
   - Configure `SimpleComConfig/config.yml` so that the **port-multiplex proxy** and internal WebSocket server are running (players and the FX client connect to the proxy listen-port).
2. **Client**:
   - Install/run the **released V2.0 client package** (players do not need to build from source).
   - In Minecraft, join the server and get the 6-digit verification code from chat.
   - In the client UI (FX GUI), enter your username, server address (e.g. `example.com:25566`), and the verification code, then click **Connect**.

### 中文

#### V1.x（游戏内 payload 客户端）

1. **客户端**：将 `simplecom-1.1.jar` 放入 `.minecraft/mods/`（需安装 Architectury）
2. **服务端**：在服务器上安装 Bukkit/Spigot 插件

#### V2.0（WebSocket + FX 图形界面 + 代理端口复用）

1. **服务端**：
   - 安装 **V2.0 SimpleCom 服务端插件/模组**。
   - 在 `SimpleComConfig/config.yml` 中启用 **端口复用代理** 与内部 WebSocket 服务器（玩家和 FX 客户端都连到这个代理监听端口）。
2. **客户端**：
   - 直接安装/运行 **V2.0 客户端发布包**（玩家无需自己构建）。
   - 在 Minecraft 中进入服务器，从聊天栏获得 6 位验证码。
   - 在客户端界面（FX GUI）中输入用户名、服务器地址（如 `example.com:25566`）、验证码，然后点击“连接/Connect”。

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

**微信 / 支付宝：** 
<img width="943" height="943" alt="mm_reward_qrcode_1771753353154" src="https://github.com/user-attachments/assets/f3feb475-ca97-4678-a1b7-47e5abeadda4" />
![1767020347650](https://github.com/user-attachments/assets/7c1fcbae-6a37-46e1-ab6d-90c2601bf0e6)


感谢支持。

---

## License / 许可证

GNU LESSER GENERAL PUBLIC LICENSE
