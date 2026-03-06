package com.xiaofan.client.gui;

import com.xiaofan.client.SimpleComVoiceClient;
import com.xiaofan.config.PasswordHash;
import com.xiaofan.config.SimpleComConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * 创建加密信道：名称、密码输入，取消/确认
 */
public class CreateEncryptedChannelScreen extends Screen {

    private static final String TITLE = "创建加密信道";
    private static final String NAME_HINT = "加密信道名称";
    private static final String PWD_HINT = "加密信道密码";
    private static final String CONFIRM = "确认";
    private static final String CANCEL = "取消";

    private TextFieldWidget nameField;
    private TextFieldWidget passwordField;
    private final Screen parent;

    public CreateEncryptedChannelScreen(Screen parent) {
        super(Text.literal(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int fieldWidth = 200;
        int fieldHeight = 20;

        nameField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, height / 2 - 55, fieldWidth, fieldHeight, Text.literal(NAME_HINT));
        nameField.setMaxLength(32);
        addSelectableChild(nameField);

        passwordField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, height / 2 - 25, fieldWidth, fieldHeight, Text.literal(PWD_HINT));
        passwordField.setMaxLength(64);
        addSelectableChild(passwordField);

        addDrawableChild(new ButtonWidget(centerX - 105, height / 2 + 15, 100, 20, Text.literal(CANCEL), b -> closeToParent()));
        addDrawableChild(new ButtonWidget(centerX + 5, height / 2 + 15, 100, 20, Text.literal(CONFIRM), b -> onConfirm()));

        SimpleComVoiceClient.setEncryptedCreateResponseHandler(this::onCreateResponse);
    }

    @Override
    public void removed() {
        super.removed();
        SimpleComVoiceClient.setEncryptedCreateResponseHandler(null);
    }

    private void onCreateResponse(SimpleComVoiceClient.EncryptedCreateResult result) {
        if (client == null) return;
        client.execute(() -> {
            if (result.success) {
                SimpleComConfig.setChannelUnclamped(result.channelId);
                SimpleComConfig.setEncryptedChannelName(nameField.getText().trim());
                SimpleComVoiceClient.onChannelChanged(result.channelId);
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("已创建并加入加密信道：" + nameField.getText().trim() + " (ID " + result.channelId + ")"), false);
                }
                closeToParent();
            } else {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§c创建失败，信道名称可能已存在"), false);
                }
            }
        });
    }

    private void onConfirm() {
        String name = nameField.getText().trim();
        String pwd = passwordField.getText();
        if (name.isEmpty()) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("§c请输入信道名称"), false);
            }
            return;
        }
        String hash = PasswordHash.hash(pwd);
        SimpleComVoiceClient.sendEncryptedCreate(name, hash);
    }

    private void closeToParent() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 75, 0xFFFFFF);
        nameField.render(matrices, mouseX, mouseY, delta);
        passwordField.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
