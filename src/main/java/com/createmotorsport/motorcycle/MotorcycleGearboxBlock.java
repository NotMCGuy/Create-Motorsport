package com.createmotorsport.motorcycle;

import com.createmotorsport.block.entity.EngineBlockEntity;
import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class MotorcycleGearboxBlock extends HorizontalDirectionalBlock implements net.minecraft.world.level.block.EntityBlock {
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MotorcycleMountBlockEntity(pos,state); }
    public MotorcycleGearboxBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return simpleCodec(MotorcycleGearboxBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }

    public static EngineBlockEntity connectedEngine(Level level, BlockPos arm, Direction forward) {
        BlockPos gearbox = arm.relative(forward);
        if (!level.hasChunkAt(gearbox)) return null;
        BlockState state = level.getBlockState(gearbox);
        if (!(state.getBlock() instanceof MotorcycleGearboxBlock)) return null;
        var armEntity = level.getBlockEntity(arm);
        var gearboxEntity = level.getBlockEntity(gearbox);
        if (armEntity == null || gearboxEntity == null) return null;
        var body = Sable.HELPER.getContaining(armEntity);
        if (Sable.HELPER.getContaining(gearboxEntity) != body) return null;
        for (Direction input : inputDirections(forward)) {
            BlockPos enginePos = gearbox.relative(input);
            if (level.hasChunkAt(enginePos) && level.getBlockEntity(enginePos) instanceof EngineBlockEntity engine
                    && !engine.isRemoved() && Sable.HELPER.getContaining(engine) == body) return engine;
        }
        return null;
    }

    public static boolean hasConnectedGearbox(EngineBlockEntity engine) {
        Level level = engine.getLevel();
        if (level == null || engine.isRemoved()) return false;
        var body = Sable.HELPER.getContaining(engine);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = engine.getBlockPos().relative(side);
            if (!level.hasChunkAt(pos) || !(level.getBlockState(pos).getBlock() instanceof MotorcycleGearboxBlock)) continue;
            var gearbox = level.getBlockEntity(pos);
            if (gearbox != null && !gearbox.isRemoved() && Sable.HELPER.getContaining(gearbox) == body) return true;
        }
        return false;
    }

    public static Direction[] inputDirections(Direction forward) {
        return new Direction[]{forward, forward.getClockWise(), forward.getCounterClockWise()};
    }

    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide) {
            MotorcycleSuspensionBlockEntity rear = null;
            EngineBlockEntity engine = null;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                var be = level.getBlockEntity(pos.relative(side));
                if (be instanceof MotorcycleSuspensionBlockEntity candidate && !candidate.isFrontAxle()
                        && candidate.getBlockPos().relative(candidate.getFacing()).equals(pos)) {
                    rear = candidate;
                    engine = connectedEngine(level, candidate.getBlockPos(), candidate.getFacing());
                    if (engine != null) break;
                }
            }
            String message;
            if (rear == null) message = "Gearbox: place the rear arm beside it, facing toward the gearbox.";
            else if (engine == null) message = "Gearbox disconnected: place an engine beside the gearbox at the same height, on the same body.";
            else if (Sable.HELPER.getContaining(rear) == null) message = "Gearbox connected. Assemble engine, gearbox and rear arm into one moving body to drive.";
            else message = "Gearbox connected | throttle " + Math.round(engine.getThrottle() * 100) + "% | gear "
                    + engine.getDrivetrain().gearDisplay() + " | rear torque "
                    + Math.round(rear.getTelemetry(com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide.LEFT).driveTorqueNm()) + " Nm";
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
