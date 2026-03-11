# SimpleCom / 简单的通讯器

## Purpose / 初衷

### English

**Why SimpleCom exists:** Most voice mods use **UDP** for real-time audio. UDP often fails in real networks: symmetric NAT, carrier-grade NAT, firewalls, and strict ISPs can block or drop UDP entirely. Players behind such networks cannot connect or hear each other.

**SimpleCom** sends voice over the **same TCP connection you already use for Minecraft** via plugin message (payload) channels. No extra ports, no NAT traversal, no firewall rules. If you can join the server, voice works. Data flows: **Client → Server → Other clients in the same channel**. We trade a bit of latency for **reliability**: voice works wherever the game works.

### 中文

**SimpleCom 的初衷：** 多数语音模组用 **UDP** 传语音。在实际网络里 UDP 经常失效：对称型 NAT、运营商 NAT、防火墙、严格网络策略都会拦截或丢包，玩家无法连上或听不到对方。

**SimpleCom** 通过 **你已经在用的 Minecraft TCP 连接** 上的插件消息（payload）通道传语音。不需要额外端口、不需要 NAT 穿透、不需要改防火墙。能进服务器，语音就能用。数据路径：**客户端 → 服务器 → 同信道其他客户端**。我们用一点延迟换 **稳定**：游戏能连上的地方，语音就能用。

---

## How to Use / 使用方法

### English

#### V1.x (payload channel inside Minecraft)

1. **Install** the mod (Fabric or Forge) and **Architectury API**. On the server, install the SimpleCom server plugin (Bukkit/Spigot) so it can relay voice.
2. **Join** a server. When the server has the plugin, you’ll see a success message; otherwise you may see a timeout (voice won’t work without the server plugin).
3. **Choose a channel:** Press **C** to open the channel screen. Enter a number 1–100. Channel **0** = mute (no send/receive).
4. **Talk:** Hold **V** to record. Release **V** to send. You’ll see “Recording...” while holding and “Recording done, X KB” when sent. When someone else talks, you’ll see “X is speaking”.
5. **Controls:** **V** = push-to-talk, **C** = channel settings. You can rebind these in Minecraft’s Controls menu under the “SimpleCom” category.

#### V2.0 (WebSocket + FX GUI + proxy)

1. **Server (required)**: install the **V2.0 SimpleCom server plugin/mod** and configure `SimpleComConfig/config.yml` so that the port-multiplex proxy and internal WebSocket server are running (players and the FX client connect to the **proxy listen-port**).
2. **Client app**: build or download the standalone FX client JAR from the `V2.0new/SimpleCom-core` module (task `:SimpleCom-core:shadowJar` produces `SimpleCom-standalone-V2.0.jar`) and run it with Java 11+:
   - `java -jar SimpleCom-standalone-V2.0.jar`
3. **In game**: join the Minecraft server; when the server plugin is active, you will receive a **6-digit verification code** in chat.
4. **In the FX GUI**:
   - Choose language (`Language / 语言`).
   - Enter your **Minecraft username**.
   - Enter the **server address** of the voice proxy, e.g. `example.com:25566` or `127.0.0.1:25566` (the proxy listen-port or direct WS port).
   - Enter the 6-digit **verification code** from chat.
   - Click **Connect** to start a WebSocket session with the server.
5. After successful auth, the **connected window** appears; global hotkey push-to-talk and channel control are handled in this FX GUI instead of inside Minecraft’s keybinds.

### 中文

#### V1.x（游戏内 payload 通道）

1. **安装** 本模组（Fabric 或 Forge）以及 **Architectury API**。服务器需安装 SimpleCom 服务端插件（Bukkit/Spigot）才能转发语音。
2. **进入** 服务器。若服务器装有插件，会看到握手成功提示；否则可能提示超时（无服务端插件时语音不可用）。
3. **选信道：** 按 **C** 打开信道界面，输入 1–100 的信道号。信道 **0** 为静音（不发送也不接收）。
4. **说话：** 按住 **V** 录音，松开 **V** 发送。按住时显示“正在录音...”，发送后显示“录音完成，X KB”。别人说话时会显示“XXX正在对讲”。
5. **按键：** **V** = 按键说话，**C** = 信道设置。可在游戏“控制”选项中“简单的通讯器”分类下改键。

#### V2.0（WebSocket + FX 图形界面 + 服务端端口复用）

1. **服务端（必需）**：安装 **V2.0 SimpleCom 服务端插件/模组**，并在 `SimpleComConfig/config.yml` 中启用端口复用代理与内部 WebSocket 服务器（玩家和 FX 客户端都连接到同一个 **代理监听端口**）。
2. **客户端程序**：从 `V2.0new/SimpleCom-core` 模块构建或下载独立 FX 客户端 JAR（执行 `:SimpleCom-core:shadowJar` 得到 `SimpleCom-standalone-V2.0.jar`），使用 Java 11+ 运行：
   - `java -jar SimpleCom-standalone-V2.0.jar`
