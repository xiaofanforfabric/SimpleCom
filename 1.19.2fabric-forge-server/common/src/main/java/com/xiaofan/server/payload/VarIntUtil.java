package com.xiaofan.server.payload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** VarInt 解析工具 */
public final class VarIntUtil {

    public static int readVarInt(byte[] data) throws IOException {
        if (data == null || data.length == 0) return 1;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        return readVarInt(in);
    }

    /** 从流中顺序读取 VarInt */
    public static int readVarInt(java.io.InputStream in) throws IOException {
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
