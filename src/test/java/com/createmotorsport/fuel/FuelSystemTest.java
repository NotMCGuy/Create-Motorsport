package com.createmotorsport.fuel;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FuelSystemTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.FLUID.bindTags(
                java.util.Map.of(FuelRules.FUELS, List.of(Fluids.LAVA.builtInRegistryHolder())));
    }
    private FluidTank source(int amount) {
        FluidTank source = new FluidTank(16000);
        source.setFluid(new FluidStack(Fluids.LAVA, amount));
        return source;
    }
    @Test void fuelAcceptance() {
        assertTrue(FuelRules.accepts(new FluidStack(Fluids.LAVA, 1)));
        assertFalse(FuelRules.accepts(new FluidStack(Fluids.WATER, 1)));
        assertFalse(FuelRules.accepts(FluidStack.EMPTY));
    }
    @Test void configuredTransfer() {
        FluidTank source = source(1000), target = new FluidTank(2000);
        assertEquals(250, FuelTransfer.transfer(source, target, 250));
        assertEquals(750, source.getFluidAmount());
        assertEquals(250, target.getFluidAmount());
    }
    @Test void partialCapacity() {
        FluidTank source = source(1000), target = new FluidTank(100);
        assertEquals(100, FuelTransfer.transfer(source, target, 250));
        assertEquals(900, source.getFluidAmount());
        assertEquals(100, target.getFluidAmount());
    }
    @Test void depletedSourceAndSelfTransfer() {
        FluidTank source = source(75), target = new FluidTank(1000);
        assertEquals(0, FuelTransfer.transfer(source, source, 250));
        assertEquals(0, FuelTransfer.transfer(source, target, 0));
        assertEquals(75, FuelTransfer.transfer(source, target, 250));
        assertTrue(source.isEmpty());
        assertEquals(75, target.getFluidAmount());
    }
    @Test void emptyFullAndIncompatible() {
        FluidTank empty = source(0), target = new FluidTank(100);
        assertEquals(0, FuelTransfer.transfer(empty, target, 250));
        target.setFluid(new FluidStack(Fluids.LAVA, 100));
        assertEquals(0, FuelTransfer.transfer(source(1000), target, 250));
        target.setFluid(new FluidStack(Fluids.WATER, 50));
        assertEquals(0, FuelTransfer.transfer(source(1000), target, 250));
    }
    @Test void executionDisagreementReturnsRemainder() {
        FluidTank source = source(1000);
        FluidTank target = new FluidTank(1000) {
            @Override public int fill(FluidStack stack, FluidAction action) {
                return super.fill(stack.copyWithAmount(action.execute() ? 30 : stack.getAmount()), action);
            }
        };
        assertEquals(30, FuelTransfer.transfer(source, target, 250));
        assertEquals(1000, source.getFluidAmount() + target.getFluidAmount());
    }
    @Test void fullDrainRollback() {
        FluidTank source = source(100);
        FluidTank target = new FluidTank(1000) {
            @Override public int fill(FluidStack stack, FluidAction action) {
                return action.execute() ? 0 : stack.getAmount();
            }
        };
        assertEquals(0, FuelTransfer.transfer(source, target, 250));
        assertEquals(100, source.getFluidAmount());
        assertEquals(Fluids.LAVA, source.getFluid().getFluid());
    }
    @Test void rangeBoundaryAndNonFinite() {
        assertTrue(FuelCoordinates.inRange(Vec3.ZERO, new Vec3(12, 0, 0), 12));
        assertFalse(FuelCoordinates.inRange(Vec3.ZERO, new Vec3(12.01, 0, 0), 12));
        assertFalse(FuelCoordinates.inRange(Vec3.ZERO, new Vec3(Double.NaN, 0, 0), 12));
    }
    private void close(Vec3 expected, Vec3 actual) { assertTrue(expected.distanceTo(actual) < 1e-6, actual.toString()); }
    @Test void facingAttachment() {
        close(new Vec3(5.0 / 16, 1, 5.5 / 16), FuelCoordinates.attachment(Direction.NORTH));
        close(new Vec3(10.5 / 16, 1, 5.0 / 16), FuelCoordinates.attachment(Direction.EAST));
        close(new Vec3(11.0 / 16, 1, 10.5 / 16), FuelCoordinates.attachment(Direction.SOUTH));
        close(new Vec3(5.5 / 16, 1, 11.0 / 16), FuelCoordinates.attachment(Direction.WEST));
    }
    @Test void normalWorldHasIdentityTransform() {
        Vec3 point = new Vec3(120, 64, -9);
        close(point, FuelCoordinates.transform(point, null));
    }
    @Test void sableTranslationRotationAndPivot() {
        Pose3d pose = new Pose3d();
        pose.position().set(100, 64, 20);
        pose.rotationPoint().set(1000, 0, 1000);
        pose.orientation().rotationY(Math.PI / 2);
        close(new Vec3(100, 64, 19), FuelCoordinates.transform(new Vec3(1001, 0, 1000), pose));
    }
    @Test void ownershipInvalidationAndCooldown() {
        PumpSession session = new PumpSession();
        UUID owner = UUID.randomUUID();
        assertFalse(session.held());
        session.start(owner, 2);
        UUID first = session.id();
        assertTrue(session.matches(owner, first));
        assertFalse(session.matches(UUID.randomUUID(), first));
        assertFalse(session.matches(owner, UUID.randomUUID()));
        assertTrue(session.claimTransfer(10));
        assertFalse(session.claimTransfer(10));
        assertFalse(session.claimTransfer(14));
        assertTrue(session.claimTransfer(15));
        session.reset();
        assertFalse(session.matches(owner, first));
        session.start(owner, 2);
        assertNotEquals(first, session.id());
    }
    @Test void pumpRecipeAndLootDecodeWithRegisteredItems() throws java.io.IOException {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        for (String name : List.of("fuel_pump", "fuel_tank")) {
        try (var recipe = getClass().getResourceAsStream("/data/createmotorsport/recipe/" + name + ".json");
             var loot = getClass().getResourceAsStream("/data/createmotorsport/loot_table/blocks/" + name + ".json")) {
            assertNotNull(recipe);
            assertNotNull(loot);
            var recipeJson = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(recipe, java.nio.charset.StandardCharsets.UTF_8));
            var lootJson = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(loot, java.nio.charset.StandardCharsets.UTF_8));
            assertNotNull(net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, recipeJson).getOrThrow());
            assertNotNull(net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(ops, lootJson).getOrThrow());
        }
        }
    }
    @Test void upperPumpResolvesToOwningBase() {
        var lower = com.createmotorsport.CreateMotorsport.FUEL_PUMP.get().defaultBlockState();
        var upper = lower.setValue(com.createmotorsport.block.FuelPumpBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        var pos = new net.minecraft.core.BlockPos(10, 64, 20);
        assertEquals(pos, com.createmotorsport.block.FuelPumpBlock.base(lower, pos));
        assertEquals(pos, com.createmotorsport.block.FuelPumpBlock.base(upper, pos.above()));
        assertNull(com.createmotorsport.CreateMotorsport.FUEL_PUMP.get().newBlockEntity(pos.above(), upper));
    }

    @Test void fuelTankStorageSurvivesSaveAndLoad() {
        var state = com.createmotorsport.CreateMotorsport.FUEL_TANK.get().defaultBlockState();
        var pos = net.minecraft.core.BlockPos.ZERO;
        var tank = new com.createmotorsport.block.entity.FuelTankBlockEntity(pos, state);
        assertTrue(tank.tank.isFluidValid(new FluidStack(Fluids.LAVA, 1)));
        assertFalse(tank.tank.isFluidValid(new FluidStack(Fluids.WATER, 1)));
        tank.tank.setFluid(new FluidStack(Fluids.LAVA, 725));
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var saved = tank.saveWithFullMetadata(registries);
        var loaded = new com.createmotorsport.block.entity.FuelTankBlockEntity(pos, state);
        loaded.loadWithComponents(saved, registries);
        assertEquals(725, loaded.tank.getFluidAmount());
        assertEquals(Fluids.LAVA, loaded.tank.getFluid().getFluid());
    }

}
