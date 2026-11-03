package com.createmotorsport.physics.world;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Sable's gravity is 11 by default. Use {@link #of} for any physics */
public final class Gravity {


    public static final double DEFAULT = 11.0;

    private Gravity() {
    }

    public static double of(@Nullable Level level) {
        return level == null ? DEFAULT : Math.abs(DimensionPhysicsData.getGravity(level).y());
    }
}
