package com.createmotorsport.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class FlatButton extends AbstractButton {
    public static final int NEUTRAL = 0xFF3A3A3A;
    public static final int GO = 0xFF2F6B3A;
    public static final int STOP = 0xFF7A2F2F;
    public static final int ACCENT = 0xFF35506A;

    private static final int BORDER = 0xFF555555;
    private static final int BORDER_HOVER = 0xFF8A8A8A;
    private static final int LABEL = 0xFFE8E8E8;
    private static final int LABEL_OFF = 0xFF888888;

    private final Consumer<FlatButton> onPress;
    private int color;
    private boolean muted;

    public FlatButton(int x, int y, int w, int h, Component label, int color,
                      Consumer<FlatButton> onPress) {
        super(x, y, w, h, label);
        this.color = color;
        this.onPress = onPress;
    }

    public FlatButton color(int color) {
        this.color = color;
        return this;
    }

    // Draws the label dimmed for toggle currently off
    public FlatButton muted(boolean muted) {
        this.muted = muted;
        return this;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = isHoveredOrFocused() && active;

        g.fill(x, y, x + w, y + h, active ? (hot ? lighten(color) : color) : 0xFF2A2A2A);
        int border = hot ? BORDER_HOVER : BORDER;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);

        Component label = getMessage();
        var font = Minecraft.getInstance().font;
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + (h - 8) / 2, active && !muted ? LABEL : LABEL_OFF,
                false);
    }

    // highlight on hover
    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 28);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 28);
        int b = Math.min(255, (argb & 0xFF) + 28);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
