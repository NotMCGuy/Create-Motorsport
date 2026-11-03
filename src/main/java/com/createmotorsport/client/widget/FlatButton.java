package com.createmotorsport.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;


public class FlatButton extends AbstractButton {

    private final Consumer<FlatButton> onPress;
    private int color;
    private boolean muted;
    @Nullable
    private Component hoverTip;

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

    // tooltips are nice, see HoverTip
    public FlatButton hoverTip(@Nullable Component hoverTip) {
        this.hoverTip = hoverTip;
        return this;
    }

    @Nullable
    public Component hoverTip() {
        return hoverTip;
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
        int label = !active ? Theme.TEXT_MUTED : muted ? Theme.TEXT_DIM : Theme.TEXT;
        draw(g, x, y, w, h, getMessage(), active ? color : Theme.DISABLED, hot, label);
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, Component label, int color, boolean hot,
                            int labelColor) {
        g.fill(x, y, x + w, y + h, hot ? Theme.hover(color) : color);
        int edge = hot ? Theme.EDGE_HOVER : Theme.EDGE;
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);

        var font = Minecraft.getInstance().font;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, labelColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
