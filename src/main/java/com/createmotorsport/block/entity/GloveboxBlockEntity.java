package com.createmotorsport.block.entity;

import com.createmotorsport.trailer.TrailerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GloveboxBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items=NonNullList.withSize(9,ItemStack.EMPTY);
    public GloveboxBlockEntity(BlockPos pos,BlockState state){super(TrailerRegistry.GLOVEBOX_ENTITY.get(),pos,state);}
    @Override public int getContainerSize(){return 9;}
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> items){this.items=items;}
    @Override protected Component getDefaultName(){return Component.translatable("block.createmotorsport.glovebox");}
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){return new DispenserMenu(id,inventory,this);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){
        super.saveAdditional(tag,provider);ContainerHelper.saveAllItems(tag,items,provider);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){
        super.loadAdditional(tag,provider);items=NonNullList.withSize(9,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,provider);
    }
    @Override public boolean stillValid(Player player){
        if(level==null||isRemoved()||level.getBlockEntity(worldPosition)!=this)return false;
        var position=new org.joml.Vector3d(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5);
        var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
        if(body!=null)body.logicalPose().transformPosition(position);
        return player.distanceToSqr(position.x,position.y,position.z)<=64;
    }
}
