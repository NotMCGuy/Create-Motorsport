package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.menu.SteeringWheelMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;


public class SteeringWheelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SteeringWheelBlock> CODEC = simpleCodec(SteeringWheelBlock::new);
    private static final Component MENU_TITLE = Component.translatable("container.createmotorsport.steering_wheel");

    public static java.util.function.Consumer<BlockPos> clientDriveToggle = pos -> {
    };

    public SteeringWheelBlock(BlockBehaviour.Properties properties) {
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
        // turns blockbench model 90 degrees
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getCounterClockWise());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SteeringWheelBlockEntity wheel)) {
            return InteractionResult.PASS;
        }

        // Right click to drive, sneak+right click to open menu
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new SteeringWheelMenu(id, inv, wheel),
                        MENU_TITLE
                ), pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            clientDriveToggle.accept(pos);
        } else if (wheel.hasUser() && !wheel.isUser(player)) {
            player.displayClientMessage(Component.translatable("createmotorsport.steering_wheel.occupied"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }


    // block entity keeps ghost items for redstone links in NBT
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteeringWheelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == CreateMotorsport.STEERING_WHEEL_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, be) -> ((SteeringWheelBlockEntity) be).tick()
                : null;
    }
}
