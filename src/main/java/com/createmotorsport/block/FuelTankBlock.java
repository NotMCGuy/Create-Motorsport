package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.FuelTankBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

public class FuelTankBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FuelTankBlock> CODEC = simpleCodec(FuelTankBlock::new);

    public FuelTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FuelTankBlockEntity(pos, state); }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FuelTankBlockEntity tank) {
            for (ItemStack stack : drops) {
                if (stack.is(CreateMotorsport.FUEL_TANK_ITEM.get())) {
                    stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tank.saveWithFullMetadata(builder.getLevel().registryAccess())));
                }
            }
        }
        return drops;
    }
}
