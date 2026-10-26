package com.createmotorsport.registry;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.physics.spec.EngineSpec;
import com.createmotorsport.physics.spec.GearboxSpec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;

public enum EngineKind {
    RACING(SpecRegistries.F1_V8_2011, SpecRegistries.F1_SEAMLESS_7,
            EngineSpec.F1_V8_2011, GearboxSpec.F1_SEAMLESS_7),
    PASSENGER(SpecRegistries.PASSENGER_I4_TURBO, SpecRegistries.PASSENGER_DCT_6,
            EngineSpec.PASSENGER_I4_TURBO, GearboxSpec.PASSENGER_DCT_6),
    TRUCK(SpecRegistries.TRUCK_DIESEL, SpecRegistries.TRUCK_MANUAL_10,
            EngineSpec.TRUCK_DIESEL, GearboxSpec.TRUCK_MANUAL_10);

    private final ResourceKey<EngineSpec> engine;
    private final ResourceKey<GearboxSpec> gearbox;
    private final EngineSpec engineFallback;
    private final GearboxSpec gearboxFallback;

    EngineKind(ResourceKey<EngineSpec> engine, ResourceKey<GearboxSpec> gearbox,
               EngineSpec engineFallback, GearboxSpec gearboxFallback) {
        this.engine = engine;
        this.gearbox = gearbox;
        this.engineFallback = engineFallback;
        this.gearboxFallback = gearboxFallback;
    }

    public ResourceKey<EngineSpec> engine() {
        return this.engine;
    }

    public ResourceKey<GearboxSpec> gearbox() {
        return this.gearbox;
    }

    public static EngineKind of(BlockState state) {
        if (state.is(CreateMotorsport.TRUCK_ENGINE_BLOCK.get())) {
            return TRUCK;
        }
        return state.is(CreateMotorsport.PASSENGER_ENGINE_BLOCK.get()) ? PASSENGER : RACING;
    }

    public static EngineSpec engineFallback(ResourceKey<EngineSpec> key, EngineKind kind) {
        if (key.equals(SpecRegistries.MOTORCYCLE_I4)) return EngineSpec.MOTORCYCLE_I4;
        for (EngineKind k : values()) {
            if (k.engine == key) {
                return k.engineFallback;
            }
        }
        return kind.engineFallback;
    }

    public static GearboxSpec gearboxFallback(ResourceKey<GearboxSpec> key, EngineKind kind) {
        if (key.equals(SpecRegistries.MOTORCYCLE_QUICKSHIFT_6)) return GearboxSpec.MOTORCYCLE_QUICKSHIFT_6;
        for (EngineKind k : values()) {
            if (k.gearbox == key) {
                return k.gearboxFallback;
            }
        }
        return kind.gearboxFallback;
    }
}
