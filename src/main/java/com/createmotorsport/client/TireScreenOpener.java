package com.createmotorsport.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TireScreenOpener {

    private TireScreenOpener() {
    }

    public static void open(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TireConfigScreen(stack, hand));
    }
}
