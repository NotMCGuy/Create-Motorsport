package com.createmotorsport.motorcycle;

import com.createmotorsport.block.FuelTankBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

public final class MotorcycleFuelTankBlock extends FuelTankBlock {
    public final boolean creative;
    public MotorcycleFuelTankBlock(Properties properties, boolean creative) {
        super(properties); this.creative = creative;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new MotorcycleFuelTankBlock(properties, creative));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(BlockStateProperties.HALF);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean top = context.getClickedFace() == Direction.DOWN || context.getClickedFace() != Direction.UP
                && context.getClickLocation().y - context.getClickedPos().getY() > .5;
        return super.getStateForPlacement(context).setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BlockStateProperties.HALF) == Half.TOP ? box(0,8,0,16,16,16) : box(0,0,0,16,8,16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MotorcycleFuelTankBlockEntity(pos,state); }
    @Override protected List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var drops = super.getDrops(state,builder);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof MotorcycleFuelTankBlockEntity tank)
            for (var stack : drops) if (stack.is(asItem())) stack.set(DataComponents.BLOCK_ENTITY_DATA,
                    CustomData.of(tank.saveWithFullMetadata(builder.getLevel().registryAccess())));
        return drops;
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
            Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player,hand,level,pos,hit.getDirection()))
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MotorcycleFuelTankBlockEntity tank) {
            var fluid=tank.handler().getFluidInTank(0);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(creative ? "Creative slab tank: unlimited fuel" :
                    "Slab tank: " + fluid.getAmount() + " / " + tank.tank.getCapacity() + " mB"),true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
