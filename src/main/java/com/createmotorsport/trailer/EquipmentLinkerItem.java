package com.createmotorsport.trailer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class EquipmentLinkerItem extends Item {
    public EquipmentLinkerItem(Properties properties) {super(properties);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext ctx) {
        var player=ctx.getPlayer();var level=ctx.getLevel();
        if(player==null || !(level.getBlockEntity(ctx.getClickedPos()) instanceof EquipmentBlockEntity be))return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!be.canUse(player))return InteractionResult.FAIL;
        if(be.kind()==CouplingRules.Kind.CONTROL_PANEL) {
            CompoundTag tag=new CompoundTag();tag.put("Panel",EquipmentRef.of(be).save());
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("trailer.createmotorsport.select_equipment"),true);
        } else {
            var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            var ref=EquipmentRef.load(tag.getCompound("Panel"));var panel=ref==null?null:ref.resolve(level);
            if(panel==null || !panel.link(be,player))be.feedback(player,CouplingRules.Status.UNLINKED);
            else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("trailer.createmotorsport.linked"),true);
        }
        return InteractionResult.CONSUME;
    }
}
