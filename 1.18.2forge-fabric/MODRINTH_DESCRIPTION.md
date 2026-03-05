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

1. **Install** the mod (Fabric or Forge) and **Architectury API**. On the server, install the SimpleCom server plugin (Bukkit/Spigot) so it can relay voice.
2. **Join** a server. When the server has the plugin, you’ll see a success message; otherwise you may see a timeout (voice won’t work without the server plugin).
3. **Choose a channel:** Press **C** to open the channel screen. Enter a number 1–100. Channel **0** = mute (no send/receive).
4. **Talk:** Hold **V** to record. Release **V** to send. You’ll see “Recording...” while holding and “Recording done, X KB” when sent. When someone else talks, you’ll see “X is speaking”.
5. **Controls:** **V** = push-to-talk, **C** = channel settings. You can rebind these in Minecraft’s Controls menu under the “SimpleCom” category.

### 中文

1. **安装** 本模组（Fabric 或 Forge）以及 **Architectury API**。服务器需安装 SimpleCom 服务端插件（Bukkit/Spigot）才能转发语音。
2. **进入** 服务器。若服务器装有插件，会看到握手成功提示；否则可能提示超时（无服务端插件时语音不可用）。
3. **选信道：** 按 **C** 打开信道界面，输入 1–100 的信道号。信道 **0** 为静音（不发送也不接收）。
4. **说话：** 按住 **V** 录音，松开 **V** 发送。按住时显示“正在录音...”，发送后显示“录音完成，X KB”。别人说话时会显示“XXX正在对讲”。
5. **按键：** **V** = 按键说话，**C** = 信道设置。可在游戏“控制”选项中“简单的通讯器”分类下改键。

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
