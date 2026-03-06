package com.xiaofan.servercommen.payload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** VarInt 解析工具 */
public final class VarIntUtil {

    public static int readVarInt(byte[] data) throws IOException {
        if (data == null || data.length == 0) return 1;
        return readVarInt(new DataInputStream(new ByteArrayInputStream(data)));
    }

    /** 从流中读取 VarInt（用于顺序解析 payload） */
    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = in.read();
            if (b < 0) throw new IOException("EOF");
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }
}
