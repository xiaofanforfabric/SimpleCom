package com.xiaofan.server.payload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** VarInt 解析工具 */
public final class VarIntUtil {

    /** 从 InputStream 顺序读取 VarInt */
    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = in.read() & 0xFF;
            if (b < 0) throw new IOException("EOF");
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public static int readVarInt(byte[] data) throws IOException {
        if (data == null || data.length == 0) return 1;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = in.readByte() & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }
}
