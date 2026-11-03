package com.createmotorsport.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * An "are you sure" pop up copied from my universal keyboard mod
 */
public class ConfirmDialog {

    private static final int W = 200;
    private static final int PAD = 6;
    private static final int GAP = 2;
    private static final int BTN_H = 14;
    private static final int BTN_W = 54;

    private final Font font;

    private boolean open;
    private Component title = Component.empty();
    private Component body = Component.empty();
    private Component confirmLabel = Component.empty();
    private Runnable onConfirm = () -> { };

    private int px, py, pw, ph;

    public ConfirmDialog(Font font) {
        this.font = font;
    }

    public void setParentBounds(int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
    }

    public void open(Component title, Component body, Component confirmLabel, Runnable onConfirm) {
        this.title = title;
        this.body = body;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
        this.open = true;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public void render(GuiGraphics g, int mx, int my) {
        if (!open) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.fill(px, py, px + pw, py + ph, Theme.DIM);

        int dh = dialogHeight();
        int cx = dialogX(), cy = dialogY();
        g.fill(cx, cy, cx + W, cy + dh, Theme.PANEL);
        g.fill(cx, cy, cx + W, cy + 1, Theme.ACCENT);
        g.fill(cx, cy + dh - 1, cx + W, cy + dh, Theme.ACCENT);
        g.fill(cx, cy, cx + 1, cy + dh, Theme.ACCENT);
        g.fill(cx + W - 1, cy, cx + W, cy + dh, Theme.ACCENT);

        int centerX = cx + W / 2;
        int y = cy + PAD;
        y += drawWrappedCentered(g, title, centerX, y, Theme.TEXT) + GAP;
        if (!body.getString().isEmpty()) {
            drawWrappedCentered(g, body, centerX, y, Theme.TEXT_DIM);
        }

        int yesX = yesX(), noX = noX(), btnY = buttonY();
        boolean yesHov = isIn(mx, my, yesX, btnY);
        boolean noHov = isIn(mx, my, noX, btnY);
        FlatButton.draw(g, yesX, btnY, BTN_W, BTN_H, confirmLabel, Theme.STOP, yesHov, Theme.TEXT);
        FlatButton.draw(g, noX, btnY, BTN_W, BTN_H, Component.literal("Cancel"), Theme.NEUTRAL, noHov, Theme.TEXT);

        g.pose().popPose();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) {
            return false;
        }
        if (button == 0 && isIn((int) mx, (int) my, yesX(), buttonY())) {
            close();
            onConfirm.run();
        } else if (button == 0 && isIn((int) mx, (int) my, noX(), buttonY())) {
            close();
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
        return true;
    }

    public boolean charTyped() {
        return open;
    }

    private int dialogHeight() {
        int h = PAD + wrap(title).size() * font.lineHeight + GAP;
        if (!body.getString().isEmpty()) {
            h += wrap(body).size() * font.lineHeight + GAP;
        }
        return h + 4 + BTN_H + PAD;
    }

    private int dialogX() {
        return px + (pw - W) / 2;
    }

    private int dialogY() {
        return py + (ph - dialogHeight()) / 2;
    }

    private int yesX() {
        return dialogX() + W / 2 - BTN_W - 4;
    }

    private int noX() {
        return dialogX() + W / 2 + 4;
    }

    private int buttonY() {
        return dialogY() + dialogHeight() - PAD - BTN_H;
    }

    private List<FormattedCharSequence> wrap(Component text) {
        return font.split(text, W - PAD * 2);
    }

    private int drawWrappedCentered(GuiGraphics g, Component text, int centerX, int y, int color) {
        List<FormattedCharSequence> lines = wrap(text);
        int ly = y;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, centerX - font.width(line) / 2, ly, color, false);
            ly += font.lineHeight;
        }
        return lines.size() * font.lineHeight;
    }

    private static boolean isIn(int mx, int my, int x, int y) {
        return mx >= x && mx < x + BTN_W && my >= y && my < y + BTN_H;
    }
}
