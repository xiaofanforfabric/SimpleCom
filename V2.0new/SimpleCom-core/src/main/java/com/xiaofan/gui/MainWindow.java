package com.xiaofan.gui;

import com.xiaofan.ws.SimpleComWsClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * 主窗口：语言选择、用户名输入、服务器地址输入、验证码输入、连接按钮。
 * Main window: language selector, username, server address, verification code, connect button.
 */
public final class MainWindow {

    private MainWindow() {}

    /** @param initialStatus 若不为空，在状态栏显示该错误信息 | If non-null, show this error in status label */
    public static Scene createScene(Stage stage, String initialStatus) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(24, 32, 24, 32));
        root.setPrefWidth(420);

        // 标题
        Label titleLabel = new Label("SimpleCom");
        titleLabel.setFont(Font.font(22));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // 语言选择 | Language selector（固定双语标签，方便不同语言用户都能找到）
        HBox langRow = new HBox(10);
        langRow.setAlignment(Pos.CENTER_LEFT);
        Label langLabel = new Label("语言 / Language:");
        ChoiceBox<I18n.Language> langBox = new ChoiceBox<>();
        langBox.getItems().addAll(I18n.Language.values());
        langBox.setValue(I18n.getLanguage());
        langBox.setConverter(new StringConverter<I18n.Language>() {
            @Override
            public String toString(I18n.Language l) {
                return l == null ? "" : l.displayName;
            }
            @Override
            public I18n.Language fromString(String s) {
                return null;
            }
        });
        langBox.valueProperty().addListener((obs, old, newLang) -> {
            if (newLang != null && newLang != old) {
                I18n.setLanguage(newLang);
                stage.setScene(createScene(stage));
                stage.setTitle(I18n.get("app.title"));
            }
        });
        langRow.getChildren().addAll(langLabel, langBox);

        // 用户名 | Username
        Label usernameLabel = new Label(I18n.get("label.username") + ":");
        TextField usernameField = new TextField();
        usernameField.setPromptText(I18n.get("label.username"));
        VBox.setVgrow(usernameField, Priority.NEVER);

        // 服务器地址 | Server address
        Label serverLabel = new Label(I18n.get("label.server") + ":");
        TextField serverField = new TextField();
        serverField.setPromptText(I18n.get("hint.server"));

        // 验证码 | Verification code
        Label codeLabel = new Label(I18n.get("label.code") + ":");
        TextField codeField = new TextField();
        codeField.setPromptText(I18n.get("hint.code"));
        codeField.setMaxWidth(160);
        // 只允许输入数字，且最多 6 位
        codeField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filtered = newVal.replaceAll("[^0-9]", "");
            if (filtered.length() > 6) filtered = filtered.substring(0, 6);
            if (!filtered.equals(newVal)) codeField.setText(filtered);
        });
        HBox codeRow = new HBox(0, codeField);
        codeRow.setAlignment(Pos.CENTER_LEFT);

        // 状态信息 | Status label
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(360);
        statusLabel.setMinHeight(40);
        if (initialStatus != null && !initialStatus.isEmpty()) {
            showError(statusLabel, initialStatus);
        }

        // 连接按钮 | Connect button
        Button connectBtn = new Button(I18n.get("btn.connect"));
        connectBtn.setDefaultButton(true);
        connectBtn.setMaxWidth(Double.MAX_VALUE);

        connectBtn.setOnAction(e -> onConnect(
                stage, connectBtn, statusLabel,
                usernameField.getText().trim(),
                serverField.getText().trim(),
                codeField.getText().trim()
        ));

        root.getChildren().addAll(
                titleLabel,
                new Separator(),
                langRow,
                new Separator(),
                usernameLabel, usernameField,
                serverLabel, serverField,
                codeLabel, codeRow,
                connectBtn,
                statusLabel
        );

        return new Scene(root);
    }

    public static Scene createScene(Stage stage) {
        return createScene(stage, null);
    }

    private static void onConnect(Stage stage, Button connectBtn, Label statusLabel,
                                   String username, String server, String code) {
        if (username.isEmpty()) {
            showError(statusLabel, I18n.get("err.username_empty"));
            return;
        }
        if (server.isEmpty()) {
            showError(statusLabel, I18n.get("err.server_empty"));
            return;
        }
        if (code.isEmpty()) {
            showError(statusLabel, I18n.get("err.code_empty"));
            return;
        }

        String wsUri = server.startsWith("ws://") || server.startsWith("wss://")
                ? server : "ws://" + server;

        connectBtn.setDisable(true);
        showInfo(statusLabel, I18n.get("status.connecting"));

        try {
            URI uri = URI.create(wsUri);
            final SimpleComWsClient[] clientHolder = new SimpleComWsClient[1];
            SimpleComWsClient client = new SimpleComWsClient(
                    uri,
                    username,
                    code,
                    () -> Platform.runLater(() -> SimpleComApp.showConnectedWindow(server, username, clientHolder[0])),
                    (closeCode, reason) -> Platform.runLater(() -> {
                        connectBtn.setDisable(false);
                        if (closeCode == 4004) {
                            showError(statusLabel, I18n.get("err.not_found"));
                        } else if (closeCode == 4003) {
                            showError(statusLabel, I18n.get("err.code_wrong"));
                        } else {
                            showError(statusLabel, I18n.get("err.connect_failed") + (reason != null ? reason : ""));
                        }
                    }),
                    () -> Platform.runLater(SimpleComApp::onServerDisconnected)
            );
            clientHolder[0] = client;
            // 在后台线程执行带超时的连接，防止卡住 FX 线程或永久等待
            // Run timed connection in background to avoid blocking FX thread or hanging forever
            final SimpleComWsClient wsClient = client;
            Thread connectThread = new Thread(() -> {
                try {
                    boolean connected = wsClient.connectBlocking(10, TimeUnit.SECONDS);
                    if (!connected) {
                        Platform.runLater(() -> {
                            connectBtn.setDisable(false);
                            showError(statusLabel, I18n.get("err.connect_failed") + "timeout / 连接超时");
                        });
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    Platform.runLater(() -> {
                        connectBtn.setDisable(false);
                        showError(statusLabel, I18n.get("err.connect_failed") + "interrupted");
                    });
                }
            }, "SimpleCom-WS-Connect");
            connectThread.setDaemon(true);
            connectThread.start();
        } catch (Exception ex) {
            connectBtn.setDisable(false);
            showError(statusLabel, I18n.get("err.invalid_address"));
        }
    }

    private static void showError(Label label, String text) {
        label.setStyle("-fx-text-fill: #D32F2F;");
        label.setText(text);
    }

    private static void showInfo(Label label, String text) {
        label.setStyle("-fx-text-fill: #555555;");
        label.setText(text);
    }
}
