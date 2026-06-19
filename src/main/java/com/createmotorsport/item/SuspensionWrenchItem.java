package com.createmotorsport.item;

import com.createmotorsport.block.entity.SuspensionBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public class SuspensionWrenchItem extends Item {
    public SuspensionWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SuspensionBlockEntity suspension)) {
            return InteractionResult.PASS;
        }

        if (!context.getLevel().isClientSide) {
            SuspensionBlockEntity.SuspensionStiffness stiffness = suspension.cycleStiffness();
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.literal("Suspension: " + stiffness.getDisplayName()),
                        true
                );
            }
        }

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
