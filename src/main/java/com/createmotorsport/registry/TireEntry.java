package com.createmotorsport.registry;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.physics.world.Gravity;

import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public record TireEntry(ResourceKey<TireSpec> compound, double designMassKg, float radius,
                        Vec3 rotation, Vec3 offset, Optional<ResourceLocation> model,
                        float minimumFriction, float verticalDeflection, float verticalDampingRatio,
                        float mass, float width, Optional<ResourceLocation> item) {

    public static final float DEFAULT_VERTICAL_DEFLECTION = 0.035f;

    public static final float DEFAULT_VERTICAL_DAMPING = 0.06f;

    public float designLoad() {
        return designLoad(designMassKg);
    }

    public static float designLoad(double designMassKg) {
        return (float) (designMassKg * Gravity.DEFAULT / 4.0);
    }

    public static double designMassKg(float designLoad) {
        return designLoad * 4.0 / Gravity.DEFAULT;
    }

    public static double verticalRate(double designLoad, double radius, double deflectionFraction) {
        double sink = Math.max(1.0e-4, deflectionFraction) * Math.max(1.0e-4, radius);
        return designLoad / sink;
    }

    public TireLike art() {
        return new TireLike(radius, rotation, offset, model.orElse(null), minimumFriction);
    }

    public Item carrier() {
        return item.flatMap(net.minecraft.core.registries.BuiltInRegistries.ITEM::getOptional)
                .filter(i -> i instanceof com.createmotorsport.item.TireItem)
                .orElse(CreateMotorsport.RACING_TIRE.get());
    }

    public ItemStack asStack(ResourceLocation id) {
        return asStack(carrier(), id);
    }

    public ItemStack asStack(Item carrier, ResourceLocation id) {
        ItemStack stack = new ItemStack(carrier);
        fit(stack, id);
        return stack;
    }

    public void fit(ItemStack stack, ResourceLocation id) {
        stack.set(CreateMotorsport.TIRE, id);
        stack.set(CreateMotorsport.TIRE_COMPOUND, compound.location());
        stack.set(CreateMotorsport.TIRE_DESIGN_LOAD, designLoad());
        stack.set(CreateMotorsport.TIRE_VERTICAL_DEFLECTION, verticalDeflection);
        stack.set(CreateMotorsport.TIRE_VERTICAL_DAMPING, verticalDampingRatio);
        stack.set(CreateMotorsport.TIRE_MASS, mass);
        stack.set(CreateMotorsport.TIRE_WIDTH, width);
        stack.set(OffroadDataComponents.TIRE, art());
    }
}
