package com.xiaofan.client.gui;

import com.xiaofan.client.SimpleComVoiceClient;
import com.xiaofan.config.PasswordHash;
import com.xiaofan.config.SimpleComConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 加入 XXX 加密信道：密码输入，返回/确认
 */
public class JoinEncryptedChannelScreen extends Screen {

    private static final String CONFIRM = "确认";
    private static final String BACK = "返回";

    private final Screen parent;
    private final String channelName;
    private TextFieldWidget passwordField;

    public JoinEncryptedChannelScreen(Screen parent, String channelName) {
        super(new LiteralText("加入加密信道: " + (channelName != null ? channelName : "")));
        this.parent = parent;
        this.channelName = channelName != null ? channelName : "";
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int fieldWidth = 200;
        int fieldHeight = 20;

        passwordField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, height / 2 - 20, fieldWidth, fieldHeight, new LiteralText("password"));
        passwordField.setMaxLength(64);
        addChild(passwordField);

        addButton(new ButtonWidget(centerX - 105, height / 2 + 15, 100, 20, new LiteralText(BACK), b -> closeToParent()));
        addButton(new ButtonWidget(centerX + 5, height / 2 + 15, 100, 20, new LiteralText(CONFIRM), b -> onConfirm()));

        SimpleComVoiceClient.setEncryptedJoinResponseHandler(this::onJoinResponse);
    }

    @Override
    public void removed() {
        super.removed();
        SimpleComVoiceClient.setEncryptedJoinResponseHandler(null);
    }

    private void onJoinResponse(SimpleComVoiceClient.EncryptedJoinResult result) {
        if (client == null) return;
        client.execute(() -> {
            if (result.success) {
                SimpleComConfig.setChannelUnclamped(result.channelId);
                SimpleComConfig.setEncryptedChannelName(channelName);
                SimpleComVoiceClient.onChannelChanged(result.channelId);
                if (client.player != null) {
                    client.player.sendMessage(new LiteralText("已加入加密信道：" + channelName + " (ID " + result.channelId + ")"), false);
                }
                closeToParent();
            } else {
                if (client.player != null) {
                    client.player.sendMessage(new LiteralText("§c加入失败，密码错误或信道不存在"), false);
                }
            }
        });
    }

    private void onConfirm() {
        String hash = PasswordHash.hash(passwordField.getText());
        SimpleComVoiceClient.sendEncryptedJoin(channelName, hash);
    }

    private void closeToParent() {
        if (client != null) client.openScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 55, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer, new LiteralText("加入 " + channelName + " 信道，请输入密码"), width / 2, height / 2 - 40, 0xA0A0A0);
        passwordField.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
