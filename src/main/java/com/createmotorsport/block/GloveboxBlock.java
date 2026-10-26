package com.createmotorsport.block;

import com.createmotorsport.block.entity.GloveboxBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public class GloveboxBlock extends HorizontalDirectionalBlock implements EntityBlock,
        com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final MapCodec<GloveboxBlock> CODEC=simpleCodec(GloveboxBlock::new);
    public GloveboxBlock(BlockBehaviour.Properties properties) {
        super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new GloveboxBlockEntity(pos,state);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide && level.getBlockEntity(pos) instanceof GloveboxBlockEntity box)player.openMenu(box);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){
        if(!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof GloveboxBlockEntity box){
            Containers.dropContents(level,pos,box);level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return box(0,8,0,16,16,16);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
