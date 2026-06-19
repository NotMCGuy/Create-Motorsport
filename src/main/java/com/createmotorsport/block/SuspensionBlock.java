package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.offroad.index.OffroadBlocks;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SuspensionBlock extends HorizontalAxisKineticBlock implements EntityBlock {
    public static final MapCodec<SuspensionBlock> CODEC = simpleCodec(SuspensionBlock::new);
    private static final VoxelShape CENTER_COLLISION_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape NORTH_SUPPORT_SHAPE = Block.box(0.0D, 0.0D, -16.0D, 16.0D, 16.0D, 0.0D);
    private static final VoxelShape SOUTH_SUPPORT_SHAPE = Block.box(0.0D, 0.0D, 16.0D, 16.0D, 16.0D, 32.0D);
    private static final VoxelShape WEST_SUPPORT_SHAPE = Block.box(-16.0D, 0.0D, 0.0D, 0.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_SUPPORT_SHAPE = Block.box(16.0D, 0.0D, 0.0D, 32.0D, 16.0D, 16.0D);

    public SuspensionBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        if (face.getAxis() == getRotationAxis(state)) {
            return true;
        }
        for (Direction sideDirection : getSideMountDirections(state)) {
            if (face == sideDirection) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            placeSideWheelMounts(level, pos, state);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            removeOwnedSideWheelMounts(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getSelectableShape(state, level, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CENTER_COLLISION_SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getSelectableShape(state, level, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (isOffroadTire(stack)) {
            Direction mountDirection = getMountDirectionFromHit(state, pos, hitResult);
            if (mountDirection == null) {
                return ItemInteractionResult.FAIL;
            }

            BlockPos mountPos = pos.relative(mountDirection);
            BlockState mountState = level.getBlockState(mountPos);
            if (!mountState.isAir() && !mountState.is(OffroadBlocks.WHEEL_MOUNT.get())) {
                return ItemInteractionResult.FAIL;
            }

            if (!level.isClientSide) {
                if (mountState.isAir()) {
                    placeWheelMount(level, mountPos, mountDirection);
                }
                if (!insertTireIntoWheelMount(stack, level, mountPos, player)) {
                    return ItemInteractionResult.FAIL;
                }
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!stack.is(OffroadBlocks.WHEEL_MOUNT.get().asItem())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Direction mountDirection = getMountDirectionFromHit(state, pos, hitResult);
        if (mountDirection == null) {
            return ItemInteractionResult.FAIL;
        }

        BlockPos mountPos = pos.relative(mountDirection);
        if (!level.getBlockState(mountPos).isAir()) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            placeWheelMount(level, mountPos, mountDirection);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static VoxelShape getSelectableShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = CENTER_COLLISION_SHAPE;
        Direction.Axis axis = state.getValue(HORIZONTAL_AXIS);

        if (axis == Direction.Axis.X) {
            if (!hasWheelMount(level, pos.relative(Direction.NORTH))) {
                shape = Shapes.or(shape, NORTH_SUPPORT_SHAPE);
            }
            if (!hasWheelMount(level, pos.relative(Direction.SOUTH))) {
                shape = Shapes.or(shape, SOUTH_SUPPORT_SHAPE);
            }
        } else {
            if (!hasWheelMount(level, pos.relative(Direction.WEST))) {
                shape = Shapes.or(shape, WEST_SUPPORT_SHAPE);
            }
            if (!hasWheelMount(level, pos.relative(Direction.EAST))) {
                shape = Shapes.or(shape, EAST_SUPPORT_SHAPE);
            }
        }

        return shape;
    }

    private static boolean hasWheelMount(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(OffroadBlocks.WHEEL_MOUNT.get());
    }

    private static boolean isOffroadTire(ItemStack stack) {
        return stack.get(OffroadDataComponents.TIRE) != null;
    }

    private static void placeWheelMount(Level level, BlockPos mountPos, Direction mountDirection) {
        BlockState mountState = OffroadBlocks.WHEEL_MOUNT.getDefaultState()
                .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, mountDirection);
        level.setBlock(mountPos, mountState, 3);
    }

    private static void placeSideWheelMounts(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : getSideMountDirections(state)) {
            BlockPos mountPos = pos.relative(direction);
            if (level.getBlockState(mountPos).isAir()) {
                placeWheelMount(level, mountPos, direction);
            }
        }
    }

    private static void removeOwnedSideWheelMounts(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : getSideMountDirections(state)) {
            BlockPos mountPos = pos.relative(direction);
            BlockState mountState = level.getBlockState(mountPos);
            if (isOwnedWheelMount(mountState, direction)) {
                level.destroyBlock(mountPos, true);
            }
        }
    }

    private static Direction[] getSideMountDirections(BlockState state) {
        return state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X
                ? new Direction[] {Direction.NORTH, Direction.SOUTH}
                : new Direction[] {Direction.WEST, Direction.EAST};
    }

    private static boolean isOwnedWheelMount(BlockState state, Direction direction) {
        return state.is(OffroadBlocks.WHEEL_MOUNT.get())
                && state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING) == direction;
    }

    private static boolean insertTireIntoWheelMount(ItemStack stack, Level level, BlockPos mountPos, Player player) {
        if (!(level.getBlockEntity(mountPos) instanceof WheelMountBlockEntity wheelMount)) {
            return false;
        }

        ItemStack heldStack = wheelMount.getInventory().slot.getStack();
        if (!heldStack.isEmpty()) {
            return false;
        }

        wheelMount.getInventory().slot.setStack(stack.copyWithCount(1));
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        wheelMount.setChanged();
        wheelMount.onStackChanged();
        return true;
    }

    @Nullable
    private static Direction getMountDirectionFromHit(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        Direction.Axis axis = state.getValue(HORIZONTAL_AXIS);
        Direction face = hitResult.getDirection();
        if (face.getAxis().isHorizontal() && face.getAxis() != axis) {
            return face;
        }

        double localX = hitResult.getLocation().x - pos.getX();
        double localZ = hitResult.getLocation().z - pos.getZ();
        if (axis == Direction.Axis.X) {
            return localZ < 0.5D ? Direction.NORTH : Direction.SOUTH;
        }
        if (axis == Direction.Axis.Z) {
            return localX < 0.5D ? Direction.WEST : Direction.EAST;
        }
        return null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuspensionBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, blockEntity) -> SuspensionBlockEntity.tick(tickerLevel, pos, tickerState, (SuspensionBlockEntity) blockEntity)
                : null;
    }
}
