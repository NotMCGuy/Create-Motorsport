package com.createmotorsport.motorcycle;

import com.createmotorsport.block.SteeringWheelBlock;
import com.createmotorsport.menu.SteeringWheelMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class MotorcycleHandlebarBlock extends SteeringWheelBlock {
    public MotorcycleHandlebarBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return simpleCodec(MotorcycleHandlebarBlock::new); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MotorcycleHandlebarBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == MotorcycleModule.HANDLEBAR_ENTITY.get() ? (l, p, s, be) -> ((MotorcycleHandlebarBlockEntity) be).tick() : null;
    }
}