3. **在游戏中**：进入安装了 SimpleCom 服务端插件的服务器，聊天栏会收到一条包含 **6 位验证码** 的提示。
4. **在 FX 图形界面中**：
   - 选择界面语言（`Language / 语言`）。
   - 输入你的 **Minecraft 用户名**。
   - 输入语音服务器地址，例如 `example.com:25566` 或 `127.0.0.1:25566`（即代理监听端口或直接 WS 端口）。
   - 输入聊天中看到的 6 位 **验证码**。
   - 点击 **连接 / Connect**，客户端会通过 WebSocket 连接到服务端。
5. 认证成功后会进入“已连接”窗口，按键说话（全局热键）与信道控制在此 FX 窗口中完成，而不再依赖游戏内按键绑定。

---

## Features / 功能特性

### English

- Push-to-talk over **Minecraft’s existing connection** (no UDP, no extra ports).
- **100 channels** (1–100); channel 0 = mute.
- **Forge & Fabric** in one mod (Architectury); one JAR per game version.
- Server plugin relays voice between players in the same channel.
- VarInt + UTF-8 protocol, compatible with Minecraft payload format.

### 中文

- 基于 **Minecraft 已有连接** 的按键说话（不用 UDP，不占额外端口）。
- **100 个信道**（1–100）；信道 0 为静音。
- **Forge 与 Fabric** 共用同一模组（Architectury），各版本一个 JAR。
- 服务端插件在同信道玩家间转发语音。
- VarInt + UTF-8 协议，与 Minecraft payload 格式兼容。

---

## Version Compatibility / 版本兼容

### English

**Each release version is incompatible with other versions.** You must use matching client and server versions.

- **V2.0** server (plugin/mod) only works with **V2.0** client. This version introduces the new **Voice Stream API (WSAPI)** and a unified `V2.0` versioning scheme.
- **V1.1** server (mod or Bukkit plugin) only works with **V1.1** client.
- **V0.1** server only worked with **V1.0** client.

Do not mix versions: e.g. V2.0 client on a V1.1 server will not work correctly.

### 中文

**每个版本与其它版本不兼容。** 客户端与服务端必须使用相同版本。

- **V2.0** 服务端（插件/模组）仅兼容 **V2.0** 客户端。本版本引入新的 **语音流 API（WSAPI）**，并统一为 `V2.0` 版本号。
- **V1.1** 服务端（模组或 Bukkit 插件）仅兼容 **V1.1** 客户端。
- **V0.1** 服务端仅兼容 **V1.0** 客户端。

请勿混用版本：例如 V2.0 客户端连接 V1.1 服务端将无法正常工作。

---

## V2.0 Changes / V2.0 更新内容

### English

- **New server core (`SimpleCom-server-core`)**: shared pure-Java implementation of the port-multiplex proxy and WebSocket server, reused by Bukkit, Fabric and other server platforms.
- **Voice Stream API (WSAPI)**: a token-protected WebSocket endpoint that lets external services (e.g. bots, bridges) subscribe to non-encrypted voice packets in real time.
- **Unified versioning**: all main modules in the `V2.0new` branch now use the same version string **`V2.0`** for easier deployment and troubleshooting.

### 中文

- **全新的服务端核心库（`SimpleCom-server-core`）**：将端口复用代理与 WebSocket 服务器抽取为纯 Java 公共模块，供 Bukkit、Fabric 等多种服务端平台复用。
- **语音流 API（WSAPI）**：新增带 Token 校验的 WebSocket 接口，允许外部服务（如机器人、语音桥接服务）实时订阅「非加密信道」的语音数据包。
- **版本号统一为 `V2.0`**：`V2.0new` 分支下主要模块（客户端、服务端、核心库）统一使用 **V2.0** 版本号，便于部署与排错。

---

## Requirements / 依赖说明

### English

- **Minecraft** (see project for supported versions)  
- **Architectury API** (required)  
- **Fabric Loader** + **Fabric API** (Fabric) or **Forge** (Forge)  
- **Server:** SimpleCom Bukkit/Spigot plugin for voice to work in multiplayer.

### 中文

- **Minecraft**（支持版本见项目说明）  
- **Architectury API**（必装）  
- **Fabric Loader** + **Fabric API**（Fabric）或 **Forge**（Forge）  
- **服务器：** 多人语音需安装 SimpleCom Bukkit/Spigot 插件。

---

## Donate / 捐赠

### English

**Cryptocurrency (USDT):**

- **USDT on TRON:** `TDoJuuSBTztZFVQuVC7xeEESfFbktBUb9W`
- **USDT on Ethereum:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`
- **USDT on BNB Chain:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`
- **USDT on Polygon:** `0x9ce2b2de37976da8e795e7a3ff4badb60b3c8487`
- **USDT on X Layer:** `XKO9ce2b2de37976da8e795e7a3ff4badb60b3c8487`

Thank you for your support.

### 中文

**微信 / 支付宝：** 占位，后续会上传收款二维码。

感谢支持。
