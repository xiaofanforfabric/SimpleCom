package com.xiaofan.gui;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局 PTT 按键监听。使用 JNA 轮询 GetAsyncKeyState，不依赖 Fabric API/Mixin，跨版本兼容。
 * 仅 Windows 支持 MC 获得焦点时触发；Linux/Mac 需 SimpleCom 窗口有焦点。
 */
public final class PttKeyManager {

    private static final PttKeyManager INSTANCE = new PttKeyManager();
    private static final int POLL_INTERVAL_MS = 40;

    private final AtomicInteger boundKeyCode = new AtomicInteger(86); // VK_V = 86
    private volatile Runnable onKeyPressed;
    private volatile Runnable onKeyReleased;
    private volatile Thread pollThread;
    private volatile boolean running;

    private PttKeyManager() {}

    public static PttKeyManager getInstance() {
        return INSTANCE;
    }

    /** 设置绑定的按键（使用 KeyCode.getCode() 或 VK_*） */
    public void setKeyCode(int vkCode) {
        boundKeyCode.set(vkCode);
    }

    /** 设置按下时的回调 */
    public void setOnKeyPressed(Runnable callback) {
        this.onKeyPressed = callback;
    }

    /** 设置松开时的回调 */
    public void setOnKeyReleased(Runnable callback) {
        this.onKeyReleased = callback;
    }

    /** 启动全局监听（Windows 下轮询，其他平台仅依赖 JavaFX 窗口焦点） */
    public void start() {
        if (running) return;
        if (!isWindows()) {
            return; // 非 Windows 不启动轮询，仅靠 ConnectedWindow 的 EventFilter
        }
        running = true;
        pollThread = new Thread(this::pollLoop, "SimpleCom-PTT-Poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    /** 停止全局监听 */
    public void stop() {
        running = false;
        Thread t = pollThread;
        pollThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private void pollLoop() {
        Win32User32 user32;
        try {
            user32 = Win32User32.INSTANCE;
        } catch (Throwable e) {
            System.err.println("[SimpleCom] JNA User32 not available, PTT only works when SimpleCom window has focus: " + e.getMessage());
            running = false;
            return;
        }
        boolean wasDown = false;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                short state = user32.GetAsyncKeyState(boundKeyCode.get());
                boolean isDown = (state & 0x8000) != 0;
                if (isDown && !wasDown && onKeyPressed != null) {
                    onKeyPressed.run();
                } else if (!isDown && wasDown && onKeyReleased != null) {
                    onKeyReleased.run();
                }
                wasDown = isDown;
            } catch (Throwable ignored) {}
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        running = false;
    }
}
