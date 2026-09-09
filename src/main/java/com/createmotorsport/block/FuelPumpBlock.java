package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.FuelPumpBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class FuelPumpBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FuelPumpBlock> CODEC = simpleCodec(FuelPumpBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public FuelPumpBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getClickedPos().getY() >= ctx.getLevel().getMaxBuildHeight() - 1
                || !ctx.getLevel().getBlockState(ctx.getClickedPos().above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(base(state, pos)) instanceof FuelPumpBlockEntity pump) pump.take(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new FuelPumpBlockEntity(pos, state) : null;
    }
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return java.util.List.of();
        var drops = super.getDrops(state, builder);
        var be = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof FuelPumpBlockEntity pump) {
            for (var stack : drops) {
                if (stack.is(CreateMotorsport.FUEL_PUMP_ITEM.get())) {
                    stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                            net.minecraft.world.item.component.CustomData.of(pump.saveWithFullMetadata(builder.getLevel().registryAccess())));
                }
            }
        }
        return drops;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CreateMotorsport.FUEL_PUMP_ENTITY.get() ? (l, p, s, be) -> ((FuelPumpBlockEntity) be).tick() : null;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof FuelPumpBlockEntity pump) pump.reset();
        super.onRemove(state, level, pos, replacement, moving);
        if (!level.isClientSide && !state.is(replacement.getBlock()) && !moving) {
            BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(HALF) != state.getValue(HALF)) {
                if (state.getValue(HALF) == DoubleBlockHalf.UPPER) level.destroyBlock(other, true);
                else level.removeBlock(other, false);
            }
        }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.below();
            if (level.getBlockState(lower).is(this)) level.destroyBlock(lower, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
