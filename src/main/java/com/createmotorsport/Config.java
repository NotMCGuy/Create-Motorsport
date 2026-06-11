package com.createmotorsport;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Whether Create: Motorsport should log extra development diagnostics.")
            .define("enableDebugLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
