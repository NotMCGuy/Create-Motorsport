package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;


// Baked parts rendered by the block entity renderers (just the spinnable dashboard rim for now)
public class MotorsportPartialModels {
    public static final PartialModel DASHBOARD_WHEEL = block("dashboard_wheel");
    public static final PartialModel DOWNFLAP_WING = block("down_flap/wing");

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "block/" + path));
    }

    public static void init() {
    }
}
