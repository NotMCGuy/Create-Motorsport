package com.createmotorsport.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.ClutchBlockEntity;
import com.createmotorsport.menu.ClutchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ClutchBlock extends HorizontalAxisKineticBlock implements EntityBlock {
    public static final MapCodec<ClutchBlock> CODEC = simpleCodec(ClutchBlock::new);
    private static final Component CONTAINER_TITLE = Component.translatable("container.createmotorsport.clutch");

    public ClutchBlock(BlockBehaviour.Properties properties) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return openClutchMenu(level, pos, player) ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return openClutchMenu(level, pos, player) ? ItemInteractionResult.CONSUME : ItemInteractionResult.SUCCESS;
    }

    private boolean openClutchMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return false;
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> {
                    if (level.getBlockEntity(pos) instanceof ClutchBlockEntity clutch) {
                        return new ClutchMenu(containerId, playerInventory, clutch);
                    }
                    return new ClutchMenu(containerId, playerInventory);
                },
                CONTAINER_TITLE
        ));
        return true;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClutchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == CreateMotorsport.CLUTCH_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, blockEntity) -> ClutchBlockEntity.tick(tickerLevel, pos, tickerState, (ClutchBlockEntity) blockEntity)
                : null;
    }
}
