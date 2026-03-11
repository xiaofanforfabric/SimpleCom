package com.xiaofan.gui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

/**
 * 最小化 Windows User32 接口，仅用于 GetAsyncKeyState 轮询。
 * 不依赖 jna-platform，仅需 JNA 核心。
 */
interface Win32User32 extends Library {
    Win32User32 INSTANCE = Native.load("user32", Win32User32.class, W32APIOptions.DEFAULT_OPTIONS);
    short GetAsyncKeyState(int vKey);
}
