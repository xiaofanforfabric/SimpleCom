package com.xiaofan.client.gui;

import com.xiaofan.config.SimpleComConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 信道设置 GUI，按 C 打开
 */
public class ChannelScreen extends Screen {

    private static final String TITLE = "设置信道";
    private static final String HINT = "请输入你的信道（默认1），0静音，范围1~100";
    private static final String CONFIRM = "确认";
    private static final String CANCEL = "取消";
    private static final String BTN_CREATE_ENCRYPTED = "创建加密信道";
    private static final String BTN_CONNECT_ENCRYPTED = "连接加密信道";

    private TextFieldWidget channelField;
    private final Screen parent;

    public ChannelScreen(Screen parent) {
        super(new LiteralText(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int fieldWidth = 200;
        int fieldHeight = 20;

        channelField = new TextFieldWidget(textRenderer, centerX - fieldWidth / 2, height / 2 - 30, fieldWidth, fieldHeight, new LiteralText("channel"));
        channelField.setMaxLength(4);
        int ch = SimpleComConfig.getChannel();
        channelField.setText(ch <= 100 ? String.valueOf(ch) : "1");
        addChild(channelField);

        addButton(new ButtonWidget(centerX - 105, height / 2 + 10, 100, 20, new LiteralText(CANCEL), b -> onClose()));
        addButton(new ButtonWidget(centerX + 5, height / 2 + 10, 100, 20, new LiteralText(CONFIRM), b -> onConfirm()));

        addButton(new ButtonWidget(centerX - 105, height / 2 + 40, 100, 20, new LiteralText(BTN_CREATE_ENCRYPTED), b -> {
            if (client != null) client.openScreen(new CreateEncryptedChannelScreen(parent));
        }));
        addButton(new ButtonWidget(centerX + 5, height / 2 + 40, 100, 20, new LiteralText(BTN_CONNECT_ENCRYPTED), b -> {
            if (client != null) client.openScreen(new ConnectEncryptedChannelScreen(parent));
        }));
    }

    private void onConfirm() {
        try {
            int ch = Integer.parseInt(channelField.getText().trim());
            ch = SimpleComConfig.clampChannel(ch);
            SimpleComConfig.setChannel(ch);
            com.xiaofan.client.SimpleComVoiceClient.onChannelChanged(ch);
        } catch (NumberFormatException e) {
            SimpleComConfig.setChannel(SimpleComConfig.CHANNEL_DEFAULT);
            com.xiaofan.client.SimpleComVoiceClient.onChannelChanged(SimpleComConfig.CHANNEL_DEFAULT);
        }
        onClose();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 60, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer, new LiteralText(HINT), width / 2, height / 2 - 45, 0xA0A0A0);
        channelField.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (client != null) {
            client.openScreen(parent);
        }
    }
}
