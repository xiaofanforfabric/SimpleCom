package com.xiaofan.client.gui;

import com.xiaofan.client.SimpleComVoiceClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.List;

/**
 * 连接加密信道：向服务端请求列表，显示每页最多 5 个名称按钮，上一页/下一页
 */
public class ConnectEncryptedChannelScreen extends Screen {

    private static final String TITLE = "连接加密信道";
    private static final String PREV = "上一页";
    private static final String NEXT = "下一页";
    private static final int NAMES_PER_PAGE = 5;

    private final Screen parent;
    private List<String> names = new ArrayList<>();
    private int page = 0;
    private final ButtonWidget[] nameButtons = new ButtonWidget[NAMES_PER_PAGE];

    public ConnectEncryptedChannelScreen(Screen parent) {
        super(new LiteralText(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        SimpleComVoiceClient.setEncryptedListResponseHandler(this::onListReceived);
        SimpleComVoiceClient.requestEncryptedList();

        int centerX = width / 2;
        int btnWidth = 200;
        int btnHeight = 20;
        int startY = height / 2 - 50;

        for (int i = 0; i < NAMES_PER_PAGE; i++) {
            final int index = i;
            ButtonWidget btn = new ButtonWidget(centerX - btnWidth / 2, startY + i * 22, btnWidth, btnHeight, new LiteralText("..."), b -> {
                int idx = page * NAMES_PER_PAGE + index;
                if (idx >= 0 && idx < names.size() && client != null) {
                    client.openScreen(new JoinEncryptedChannelScreen(parent, names.get(idx)));
                }
            });
            btn.visible = false;
            nameButtons[i] = btn;
            addButton(btn);
        }

        addButton(new ButtonWidget(centerX - 105, height / 2 + 65, 100, 20, new LiteralText(PREV), b -> {
            if (page > 0) {
                page--;
                refreshNameButtons();
            }
        }));
        addButton(new ButtonWidget(centerX + 5, height / 2 + 65, 100, 20, new LiteralText(NEXT), b -> {
            if ((page + 1) * NAMES_PER_PAGE < names.size()) {
                page++;
                refreshNameButtons();
            }
        }));
    }

    @Override
    public void removed() {
        super.removed();
        SimpleComVoiceClient.setEncryptedListResponseHandler(null);
    }

    private void onListReceived(List<String> list) {
        if (client == null) return;
        client.execute(() -> {
            names = list != null ? list : new ArrayList<>();
            page = 0;
            refreshNameButtons();
        });
    }

    private void refreshNameButtons() {
        int startY = height / 2 - 50;
        for (int i = 0; i < NAMES_PER_PAGE; i++) {
            int idx = page * NAMES_PER_PAGE + i;
            ButtonWidget btn = nameButtons[i];
            btn.y = startY + i * 22;
            btn.setMessage(new LiteralText(idx < names.size() ? names.get(idx) : ""));
            btn.visible = idx < names.size();
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, height / 2 - 75, 0xFFFFFF);
        if (names.isEmpty() && page == 0) {
            drawCenteredText(matrices, textRenderer, new LiteralText("正在获取列表..."), width / 2, height / 2 - 30, 0xA0A0A0);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
