package com.createmotorsport.vehicle;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * CarAssembler rebuils an AssembledCar whenever something changes
 * Parts call of() at the beginning of the tick, first axle's substep calls refreshed(), and Sable's onPhysicsTick is calling endPhysicsSubstep()
 *
 * any initialize, remove, or onChunkUnloaded raises generation by one and the car rebuilds next time. keyed by sublevel identity
 */
public final class CarAssembler {

    private static final Map<SubLevel, AssembledCar> CARS = new IdentityHashMap<>();

    private static long generation;
    private static long sweptGeneration = Long.MIN_VALUE;
    private static long physicsSerial;

    private CarAssembler() {
    }

    public static void memberChanged(@Nullable Level level) {
        if (level != null && !level.isClientSide) {
            generation++;
        }
    }

    public static void endPhysicsSubstep(ServerLevel level, double dt) {
        for (AssembledCar car : CARS.values()) {
            car.finishSplit(dt);
            car.flushForces(level, dt);
        }
        physicsSerial++;
    }

    @Nullable
    public static AssembledCar of(BlockEntity part) {
        return part.getLevel() == null ? null : of(part.getLevel(), Sable.HELPER.getContaining(part));
    }

    @Nullable
    public static AssembledCar of(Level level, @Nullable SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || level.isClientSide) {
            return null;
        }
        if (sweptGeneration != generation) {
            CARS.entrySet().removeIf(e -> e.getKey().isRemoved() || e.getValue().isAbandoned());
            sweptGeneration = generation;
        }
        AssembledCar car = CARS.get(subLevel);
        if (car == null) {
            car = new AssembledCar();
            CARS.put(subLevel, car);
        }
        if (car.builtGeneration != generation || car.hasRemovedMember()) {
            SubLevel root = car.bodies().isEmpty() || car.root().isRemoved() ? subLevel : car.root();
            car.rebuild(level, root, generation);
            for (SubLevel body : car.bodies()) {
                CARS.put(body, car);
            }
        }
        return car;
    }

    @Nullable
    public static AssembledCar refreshed(Level level, SubLevel subLevel, double dt) {
        if (subLevel == null || subLevel.isRemoved() || level.isClientSide) {
            return null;
        }
        AssembledCar car = CARS.get(subLevel);
        if (car == null) {
            car = of(level, subLevel);
        }
        if (car != null) {
            car.refresh(physicsSerial, dt);
        }
        return car;
    }
}
