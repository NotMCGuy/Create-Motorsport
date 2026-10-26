package com.createmotorsport.motorcycle;

import com.createmotorsport.block.SuspensionBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MotorcycleSuspensionBlock extends SuspensionBlock {
    private final boolean fork;
    public MotorcycleSuspensionBlock(Properties properties, boolean fork) { super(properties); this.fork = fork; }
    public boolean isFork() { return fork; }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new MotorcycleSuspensionBlock(properties, fork));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MotorcycleSuspensionBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == MotorcycleModule.SUSPENSION_ENTITY.get() ? (l, p, s, be) -> ((MotorcycleSuspensionBlockEntity) be).tick() : null;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(3, 8, 3, 13, 16, 13);
    }
}
