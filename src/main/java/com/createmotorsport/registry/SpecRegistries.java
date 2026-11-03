package com.createmotorsport.registry;

import com.createmotorsport.physics.spec.DifferentialSpec;
import com.createmotorsport.physics.spec.GearboxSpec;
import com.createmotorsport.physics.spec.SuspensionSpec;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.physics.spec.EngineSpec;
import com.createmotorsport.physics.spec.TireSpec;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class SpecRegistries {

    public static final ResourceKey<Registry<TireSpec>> TIRE_COMPOUND = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "tire_compound"));

    public static final ResourceKey<Registry<TireEntry>> TIRE = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "tire"));

    public static final ResourceKey<Registry<SuspensionSpec>> SUSPENSION_SETUP = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "suspension_setup"));

    public static final ResourceKey<Registry<EngineSpec>> ENGINE = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "engine"));

    public static final ResourceKey<Registry<GearboxSpec>> GEARBOX = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "gearbox"));

    public static final ResourceKey<Registry<DifferentialSpec>> DIFFERENTIAL = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "differential"));

    public static final ResourceKey<TireSpec> PASSENGER_CAR_70 = compound("passenger_car_70");
    public static final ResourceKey<TireSpec> TRUCK_80 = compound("truck_80");
    public static final ResourceKey<TireSpec> FOREIGN = compound("foreign"); // for tires from any other mod
    public static final ResourceKey<TireSpec> MOTORCYCLE = compound("motorcycle");

    public static final ResourceKey<TireSpec> F1_SLICK_FRONT = compound("f1_slick_front");
    public static final ResourceKey<TireSpec> F1_SLICK_REAR = compound("f1_slick_rear");

    public static final ResourceKey<SuspensionSpec> SOFT = setup("soft");
    public static final ResourceKey<SuspensionSpec> MEDIUM = setup("medium");
    public static final ResourceKey<SuspensionSpec> FIRM = setup("firm");
    public static final ResourceKey<SuspensionSpec> RACE = setup("race");
    public static final ResourceKey<SuspensionSpec> RACE_REAR = setup("race_rear");
    public static final ResourceKey<SuspensionSpec> PASSENGER_STRUT = setup("passenger_strut");
    public static final ResourceKey<SuspensionSpec> TRUCK_STRUT = setup("truck_strut");

    public static final ResourceKey<EngineSpec> F1_V8_2011 = engine("f1_v8_2011");
    public static final ResourceKey<EngineSpec> PASSENGER_I4_TURBO = engine("passenger_i4_turbo");
    public static final ResourceKey<EngineSpec> TRUCK_DIESEL = engine("truck_diesel");
    public static final ResourceKey<EngineSpec> MOTORCYCLE_I4 = engine("motorcycle_i4");

    public static final ResourceKey<DifferentialSpec> DIFF_LSD = differential("lsd");
    public static final ResourceKey<DifferentialSpec> DIFF_OPEN = differential("open");
    public static final ResourceKey<DifferentialSpec> DIFF_SPOOL = differential("spool");
    public static final ResourceKey<DifferentialSpec> DIFF_TRUCK_TANDEM = differential("truck_tandem");

    public static final ResourceKey<GearboxSpec> F1_SEAMLESS_7 = gearbox("f1_seamless_7");
    public static final ResourceKey<GearboxSpec> PASSENGER_DCT_6 = gearbox("passenger_dct_6");
    public static final ResourceKey<GearboxSpec> ROAD_MANUAL_5 = gearbox("road_manual_5");
    public static final ResourceKey<GearboxSpec> MOTORCYCLE_QUICKSHIFT_6 = gearbox("motorcycle_quickshift_6");
    public static final ResourceKey<GearboxSpec> TRUCK_MANUAL_10 = gearbox("truck_manual_10");

    private SpecRegistries() {
    }

    private static ResourceKey<TireSpec> compound(String name) {
        return ResourceKey.create(TIRE_COMPOUND, id(name));
    }

    private static ResourceKey<SuspensionSpec> setup(String name) {
        return ResourceKey.create(SUSPENSION_SETUP, id(name));
    }

    private static ResourceKey<EngineSpec> engine(String name) {
        return ResourceKey.create(ENGINE, id(name));
    }

    private static ResourceKey<DifferentialSpec> differential(String name) {
        return ResourceKey.create(DIFFERENTIAL, id(name));
    }

    private static ResourceKey<GearboxSpec> gearbox(String name) {
        return ResourceKey.create(GEARBOX, id(name));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, name);
    }

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(TIRE_COMPOUND, SpecCodecs.TIRE_SPEC, SpecCodecs.TIRE_SPEC);
        event.dataPackRegistry(TIRE, SpecCodecs.TIRE_ENTRY, SpecCodecs.TIRE_ENTRY);
        event.dataPackRegistry(SUSPENSION_SETUP, SpecCodecs.SUSPENSION_SPEC, SpecCodecs.SUSPENSION_SPEC);
        event.dataPackRegistry(ENGINE, SpecCodecs.ENGINE_SPEC, SpecCodecs.ENGINE_SPEC);
        event.dataPackRegistry(GEARBOX, SpecCodecs.GEARBOX_SPEC, SpecCodecs.GEARBOX_SPEC);
        event.dataPackRegistry(DIFFERENTIAL, SpecCodecs.DIFFERENTIAL_SPEC, SpecCodecs.DIFFERENTIAL_SPEC);
    }

    @Nullable
    public static <T> T resolve(@Nullable RegistryAccess access, ResourceKey<Registry<T>> registry,
                                @Nullable ResourceKey<T> id, @Nullable T fallback) {
        if (access != null && id != null) {
            T spec = access.registry(registry).map(r -> r.get(id)).orElse(null);
            if (spec != null) {
                return spec;
            }
            warnMissing(id);
        }
        return fallback;
    }

    // just so a log can say source,fallback
    public static <T> boolean resolves(@Nullable RegistryAccess access, ResourceKey<Registry<T>> registry,
                                       @Nullable ResourceKey<T> id) {
        return access != null && id != null
                && access.registry(registry).map(r -> r.get(id)).orElse(null) != null;
    }

    private static final java.util.Set<ResourceKey<?>> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnMissing(ResourceKey<?> id) {
        if (WARNED.add(id)) {
            CreateMotorsport.LOGGER.warn(
                    "[SpecRegistries] {} didnt resolve, The datapack entry failed to load. Fallback applied",
                    id.location());
        }
    }

    @Nullable
    public static <T> T resolve(@Nullable RegistryAccess access, ResourceKey<Registry<T>> registry,
                                @Nullable ResourceLocation id, @Nullable T fallback) {
        return resolve(access, registry, id == null ? null : ResourceKey.create(registry, id), fallback);
    }

    public static TireSpec fittedCompound(@Nullable RegistryAccess access,
                                          net.minecraft.world.item.ItemStack tire, boolean front) {
        ResourceLocation id = tire.get(CreateMotorsport.TIRE_COMPOUND);
        if (id != null) {
            TireSpec spec = resolve(access, TIRE_COMPOUND, id, null);
            if (spec != null) {
                return spec;
            }
        }
        if (isForeign(tire)) {
            TireSpec spec = resolve(access, TIRE_COMPOUND, FOREIGN, null);
            if (spec != null) {
                return spec;
            }
        }
        return fittedCompound(access, front);
    }

    // any tires from another mod
    public static boolean isForeign(net.minecraft.world.item.ItemStack tire) {
        return !(tire.getItem() instanceof com.createmotorsport.item.TireItem)
                && !tire.has(CreateMotorsport.TIRE_COMPOUND);
    }

    public static TireSpec fittedCompound(@Nullable RegistryAccess access, boolean front) {
        TireSpec spec = resolve(access, TIRE_COMPOUND, front ? F1_SLICK_FRONT : F1_SLICK_REAR, null);
        return spec != null ? spec : front ? TireSpec.F1_SLICK_FRONT : TireSpec.F1_SLICK_REAR;
    }

    public static GearboxSpec fittedGearbox(@Nullable RegistryAccess access,
                                            ResourceKey<GearboxSpec> key, GearboxSpec fallback) {
        return resolve(access, GEARBOX, key, fallback);
    }

    public static <T> ResourceKey<T> cycle(@Nullable RegistryAccess access, ResourceKey<Registry<T>> registry,
                                           @Nullable ResourceKey<T> current, List<ResourceKey<T>> builtIn,
                                           int step) {
        List<ResourceKey<T>> keys = access == null ? List.of()
                : access.registry(registry)
                        .map(r -> r.registryKeySet().stream()
                                .sorted(Comparator.comparing(ResourceKey::location, ResourceLocation::compareTo))
                                .toList())
                        .orElse(List.of());
        if (keys.isEmpty()) {
            keys = builtIn;
        }
        if (keys.isEmpty()) {
            return current;
        }
        int at = current == null ? -1 : keys.indexOf(current);
        if (at < 0) {
            return keys.get(step >= 0 ? 0 : keys.size() - 1);
        }
        return keys.get(Math.floorMod(at + step, keys.size()));
    }

    public static final List<ResourceKey<EngineSpec>> BUILT_IN_ENGINES =
            List.of(PASSENGER_I4_TURBO, F1_V8_2011, TRUCK_DIESEL, MOTORCYCLE_I4);

    public static final List<ResourceKey<GearboxSpec>> BUILT_IN_GEARBOXES =
            List.of(F1_SEAMLESS_7, PASSENGER_DCT_6, ROAD_MANUAL_5, TRUCK_MANUAL_10, MOTORCYCLE_QUICKSHIFT_6);

    public static final List<ResourceKey<SuspensionSpec>> BUILT_IN_SETUPS =
            List.of(FIRM, MEDIUM, PASSENGER_STRUT, RACE, RACE_REAR, SOFT, TRUCK_STRUT);

    public static final List<ResourceKey<DifferentialSpec>> BUILT_IN_DIFFERENTIALS =
            List.of(DIFF_LSD, DIFF_OPEN, DIFF_SPOOL, DIFF_TRUCK_TANDEM);

    public static EngineSpec fittedEngine(@Nullable RegistryAccess access,
                                          ResourceKey<EngineSpec> key, EngineSpec fallback) {
        return resolve(access, ENGINE, key, fallback);
    }

    public static SuspensionSpec fittedSetup(@Nullable RegistryAccess access,
                                             ResourceKey<SuspensionSpec> key, SuspensionSpec fallback) {
        return resolve(access, SUSPENSION_SETUP, key, fallback);
    }

    public static DifferentialSpec fittedDifferential(@Nullable RegistryAccess access,
                                                      @Nullable ResourceKey<DifferentialSpec> key) {
        return resolve(access, DIFFERENTIAL, key, fallbackDifferential(key));
    }

    public static DifferentialSpec fallbackDifferential(@Nullable ResourceKey<DifferentialSpec> key) {
        if (DIFF_OPEN.equals(key)) {
            return DifferentialSpec.OPEN;
        }
        if (DIFF_SPOOL.equals(key)) {
            return DifferentialSpec.SPOOL;
        }
        if (DIFF_TRUCK_TANDEM.equals(key)) {
            return DifferentialSpec.TRUCK_TANDEM;
        }
        return DifferentialSpec.DEFAULT;
    }

    // race_front to "Race Front"
    public static String displayName(ResourceLocation id) {
        StringBuilder out = new StringBuilder();
        if (!id.getNamespace().equals(CreateMotorsport.MODID)) {
            out.append(id.getNamespace()).append(':');
        }
        boolean upper = true;
        for (char c : id.getPath().toCharArray()) {
            if (c == '_' || c == '/') {
                out.append(' ');
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }
}
