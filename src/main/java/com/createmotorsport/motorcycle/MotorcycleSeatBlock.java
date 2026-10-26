package com.createmotorsport.motorcycle;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class MotorcycleSeatBlock extends SeatBlock implements net.minecraft.world.level.block.EntityBlock {
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MotorcycleMountBlockEntity(pos,state); }
    public MotorcycleSeatBlock(Properties properties) {
        super(properties, DyeColor.BLACK);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection());
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return mount(state, level, pos, player).consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return mount(state, level, pos, player);
    }
    private InteractionResult mount(BlockState state, Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown() || player.isPassenger()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!level.getEntitiesOfClass(MotorcycleSeatEntity.class, new AABB(pos).inflate(0.1), e -> e.isVehicle()).isEmpty()) return InteractionResult.FAIL;
            MotorcycleSeatEntity seat = new MotorcycleSeatEntity(MotorcycleModule.SEAT_ENTITY.get(), level);
            if (level.getBlockEntity(pos) instanceof MotorcyclePart part) seat.setDefinition(part.appearance().definition);
            seat.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            seat.setYRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot());
            if (!level.addFreshEntity(seat) || !player.startRiding(seat, true)) {
                seat.discard();
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void updateEntityAfterFallOn(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0, 1));
    }
}
