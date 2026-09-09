package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.client.widget.NumberField;
import com.createmotorsport.network.SetTireDesignLoadPacket;
import com.createmotorsport.physics.Gravity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

// This will end up holding a lot more than just design load, but I need this now for testing
@OnlyIn(Dist.CLIENT)
public class TireConfigScreen extends Screen {

    private static final int PANEL = 0xFF1A1A1A;
    private static final int BORDER = 0xFF555555;
    private static final int W = 180;
    private static final int H = 76;

    private final ItemStack stack;
    private final InteractionHand hand;
    private NumberField loadField;
    private int left;
    private int top;

    public TireConfigScreen(ItemStack stack, InteractionHand hand) {
        super(Component.literal("Tire Setup"));
        this.stack = stack;
        this.hand = hand;
    }

    // The menu asks the user for a whole car mass, but the component holds a corner's design load in newtons
    private double midpointKg() {
        Float load = stack.get(CreateMotorsport.TIRE_DESIGN_LOAD);
        return load == null ? 0.0 : load * 4.0 / Gravity.DEFAULT;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        loadField = new NumberField(this.font, left + 12, top + 34, 92, 16,
                Component.literal("Design load"), 1.0, 100000.0, this::send);
        loadField.showValue(midpointKg());
        addRenderableWidget(loadField);
        setInitialFocus(loadField);
    }

    private void send(double kg) {
        PacketDistributor.sendToServer(new SetTireDesignLoadPacket(hand == InteractionHand.MAIN_HAND, kg));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        g.fill(left, top, left + W, top + H, PANEL);
        g.fill(left, top, left + W, top + 1, BORDER);
        g.fill(left, top + H - 1, left + W, top + H, BORDER);
        g.fill(left, top, left + 1, top + H, BORDER);
        g.fill(left + W - 1, top, left + W, top + H, BORDER);

        g.drawString(font, stack.getHoverName(), left + 12, top + 10, 0xFFFFFFFF, false);
        g.drawString(font, "Design load", left + 12, top + 24, 0xFFAAAAAA, false);
        g.drawString(font, "kg of car", left + 108, top + 38, 0xFF777777, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (loadField != null) {
            loadField.commit();
        }
        super.onClose();
    }
}
