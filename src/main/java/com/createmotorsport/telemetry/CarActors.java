package com.createmotorsport.telemetry;

import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.physics.vehicle.CarState;
import com.createmotorsport.vehicle.AssembledCar;
import com.createmotorsport.vehicle.CarAssembler;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record CarActors(@Nullable EngineBlockEntity engine, List<SuspensionBlockEntity> suspensions, SubLevel subLevel,
                        @Nullable CarState state) {

    @Nullable
    public static CarActors of(Level level, @Nullable SubLevel subLevel) {
        if (subLevel == null) {
            return null;
        }
        AssembledCar car = CarAssembler.of(level, subLevel);
        List<SuspensionBlockEntity> suspensions = new ArrayList<>();
        if (car != null) {
            for (SuspensionBlockEntity s : car.suspensions()) {
                if (!s.isRemoved()) {
                    suspensions.add(s);
                }
            }
        }
        suspensions.sort(Comparator.comparingLong(s -> s.getBlockPos().asLong()));
        return new CarActors(car == null ? null : car.engine(), suspensions, subLevel,
                car == null ? null : car.state());
    }
}
