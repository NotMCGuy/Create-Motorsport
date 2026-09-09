package com.createmotorsport.physics;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class Gravity {


    public static final double DEFAULT = 11.0;

    private Gravity() {
    }

    public static double of(@Nullable Level level) {
        return level == null ? DEFAULT : Math.abs(DimensionPhysicsData.getGravity(level).y());
    }
}
