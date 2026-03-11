package com.xiaofan.ws;

/**
 * {@link SeedMessageApi#seedMessageToPlayer} 的返回值枚举。
 * Return value enum for {@link SeedMessageApi#seedMessageToPlayer}.
 */
public enum SeedMessageResult {

    /** 消息已成功发送至玩家的 WS 连接 | Message delivered to player's WS connection */
    DONE,

    /** 玩家未建立 WS 连接（未认证或已断开）| Player has no active WS connection */
    NULL,

    /** 发送过程中发生 IO 异常 | IOException occurred while sending */
    ERROR
}
