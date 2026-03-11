package com.xiaofan.gui;

import com.xiaofan.ws.SimpleComWsClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * JavaFX 主应用。负责持有 Stage 并在窗口间切换。
 * JavaFX main application. Owns the primary Stage and handles window transitions.
 */
public final class SimpleComApp extends Application {

    private static Stage primaryStage;
    private static SimpleComWsClient currentWsClient;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setResizable(false);
        // 禁止通过 X 关闭，只能随 MC 一起退出 | Disable X close, only exit with MC
        primaryStage.setOnCloseRequest(e -> e.consume());
        showMainWindow();
    }

    /** 展示主界面（语言选择、用户名、服务器地址）。 */
    public static void showMainWindow() {
        showMainWindowWithStatus(null);
    }

    /** 展示主界面并显示状态/错误信息。 */
    public static void showMainWindowWithStatus(String statusMessage) {
        currentWsClient = null;
        primaryStage.setScene(MainWindow.createScene(primaryStage, statusMessage));
        primaryStage.setTitle(I18n.get("app.title"));
        primaryStage.show();
    }

    /** 验证成功后展示"已连接"窗口（含断开按钮）。 */
    public static void showConnectedWindow(String server, String username, SimpleComWsClient client) {
        currentWsClient = client;
        PttKeyManager.getInstance().start();
        primaryStage.setScene(ConnectedWindow.createScene(server, username, client, SimpleComApp::disconnectAndShowMain));
        primaryStage.setTitle(I18n.get("connected.title"));
    }

    /** 断开连接并返回主界面。 */
    public static void disconnectAndShowMain() {
        PttKeyManager.getInstance().stop();
        SimpleComWsClient client = currentWsClient;
        currentWsClient = null;
        if (client != null) {
            Thread t = new Thread(() -> {
                try {
                    client.closeBlocking();
                } catch (InterruptedException ignored) {}
                Platform.runLater(SimpleComApp::showMainWindow);
            }, "SimpleCom-WS-Disconnect");
            t.setDaemon(true);
            t.start();
        } else {
            showMainWindow();
        }
    }

    /** 服务器断开连接时调用：返回主界面并显示错误。 */
    public static void onServerDisconnected() {
        PttKeyManager.getInstance().stop();
        currentWsClient = null;
        showMainWindowWithStatus(I18n.get("err.connection_closed"));
    }
}
