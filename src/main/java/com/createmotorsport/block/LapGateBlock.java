package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import com.createmotorsport.menu.LapGateMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

public class LapGateBlock extends Block implements EntityBlock {
    public static final MapCodec<LapGateBlock> CODEC = simpleCodec(LapGateBlock::new);
    private static final Component MENU_TITLE = Component.translatable("container.createmotorsport.lap_gate");

    public LapGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
                                                                  BlockState state, Level level, BlockPos pos,
                                                                  Player player,
                                                                  net.minecraft.world.InteractionHand hand,
                                                                  BlockHitResult hitResult) {
        if (stack.is(CreateMotorsport.LAP_GATE_ITEM.get())) {
            if (!level.isClientSide) {
                com.createmotorsport.item.LapGateBlockItem.armLink(stack, pos);
                player.displayClientMessage(Component.literal(
                        "§e[Gate] 1st post set, place another on the opposite side of the road"), true);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof LapGateBlockEntity gate)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            gate.scanForCars();
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new LapGateMenu(id, inv, gate), MENU_TITLE), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LapGateBlockEntity gate) {
            gate.clearLink();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LapGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return type == CreateMotorsport.LAP_GATE_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, be) -> ((LapGateBlockEntity) be).tick()
                : null;
    }
}
