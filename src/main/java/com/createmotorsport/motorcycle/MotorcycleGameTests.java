package com.createmotorsport.motorcycle;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.block.SuspensionBlock;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createmotorsport")
@PrefixGameTestTemplate(false)
public final class MotorcycleGameTests {
    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void wheelOffsetsFollowFacingAndPersist(GameTestHelper helper) {
        var pos = new BlockPos(3,2,3);
        for (var block : new net.minecraft.world.level.block.Block[]{CreateMotorsport.SUSPENSION.get(), MotorcycleModule.FORKS.get(), MotorcycleModule.REAR_ARM.get()}) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                helper.setBlock(pos, block.defaultBlockState().setValue(SuspensionBlock.FACING, facing));
                var axle = (com.createmotorsport.block.entity.SuspensionBlockEntity) helper.getBlockEntity(pos);
                for (double offset : new double[]{-.5, 0, .5}) {
                    helper.assertTrue(axle.setSetupOverride("wheel_offset", Double.toString(offset)) == null, "Wheel offset must be tunable");
                    var expected = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pos))
                            .add(facing.getStepX() * offset, 0, facing.getStepZ() * offset);
                    helper.assertTrue(axle.axleCentre().distanceToSqr(expected) < 1e-12, "Axle centre must follow the offset and facing");
                    var saved = axle.saveCustomOnly(helper.getLevel().registryAccess());
                    axle.clearSetupOverride("wheel_offset");
                    axle.loadWithComponents(saved, helper.getLevel().registryAccess());
                    axle.gameStep();
                    helper.assertTrue(Math.abs(axle.wheelOffset() - offset) < 1e-12, "Wheel offset must survive saving");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void motorcycleSeatClaimsAndReleasesHandlebars(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, MotorcycleModule.SEAT.get());
        helper.setBlock(pos.north(), MotorcycleModule.HANDLEBARS.get());
        var bars = (MotorcycleHandlebarBlockEntity) helper.getBlockEntity(pos.north());
        bars.initialize();
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var seat = new MotorcycleSeatEntity(MotorcycleModule.SEAT_ENTITY.get(), helper.getLevel());
        var absolute = helper.absolutePos(pos);
        seat.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        helper.getLevel().addFreshEntity(seat);
        try {
            helper.assertTrue(player.startRiding(seat, true), "Rider must mount the motorcycle seat");
            seat.tick();
            helper.assertTrue(bars.isUser(player), "Sitting must claim nearby motorcycle controls");
            player.stopRiding();
            helper.assertTrue(!bars.hasUser(), "Dismounting must release controls");
        } finally {
            player.stopRiding();
            seat.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void motorcycleFuelCapabilitiesAndProfiles(GameTestHelper helper) {
        var enginePos = new BlockPos(3, 2, 3);
        helper.setBlock(enginePos, CreateMotorsport.ENGINE_BLOCK.get());
        helper.setBlock(enginePos.south(), MotorcycleModule.GEARBOX.get());
        helper.setBlock(enginePos.south(2), MotorcycleModule.REAR_ARM.get());
        var engine = (com.createmotorsport.block.entity.EngineBlockEntity) helper.getBlockEntity(enginePos);
        engine.beginStep();
        helper.assertTrue(engine.getEngineId().equals(com.createmotorsport.registry.SpecRegistries.MOTORCYCLE_I4), "Bike gearbox must select the upstream motorcycle engine");
        helper.assertTrue(engine.getGearboxId().equals(com.createmotorsport.registry.SpecRegistries.MOTORCYCLE_QUICKSHIFT_6), "Bike gearbox must select the upstream six-speed transmission");
        engine.setEngineOverride("redline_rpm", "12500");
        engine.beginStep();
        helper.assertTrue(!engine.getEngineOverrides().isEmpty(), "Automatic bike defaults must preserve custom tuning");
        var tankPos = enginePos.above();
        helper.setBlock(tankPos, MotorcycleModule.FUEL_TANK.get());
        var tank = (MotorcycleFuelTankBlockEntity) helper.getBlockEntity(tankPos);
        tank.handler().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(com.createmotorsport.fuel.FuelDraw.drain(helper.getLevel(), helper.absolutePos(enginePos), 100), "Engine must draw fuel through slab capability");
        helper.assertTrue(tank.tank.getFluidAmount() == 900, "Normal slab fuel must be consumed");
        var saved = tank.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (MotorcycleFuelTankBlockEntity) BlockEntity.loadStatic(tank.getBlockPos(), tank.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored != null && restored.tank.getFluidAmount() == 900, "Slab fuel must survive reload");
        helper.setBlock(tankPos, MotorcycleModule.CREATIVE_FUEL_TANK.get());
        var creative = (MotorcycleFuelTankBlockEntity) helper.getBlockEntity(tankPos);
        int before = creative.handler().getFluidInTank(0).getAmount();
        helper.assertTrue(com.createmotorsport.fuel.FuelDraw.drain(helper.getLevel(), helper.absolutePos(enginePos), 100), "Creative slab must fuel engines");
        helper.assertTrue(creative.handler().getFluidInTank(0).getAmount() == before, "Creative fuel must not deplete");
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 100)
    public static void motorcycleSuspensionOnSableBody(GameTestHelper helper) {
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 12; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var origin = helper.absolutePos(new BlockPos(6, 2, 6));
        var position = new org.joml.Vector3d(origin.getX(), origin.getY(), origin.getZ());
        var body = com.createmotorsport.trailer.TrailerTestRig.body(helper.getLevel(), position);
        var rearPos = new BlockPos(0, 0, 1);
        var forkPos = new BlockPos(0, 0, -2);
        com.createmotorsport.trailer.TrailerTestRig.place(body, rearPos, MotorcycleModule.REAR_ARM.get().defaultBlockState());
        com.createmotorsport.trailer.TrailerTestRig.place(body, BlockPos.ZERO, MotorcycleModule.GEARBOX.get().defaultBlockState());
        com.createmotorsport.trailer.TrailerTestRig.place(body, new BlockPos(0, 0, -1), CreateMotorsport.ENGINE_BLOCK.get().defaultBlockState());
        com.createmotorsport.trailer.TrailerTestRig.place(body, forkPos, MotorcycleModule.FORKS.get().defaultBlockState());
        var rear = (MotorcycleSuspensionBlockEntity) helper.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(rearPos));
        var fork = (MotorcycleSuspensionBlockEntity) helper.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(forkPos));
        rear.installTire(WheelSide.LEFT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        fork.installTire(WheelSide.LEFT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        com.createmotorsport.trailer.TrailerTestRig.finishPlacement(body, position);
        helper.startSequence().thenExecuteAfter(30, () -> {
            try {
                helper.assertTrue(rear.hasDriveConnection(), "Gearbox must resolve on an assembled Sable body");
                var rearTelemetry = rear.getTelemetry(WheelSide.LEFT);
                var forkTelemetry = fork.getTelemetry(WheelSide.LEFT);
                helper.assertTrue(rearTelemetry.grounded() || forkTelemetry.grounded(), "Motorcycle tyres must raycast against world terrain");
                helper.assertTrue(Double.isFinite(rearTelemetry.springLenM()) && Double.isFinite(forkTelemetry.springLenM()), "Suspension travel must stay finite");
                helper.assertTrue(!rear.getTelemetry(WheelSide.RIGHT).grounded() && !fork.getTelemetry(WheelSide.RIGHT).grounded(), "No phantom second wheels may support the bike");
            } finally {
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel()).removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            }
        }).thenSucceed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void motorcycleSingleWheelAndPersistence(GameTestHelper helper) {
        for (var block : new MotorcycleSuspensionBlock[]{MotorcycleModule.FORKS.get(), MotorcycleModule.REAR_ARM.get()}) {
            BlockPos pos = new BlockPos(block.isFork() ? 2 : 4, 2, 2);
            helper.setBlock(pos, block);
            var be = (MotorcycleSuspensionBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(be.getType() == MotorcycleModule.SUSPENSION_ENTITY.get(), "Motorcycle entity type must be registered separately");
            helper.assertTrue(be.installTire(WheelSide.RIGHT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance()), "Either click side must install the centred wheel");
            helper.assertTrue(be.hasTire(WheelSide.LEFT) && !be.hasTire(WheelSide.RIGHT), "Only one physical tyre must exist");
            helper.assertTrue(!be.installTire(WheelSide.LEFT, CreateMotorsport.TRUCK_TIRE.get().getDefaultInstance()), "Installing a second tyre must fail");
            var tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
            var restored = (MotorcycleSuspensionBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag, helper.getLevel().registryAccess());
            helper.assertTrue(restored != null && restored.hasTire(WheelSide.LEFT), "Wheel must survive a full NBT reload");
            helper.assertTrue(restored.isFrontAxle() == block.isFork(), "Fork/rear role must survive reload");
            restored.toggleAxleEnd();
            helper.assertTrue(restored.isFrontAxle() == block.isFork(), "Axle toggle must not turn forks into a driven rear arm");
            helper.assertTrue(restored.getSideDirection(WheelSide.LEFT).getStepX() == 0 && restored.getSideDirection(WheelSide.LEFT).getStepZ() == 0, "Physical hardpoint must be centred");
            helper.assertTrue(!be.removeTire(WheelSide.RIGHT).isEmpty() && !be.hasAnyTire(), "Either click side must remove exactly one tyre");
        }
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void motorcycleGearboxConnection(GameTestHelper helper) {
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos arm = new BlockPos(4 + (index % 2) * 5, 3, 4 + (index / 2) * 5);
            index++;
            BlockPos gearbox = arm.relative(facing);
            BlockPos engine = gearbox.relative(facing);
            helper.setBlock(arm, MotorcycleModule.REAR_ARM.get().defaultBlockState().setValue(SuspensionBlock.FACING, facing));
            helper.setBlock(engine, CreateMotorsport.ENGINE_BLOCK.get().defaultBlockState().setValue(EngineBlock.FACING, facing));
            var be = (MotorcycleSuspensionBlockEntity) helper.getBlockEntity(arm);
            helper.assertTrue(!be.hasDriveConnection(), "Engine alone must not connect the rear arm");
            helper.setBlock(gearbox, MotorcycleModule.GEARBOX.get().defaultBlockState().setValue(MotorcycleGearboxBlock.FACING, facing));
            helper.assertTrue(be.hasDriveConnection(), "Aligned engine, gearbox and rear arm must connect in " + facing);
            helper.setBlock(gearbox, MotorcycleModule.GEARBOX.get().defaultBlockState().setValue(MotorcycleGearboxBlock.FACING, facing.getOpposite()));
            helper.assertTrue(be.hasDriveConnection(), "Rotating the adapter housing must not silently disconnect torque");
            helper.setBlock(engine, Blocks.AIR);
            BlockPos sideEngine = gearbox.relative(facing.getClockWise());
            helper.setBlock(sideEngine, CreateMotorsport.ENGINE_BLOCK.get().defaultBlockState().setValue(EngineBlock.FACING, facing.getOpposite()));
            helper.assertTrue(be.hasDriveConnection(), "A side-mounted engine must connect; engine torque already accounts for its facing");
            helper.setBlock(sideEngine, Blocks.AIR);
            helper.assertTrue(!be.hasDriveConnection(), "Removing the engine must disconnect");
            helper.setBlock(gearbox, Blocks.AIR);
            helper.assertTrue(!be.hasDriveConnection(), "Removed gearbox must disconnect");
        }
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void motorcycleHandlebarsReuseControls(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MotorcycleModule.HANDLEBARS.get());
        var bars = (MotorcycleHandlebarBlockEntity) helper.getBlockEntity(pos);
        bars.setFrequencyItem(0, new ItemStack(Items.DIAMOND));
        var tag = bars.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (MotorcycleHandlebarBlockEntity) BlockEntity.loadStatic(bars.getBlockPos(), bars.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored != null && restored.getFrequencyItem(0).is(Items.DIAMOND), "Existing steering frequency persistence must work on handlebars");
        helper.assertTrue(restored.getType() == MotorcycleModule.HANDLEBAR_ENTITY.get(), "Handlebars must reload as their own registered type");
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void motorcycleEmptySeatCleansUp(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MotorcycleModule.SEAT.get());
        var seat = new MotorcycleSeatEntity(MotorcycleModule.SEAT_ENTITY.get(), helper.getLevel());
        var world = helper.absolutePos(pos);
        seat.setPos(world.getX() + 0.5, world.getY(), world.getZ() + 0.5);
        helper.getLevel().addFreshEntity(seat);
        seat.tick();
        helper.assertTrue(seat.isRemoved(), "Create seat cleanup must remove an empty motorcycle seat entity");
        helper.succeed();
    }
}
