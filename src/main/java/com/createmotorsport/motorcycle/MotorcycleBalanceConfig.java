package com.createmotorsport.motorcycle;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MotorcycleBalanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Grounded balance assistance for bodies with exactly one motorcycle fork and rear arm.")
            .define("balanceEnabled", true);
    public static final ModConfigSpec.DoubleValue MAX_LEAN = BUILDER
            .comment("Maximum cornering lean in degrees. Bikes remain upright at walking speed.")
            .defineInRange("maximumLeanDegrees", 40.0, 0.0, 55.0);
    public static final ModConfigSpec.BooleanValue HANDLING = BUILDER
            .comment("Motorcycle-specific speed steering, grounded heading assistance and pitch guard.")
            .define("handlingAssistance", true);
    public static final ModConfigSpec.DoubleValue CORNERING_G = BUILDER
            .comment("Limit the requested cornering acceleration; lower values make high-speed input gentler.")
            .defineInRange("maximumCorneringG", .65, .2, 1.0);
    public static final ModConfigSpec.DoubleValue BRAKE_GAIN = BUILDER
            .comment("Motorcycle brake response strength before front/rear bias and lock-up relief.")
            .defineInRange("brakeGain", 1.25, .5, 2.0);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private MotorcycleBalanceConfig() { }
}
