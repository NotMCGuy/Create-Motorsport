package com.createmotorsport.client.screen;

import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.EngineBlockEntity.ControlChannel;
import com.createmotorsport.client.screen.part.PartScreens;
import com.createmotorsport.client.widget.Theme;
import com.createmotorsport.menu.EngineMenu;
import com.createmotorsport.menu.PartKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EngineScreen extends AbstractContainerScreen<EngineMenu> {

    public EngineScreen(EngineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WirelessPanel.WIDTH;
        this.imageHeight = EngineMenu.HOTBAR_Y + 18 + 6;
        this.inventoryLabelY = EngineMenu.INV_Y - 12;
        this.inventoryLabelX = EngineMenu.INV_X;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(WirelessPanel.back(this, leftPos, topPos, imageWidth,
                () -> PartScreens.open(menu.getEnginePos(), PartKind.ENGINE, menu.canTune())));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
        if (menu.getCarried().isEmpty()) {
            if (isHovering(EngineMenu.EXHAUST_X, EngineMenu.COMPONENT_Y, 16, 16, mx, my)
                    && !menu.slots.get(EngineBlockEntity.SLOT_EXHAUST).hasItem())
                g.renderTooltip(font, Component.translatable("item.createmotorsport.exhaust_manifold"), mx, my);
            if (isHovering(EngineMenu.INTAKE_X, EngineMenu.COMPONENT_Y, 16, 16, mx, my)
                    && !menu.slots.get(EngineBlockEntity.SLOT_INTAKE).hasItem())
                g.renderTooltip(font, Component.translatable("item.createmotorsport.air_intake"), mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        WirelessPanel.frame(g, leftPos, topPos, imageWidth, imageHeight, EngineMenu.INV_X, EngineMenu.INV_Y);
        WirelessPanel.row(g, font, leftPos, topPos + EngineMenu.COMPONENT_Y,
                imageWidth, "Attachments", EngineMenu.EXHAUST_X, EngineMenu.INTAKE_X);
        for (ControlChannel channel : EngineBlockEntity.CHANNELS) {
            WirelessPanel.row(g, font, leftPos, topPos + EngineMenu.CHANNEL_Y0 + channel.ordinal() * EngineMenu.CHANNEL_ROW_H,
                    imageWidth, channel.getDisplayName(), EngineMenu.CHANNEL_SLOT_A_X, EngineMenu.CHANNEL_SLOT_B_X);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, Theme.TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Theme.TEXT_DIM, false);
    }
}
