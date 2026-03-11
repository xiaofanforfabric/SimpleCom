# SimpleCom WSAPI（Voice Stream API）文档 / Documentation

本接口用于**外部程序/插件**通过 WebSocket 订阅 SimpleCom 的语音二进制数据流（例如转接到 KOOK/Discord 等）。

## 1. 配置
## 1. Configuration

配置文件：`SimpleComConfig/config.yml`

```yml
voice-api:
  enabled: false
  token: ""
  port: 25500
  bind: "127.0.0.1"
```

- **enabled**：是否启用 WSAPI
- **token**：鉴权 token（启用时必须配置，不能为空）
- **port / bind**：WSAPI 监听地址

Configuration file: `SimpleComConfig/config.yml`

- **enabled**: whether WSAPI is enabled
- **token**: auth token (required when enabled, must not be empty)
- **port / bind**: WSAPI listen port and bind address

## 2. 连接方式
## 2. Connection

WebSocket 地址：

- `ws://<bind>:<port>/voice-api?token=<token>`

鉴权 token 支持三种传递方式（服务端按优先级依次取用）：

1. `Authorization: Bearer <token>`
2. Query 参数：`?token=<token>`
3. `Sec-WebSocket-Protocol: <token>`

WebSocket URL:

- `ws://<bind>:<port>/voice-api?token=<token>`

Token can be provided in three ways (the server checks in this order):

1. HTTP header `Authorization: Bearer <token>`
2. Query parameter: `?token=<token>`
3. Header `Sec-WebSocket-Protocol: <token>`

### 2.1 鉴权失败行为
### 2.1 Auth failure behaviour

若 token **缺失**或**不匹配**：服务端直接返回 **HTTP 403** 并断开连接（不会升级到 WebSocket）。

If the token is **missing** or **does not match**, the server responds with **HTTP 403 Forbidden** and closes the connection (no WebSocket upgrade).

## 3. 服务端推送消息
## 3. Server-pushed messages

连接成功（101 Upgrade）后，服务端会推送：

After a successful WebSocket upgrade (HTTP 101), the server will push the following messages:

### 3.1 服务器状态（Text）
### 3.1 Server status (Text)

```json
{"type":"serverstatus","compressionEncoder":true,"lowLatency":false}
```

- **compressionEncoder**：是否开启压缩编码器
- **lowLatency**：是否开启低延迟模式

- **compressionEncoder**: whether the compression encoder is enabled
- **lowLatency**: whether low-latency mode is enabled

### 3.2 心跳（Text）
### 3.2 Heartbeat (Text)

每 **5 秒**推送一次：

```json
{"type":"heartbeat","time":1710000000000}
```

`time` 为服务端毫秒时间戳。

The `time` field is the server-side timestamp in milliseconds.

### 3.3 语音数据包（Binary）
### 3.3 Voice packet (Binary)

服务端会把 **所有普通信道的语音数据包**实时推送给所有 WSAPI 客户端：

- **会推送**：信道 `1..100`
- **不会推送**：信道 `0`（不转发）
- **不会推送**：加密信道 `>100`

> 注意：WSAPI 是“只读订阅”用途。服务端会忽略 WSAPI 客户端发来的文本/二进制帧（除 close/ping 外）。

The server forwards **all voice packets from normal channels** to every WSAPI client in real time:

- **Forwarded**: channels `1..100`
- **Not forwarded**: channel `0` (no relay)
- **Not forwarded**: encrypted channels `>100`

> Note: WSAPI is **read-only**. The server ignores text/binary frames sent from WSAPI clients (except for close/ping frames).

## 4. 语音二进制包格式
## 4. Voice binary packet format

WSAPI 推送的二进制包为 SimpleCom 原始语音包（与 MC 语音客户端收到/发送的包一致），包头为：

1. `int32`：`usernameLen`
2. `byte[usernameLen]`：UTF-8 `username`
3. `int32`：`seq`（分片序号）
4. `int32`：`total`（总分片数）
5. 剩余字节：语音负载（具体编码由客户端/配置决定）

The binary packets sent via WSAPI are the original SimpleCom voice packets (same format as used between the MC voice clients and server). The header layout is:

1. `int32`: `usernameLen`
2. `byte[usernameLen]`: UTF-8 `username`
3. `int32`: `seq` (chunk sequence number)
4. `int32`: `total` (total number of chunks)
5. Remaining bytes: voice payload (the actual codec depends on client/configuration)

## 5. 版本与兼容性说明
## 5. Version & compatibility notes

- 本文档对应 `V2.0new` 分支下的实现。
- 若未来调整心跳内容、包体结构或增加新消息类型，以仓库内更新为准。

- This document describes the implementation in the `V2.0new` branch.
- If heartbeat payloads, packet layout, or new message types change in the future, the repository version is the source of truth.

