package com.createmotorsport.telemetry;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import com.createmotorsport.physics.spec.EngineSpec;
import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.registry.SpecCodecs;
import com.createmotorsport.registry.SpecRegistries;
import com.createmotorsport.registry.TireEntry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpecDump {

    private SpecDump() {
    }

    public static List<String> lines(@Nullable RegistryAccess access, CarActors car) {
        List<String> manifest = new ArrayList<>();
        Map<String, List<String>> bodies = new LinkedHashMap<>();

        EngineBlockEntity engine = car.engine();
        if (engine != null) {
            ResourceKey<EngineSpec> engineId = engine.getEngineId();
            reference(manifest, "engine", engineId, SpecRegistries.ENGINE, access);
            body(bodies, "engine", engineId, SpecCodecs.ENGINE_SPEC, engine.getDrivetrain().spec());
            overrides(manifest, "engine", engine.getEngineOverrides());

            reference(manifest, "gearbox", engine.getGearboxId(), SpecRegistries.GEARBOX, access);
            body(bodies, "gearbox", engine.getGearboxId(), SpecCodecs.GEARBOX_SPEC,
                    engine.getDrivetrain().gearbox());
            overrides(manifest, "gearbox", engine.getGearboxOverrides());
        }

        List<SuspensionBlockEntity> axles = car.suspensions();
        for (int a = 0; a < axles.size(); a++) {
            SuspensionBlockEntity axle = axles.get(a);
            String prefix = "axle" + a;

            ResourceKey<com.createmotorsport.physics.spec.SuspensionSpec> setupId = axle.getSetupId();
            reference(manifest, prefix + ".setup", setupId, SpecRegistries.SUSPENSION_SETUP, access);

            body(bodies, "suspension_setup", setupId, SpecCodecs.SUSPENSION_SPEC, axle.packSetup());
            overrides(manifest, prefix + ".setup", axle.getSetupOverrides());

            ResourceKey<com.createmotorsport.physics.spec.DifferentialSpec> diffId = axle.getDifferentialId();
            reference(manifest, prefix + ".differential", diffId, SpecRegistries.DIFFERENTIAL, access);
            body(bodies, "differential", diffId, SpecCodecs.DIFFERENTIAL_SPEC, axle.packDifferential());
            overrides(manifest, prefix + ".differential", axle.getDifferentialOverrides());

            double asked = axle.getSetup().naturalFreqHz();
            double got = axle.effectiveSpringFreqHz();
            manifest.add(prefix + ".spring_freq_hz," + got);
            manifest.add(prefix + ".spring_freq.source,"
                    + (got < asked - 1.0e-6 ? "clamped_from_" + asked : "spec"));
            manifest.add(prefix + ".body_mode_omega_dt," + axle.getBodyModeOmegaDt());
            manifest.add(prefix + ".roll_mode_omega_dt," + axle.getRollModeOmegaDt());

            geometry(manifest, prefix + ".ride_height", axle.liftHeight(), axle.isLiftOverridden());
            geometry(manifest, prefix + ".track_width", axle.trackWidth(), axle.isTrackOverridden());
            geometry(manifest, prefix + ".wheel_offset", axle.wheelOffset(), axle.isWheelOffsetOverridden());

            manifest.add(prefix + ".max_steer_deg," + axle.maxSteerDegrees());
            manifest.add(prefix + ".ackermann," + axle.getSetup().ackermann());
            manifest.add(prefix + ".ackermann_k," + axle.ackermannK());
            manifest.add(prefix + ".wheelbase," + axle.getWheelbase());
            double lock = Math.toRadians(axle.maxSteerDegrees());
            manifest.add(prefix + ".lock_inner_deg,"
                    + Math.toDegrees(axle.wheelSteerAngle(lock, WheelSide.LEFT)));
            manifest.add(prefix + ".lock_outer_deg,"
                    + Math.toDegrees(axle.wheelSteerAngle(lock, WheelSide.RIGHT)));

            var brakes = axle.getSetup().brakes();
            manifest.add(prefix + ".brake_strength," + brakes.strength());
            manifest.add(prefix + ".abs," + brakes.abs());
            manifest.add(prefix + ".abs_floor," + brakes.absFloor());
            manifest.add(prefix + ".camber_deg," + axle.getSetup().camberDeg());

            for (WheelSide side : WheelSide.values()) {
                tire(manifest, bodies, prefix + "." + (side == WheelSide.LEFT ? "L" : "R"),
                        access, axle, side);
            }
        }

        List<String> out = new ArrayList<>();
        out.add("");
        out.add("spec,value");
        out.addAll(manifest);
        out.add("");
        out.add("spec_body,value");
        bodies.values().forEach(out::addAll);
        return out;
    }

    private static void tire(List<String> manifest, Map<String, List<String>> bodies, String prefix,
                             @Nullable RegistryAccess access, SuspensionBlockEntity axle, WheelSide side) {
        ItemStack stack = axle.getTire(side);
        if (stack.isEmpty()) {
            manifest.add(prefix + ".tire,none");
            return;
        }
        ResourceLocation entry = stack.get(CreateMotorsport.TIRE);
        manifest.add(prefix + ".tire," + (entry == null ? "none" : quote(entry.toString())));
        TireLike art = stack.get(OffroadDataComponents.TIRE);
        if (art != null) {
            manifest.add(prefix + ".radius," + art.radius());
            manifest.add(prefix + ".model,"
                    + art.model().map(m -> quote(m.toString())).orElse("none"));
            manifest.add(prefix + ".minimum_friction," + art.minimumFriction());
        }
        float designLoad = stack.getOrDefault(CreateMotorsport.TIRE_DESIGN_LOAD, 0.0f);
        float deflection = stack.getOrDefault(CreateMotorsport.TIRE_VERTICAL_DEFLECTION,
                TireEntry.DEFAULT_VERTICAL_DEFLECTION);
        manifest.add(prefix + ".design_load," + (designLoad > 0.0f ? designLoad : axle.foreignDesignLoad()));
        manifest.add(prefix + ".vertical_deflection," + deflection);
        manifest.add(prefix + ".vertical_damping_ratio,"
                + stack.getOrDefault(CreateMotorsport.TIRE_VERTICAL_DAMPING, TireEntry.DEFAULT_VERTICAL_DAMPING));
        manifest.add(prefix + ".mass," + stack.getOrDefault(CreateMotorsport.TIRE_MASS, 0.0f));
        manifest.add(prefix + ".width," + stack.getOrDefault(CreateMotorsport.TIRE_WIDTH, 0.0f));
        if (art != null && designLoad > 0.0f) {
            manifest.add(prefix + ".tire_rate_N," + axle.tireRate(stack, art.radius()));
            manifest.add(prefix + ".tire_rate_rated_N,"
                    + TireEntry.verticalRate(designLoad, art.radius(), deflection));
        }

        boolean front = axle.isFrontAxle();
        ResourceKey<TireSpec> id = compoundId(access, stack, front);
        reference(manifest, prefix + ".compound", id, SpecRegistries.TIRE_COMPOUND, access);
        overrides(manifest, prefix + ".compound", stack.getOrDefault(CreateMotorsport.TIRE_COMPOUND_OVERRIDES,
                new net.minecraft.nbt.CompoundTag()));
        body(bodies, "tire_compound", id, SpecCodecs.TIRE_SPEC,
                SpecRegistries.fittedCompound(access, stack, front));
    }

    private static ResourceKey<TireSpec> compoundId(@Nullable RegistryAccess access, ItemStack tire,
                                                    boolean front) {
        ResourceLocation declared = tire.get(CreateMotorsport.TIRE_COMPOUND);
        if (declared != null) {
            ResourceKey<TireSpec> key = ResourceKey.create(SpecRegistries.TIRE_COMPOUND, declared);
            if (SpecRegistries.resolves(access, SpecRegistries.TIRE_COMPOUND, key)) {
                return key;
            }
        }
        if (SpecRegistries.isForeign(tire) && SpecRegistries.resolves(access, SpecRegistries.TIRE_COMPOUND,
                SpecRegistries.FOREIGN)) {
            return SpecRegistries.FOREIGN;
        }
        return front ? SpecRegistries.F1_SLICK_FRONT : SpecRegistries.F1_SLICK_REAR;
    }

    private static void geometry(List<String> out, String prefix, double blocks, boolean overridden) {
        out.add(prefix + "," + blocks);
        out.add(prefix + ".source," + (overridden ? "override" : "spec"));
    }

    private static <T> void reference(List<String> out, String prefix, ResourceKey<T> id,
                                      ResourceKey<Registry<T>> registry, @Nullable RegistryAccess access) {
        out.add(prefix + ".id," + quote(id.location().toString()));
        out.add(prefix + ".source," + (SpecRegistries.resolves(access, registry, id) ? "datapack" : "fallback"));
    }

    private static <T> void body(Map<String, List<String>> bodies, String kind, ResourceKey<T> id,
                                 Codec<T> codec, @Nullable T spec) {
        if (spec == null) {
            return;
        }
        String prefix = kind + "." + id.location();
        if (bodies.containsKey(prefix)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        bodies.put(prefix, lines);
        codec.encodeStart(JsonOps.INSTANCE, spec)
                .resultOrPartial(err -> lines.add(prefix + ".encode_error," + quote(err)))
                .ifPresent(json -> flatten(lines, prefix, json));
    }

    private static void flatten(List<String> out, String prefix, JsonElement el) {
        if (el instanceof JsonObject obj) {
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                flatten(out, prefix + "." + e.getKey(), e.getValue());
            }
        } else if (el instanceof JsonArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                flatten(out, prefix + "." + i, arr.get(i));
            }
        } else if (el instanceof JsonPrimitive prim && (prim.isNumber() || prim.isBoolean())) {
            out.add(prefix + "," + prim.getAsString());
        } else {
            out.add(prefix + "," + quote(el.getAsString()));
        }
    }

    private static void overrides(List<String> manifest, String prefix, net.minecraft.nbt.CompoundTag tag) {
        overrides(manifest, prefix + ".override", "", tag);
    }

    private static void overrides(List<String> manifest, String prefix, String path,
                                  net.minecraft.nbt.CompoundTag tag) {
        for (String key : tag.getAllKeys()) {
            String name = path.isEmpty() ? key : path + "." + key;
            net.minecraft.nbt.Tag value = tag.get(key);
            if (value instanceof net.minecraft.nbt.CompoundTag nested) {
                overrides(manifest, prefix, name, nested);
            } else if (value instanceof net.minecraft.nbt.NumericTag number) {
                manifest.add(prefix + "." + name + "," + number.getAsNumber());
            } else if (value != null) {
                manifest.add(prefix + "." + name + "," + quote(value.getAsString()));
            }
        }
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
