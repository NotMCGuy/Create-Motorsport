package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SuspensionBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable {
    public static final MapCodec<SuspensionBlock> CODEC = simpleCodec(SuspensionBlock::new);
    // Making it a full block for now because the underside chassis kept getting caught on the ridge between the suspension and the next block
    private static final VoxelShape SHAPE = Shapes.block();

    public SuspensionBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof WrenchItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.get(OffroadDataComponents.TIRE) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof SuspensionBlockEntity suspension)) {
            return ItemInteractionResult.FAIL;
        }

        WheelSide side = sideFromHit(state, pos, hitResult);
        if (!level.isClientSide) {
            if (!suspension.installTire(side, stack)) {
                return ItemInteractionResult.FAIL;
            }
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return cycle(context, false);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return cycle(context, true);
    }

    private static InteractionResult cycle(UseOnContext context, boolean differential) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof SuspensionBlockEntity suspension)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            String message = differential
                    ? "Differential: " + suspension.cycleDifferential()
                    : "Suspension: " + suspension.cycleSetting() + "  (sneak-click for differential)";
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal(message), true);
            }
            IWrenchable.playRotateSound(level, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SuspensionBlockEntity suspension)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            WheelSide side = sideFromHit(state, pos, hitResult);
            if (!level.isClientSide) {
                ItemStack removed = suspension.removeTire(side);
                if (removed.isEmpty()) {
                    return InteractionResult.PASS;
                }
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.createmotorsport.menu.PartMenus.openPartScreen(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SuspensionBlockEntity suspension) {
            for (ItemStack stack : suspension.getTires()) {
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static WheelSide sideFromHit(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getCounterClockWise();
        double localX = hitResult.getLocation().x - pos.getX() - 0.5;
        double localZ = hitResult.getLocation().z - pos.getZ() - 0.5;
        double towardsLeft = localX * left.getStepX() + localZ * left.getStepZ();
        return towardsLeft >= 0.0 ? WheelSide.LEFT : WheelSide.RIGHT;
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
                ? (tickerLevel, pos, tickerState, blockEntity) -> ((SuspensionBlockEntity) blockEntity).tick()
                : null;
    }

    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state,
                                                 net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return ConfiguredDrops.attach(super.getDrops(state, builder), builder, this, SuspensionBlockEntity.CONFIGURATION_KEYS);
    }
}
