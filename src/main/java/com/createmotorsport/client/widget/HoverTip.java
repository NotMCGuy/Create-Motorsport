package com.createmotorsport.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class HoverTip {

    private static final int MAX_WIDTH = 160;
    private static final int PAD = 4;
    private static final int OFFSET = 10; // offset from cursor
    private static final int Z = 400;

    private HoverTip() {
    }

    public static void forHovered(GuiGraphics g, Font font, List<? extends GuiEventListener> widgets, int mx, int my) {
        for (GuiEventListener widget : widgets) {
            if (widget instanceof FlatButton button && button.visible && button.isHovered()
                    && button.hoverTip() != null) {
                draw(g, font, button.hoverTip(), mx, my);
                return;
            }
        }
    }

    public static void draw(GuiGraphics g, Font font, Component text, int mx, int my) {
        List<FormattedCharSequence> lines = font.split(text, MAX_WIDTH);
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int w = textW + PAD * 2;
        int h = lines.size() * font.lineHeight - 1 + PAD * 2;

        // starts right unless it doesnt have room then its left of cursor
        int x = mx + OFFSET + w <= g.guiWidth() ? mx + OFFSET : Math.max(2, mx - OFFSET - w);
        int y = Math.max(2, Math.min(my - OFFSET, g.guiHeight() - h - 2));

        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        g.fill(x, y, x + w, y + h, Theme.PANEL);
        g.fill(x, y, x + w, y + 1, Theme.BORDER);
        g.fill(x, y + h - 1, x + w, y + h, Theme.BORDER);
        g.fill(x, y, x + 1, y + h, Theme.BORDER);
        g.fill(x + w - 1, y, x + w, y + h, Theme.BORDER);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + PAD, y + PAD + i * font.lineHeight, Theme.TEXT, false);
        }
        g.pose().popPose();
    }
}
