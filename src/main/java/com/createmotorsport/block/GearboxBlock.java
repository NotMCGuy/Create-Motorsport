package com.createmotorsport.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.GearboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class GearboxBlock extends HorizontalAxisKineticBlock implements EntityBlock {
    public static final MapCodec<GearboxBlock> CODEC = simpleCodec(GearboxBlock::new);

    public GearboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hitResult) {
        return changeGear(level, pos, player) ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        return changeGear(level, pos, player) ? ItemInteractionResult.CONSUME : ItemInteractionResult.SUCCESS;
    }

    private boolean changeGear(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return false;
        }

        if (level.getBlockEntity(pos) instanceof GearboxBlockEntity gearbox) {
            gearbox.changeGear(player, player.isShiftKeyDown());
        }
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GearboxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == CreateMotorsport.GEARBOX_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, blockEntity) -> GearboxBlockEntity.tick(tickerLevel, pos, tickerState, (GearboxBlockEntity) blockEntity)
                : null;
    }
}
