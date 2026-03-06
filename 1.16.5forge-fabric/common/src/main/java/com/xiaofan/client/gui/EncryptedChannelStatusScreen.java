package com.xiaofan.client.gui;

import com.xiaofan.client.SimpleComVoiceClient;
import com.xiaofan.config.SimpleComConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * 加密状态下按 C 打开：显示当前信道名称，退出此信道按钮
 */
public class EncryptedChannelStatusScreen extends Screen {

    private static final String TITLE = "加密信道";
    private static final String CURRENT = "当前信道：%s";
    private static final String EXIT = "退出此信道";

    private final Screen parent;

    public EncryptedChannelStatusScreen(Screen parent) {
        super(new LiteralText(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        addButton(new ButtonWidget(centerX - 100, height / 2 + 10, 200, 20, new LiteralText(EXIT), b -> onExit()));
    }

    private void onExit() {
        SimpleComConfig.setChannel(SimpleComConfig.CHANNEL_DEFAULT);
        SimpleComConfig.clearEncryptedChannelName();
        SimpleComVoiceClient.onChannelChanged(SimpleComConfig.CHANNEL_DEFAULT);
        if (client != null) {
            client.openScreen(parent);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 50, 0xFFFFFF);
        String name = SimpleComConfig.getEncryptedChannelName();
        if (name == null) name = "";
        drawCenteredText(matrices, textRenderer, new LiteralText(String.format(CURRENT, name)), width / 2, height / 2 - 25, 0xA0A0A0);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
