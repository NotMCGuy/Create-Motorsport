package com.createmotorsport.client;

import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.EngineBlockEntity.ControlChannel;
import com.createmotorsport.menu.EngineMenu;
import com.createmotorsport.client.widget.NumberField;
import com.createmotorsport.network.SetEngineDesignMassPacket;
import com.createmotorsport.network.ToggleEngineDirectionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

// Temporary screen in the style I am used to making gui's, based on RedstoneLinksScreen from Univeral Keyboard
@OnlyIn(Dist.CLIENT)
public class EngineScreen extends AbstractContainerScreen<EngineMenu> {
    private static final int FREQ_A_TINT = 0x55FF3333; // transparent red
    private static final int FREQ_B_TINT = 0x553333FF; // transparent blue
    private static final int PANEL = 0xFF1A1A1A;
    private static final int BORDER = 0xFF555555;
    private static final int ROW = 0xFF2A2A2A;
    private static final int SLOT_EDGE = 0xFF3D3D3D;
    private static final int SLOT_BASE = 0xFF1F1F1F;

    // toggle button for the crank rotation direction (+1 / -1)
    private static final int DIR_BTN_W = 68;
    private static final int DIR_BTN_H = 14;
    private static final int DIR_BTN_X = 100;
    private static final int DIR_BTN_Y = 18;
    private static final int DIR_FORWARD = 0xFF35506A;
    private static final int DIR_REVERSE = 0xFF6A3535;

    // Design mass text box
    private static final int MASS_X = 178;
    private static final int MASS_W = 54;
    private static final int MASS_H = 14;
    private NumberField massField;

    public EngineScreen(EngineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 244;
        this.imageHeight = EngineMenu.HOTBAR_Y + 18 + 6;
        this.inventoryLabelY = EngineMenu.INV_Y - 12;
        this.inventoryLabelX = EngineMenu.INV_X;
    }

    @Override
    protected void init() {
        super.init();
        massField = new NumberField(this.font, leftPos + MASS_X, topPos + EngineMenu.COMPONENT_Y - 1,
                MASS_W, MASS_H, Component.literal("Design mass"), 1.0, 100000.0, this::sendDesignMass);
        massField.showValue(getDesignMass());
        addRenderableWidget(massField);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (massField != null && !massField.isFocused()) {
            massField.showValue(getDesignMass());
        }
    }

    private void sendDesignMass(double blocks) {
        BlockPos pos = menu.getEnginePos();
        if (pos != null) {
            PacketDistributor.sendToServer(new SetEngineDesignMassPacket(pos, blocks));
        }
    }

    private double getDesignMass() {
        BlockPos pos = menu.getEnginePos();
        if (pos != null && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            return engine.getDesignMass();
        }
        return 0.0;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (massField != null && massField.isFocused() && key != 256) {
            return massField.keyPressed(key, scan, mods) || massField.canConsumeInput();
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = leftPos;
        int t = topPos;
        int w = imageWidth;
        int h = imageHeight;

        g.fill(l, t, l + w, t + h, PANEL);
        g.fill(l, t, l + w, t + 1, BORDER);
        g.fill(l, t + h - 1, l + w, t + h, BORDER);
        g.fill(l, t, l + 1, t + h, BORDER);
        g.fill(l + w - 1, t, l + w, t + h, BORDER);

        // Component slots (exhaust / intake)
        drawSlot(g, l + EngineMenu.EXHAUST_X, t + EngineMenu.COMPONENT_Y, 0);
        drawSlot(g, l + EngineMenu.INTAKE_X, t + EngineMenu.COMPONENT_Y, 0);
        g.drawString(font, "Components", l + 12, t + EngineMenu.COMPONENT_Y + 4, 0xFFFFFFFF, false);

        g.drawString(font, "Design mass", l + MASS_X, t + EngineMenu.COMPONENT_Y - 12, 0xFFAAAAAA, false);
        g.drawString(font, "blocks", l + MASS_X, t + EngineMenu.COMPONENT_Y + 16, 0xFF777777, false);

        // Channel rows
        for (ControlChannel channel : EngineBlockEntity.CHANNELS) {
            int y = t + EngineMenu.CHANNEL_Y0 + channel.ordinal() * EngineMenu.CHANNEL_ROW_H;
            g.fill(l + 8, y - 2, l + w - 8, y + 16, ROW);
            g.drawString(font, channel.getDisplayName(), l + 12, y + 4, 0xFFFFFFFF, false);
            drawSlot(g, l + EngineMenu.CHANNEL_SLOT_A_X, y, FREQ_A_TINT);
            drawSlot(g, l + EngineMenu.CHANNEL_SLOT_B_X, y, FREQ_B_TINT);
        }

        // Rotation direction toggle for engine crank
        int dir = getRotationDirection();
        int bx = l + DIR_BTN_X;
        int by = t + DIR_BTN_Y;
        g.fill(bx, by, bx + DIR_BTN_W, by + DIR_BTN_H, dir > 0 ? DIR_FORWARD : DIR_REVERSE);
        g.fill(bx, by, bx + DIR_BTN_W, by + 1, BORDER);
        g.fill(bx, by + DIR_BTN_H - 1, bx + DIR_BTN_W, by + DIR_BTN_H, BORDER);
        String dirLabel = "Rotation: " + (dir > 0 ? "1" : "-1");
        g.drawString(font, dirLabel, bx + (DIR_BTN_W - font.width(dirLabel)) / 2, by + 3, 0xFFFFFFFF, false);

        // Player inventory backing
        int invY = t + EngineMenu.INV_Y;
        g.fill(l + EngineMenu.INV_X - 4, invY - 2, l + w - 8, t + h - 4, 0xFF222222);
    }

    private int getRotationDirection() {
        BlockPos pos = menu.getEnginePos();
        if (pos != null && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof EngineBlockEntity engine) {
            return engine.getRotationDirection();
        }
        return 1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bx = leftPos + DIR_BTN_X;
        int by = topPos + DIR_BTN_Y;
        if (button == 0 && mx >= bx && mx < bx + DIR_BTN_W && my >= by && my < by + DIR_BTN_H) {
            BlockPos pos = menu.getEnginePos();
            if (pos != null) {
                PacketDistributor.sendToServer(new ToggleEngineDirectionPacket(pos));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void drawSlot(GuiGraphics g, int x, int y, int tint) {
        g.fill(x - 1, y - 1, x + 17, y, SLOT_EDGE);
        g.fill(x - 1, y, x, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT_BASE);
        if (tint != 0) {
            g.fill(x, y, x + 16, y + 16, tint);
        }
        g.fill(x + 16, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x - 1, y + 16, x + 17, y + 17, SLOT_EDGE);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, 0xFFFFFFFF, false);

        int gearCode = menu.getGearCode();
        String gear = switch (gearCode) {
            case 0 -> "R";
            case 1 -> "N";
            default -> String.valueOf(gearCode - 1);
        };
        String readout = menu.getRpm() + " RPM   Gear " + gear;
        g.drawString(font, readout, 8, 20, 0xFF66FF66, false);

        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFAAAAAA, false);
    }
}
