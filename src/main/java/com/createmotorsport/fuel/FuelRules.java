package com.createmotorsport.fuel;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FuelRules {
    public static final TagKey<Fluid> FUELS = TagKey.create(Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "fuels"));

    private FuelRules() {}

    public static boolean accepts(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().is(FUELS);
    }
}
