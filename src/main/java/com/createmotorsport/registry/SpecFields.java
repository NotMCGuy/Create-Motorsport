package com.createmotorsport.registry;

import com.createmotorsport.CreateMotorsport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpecFields {

    private SpecFields() {
    }

    public enum Kind { NUMBER, FLAG, LIST, READ_ONLY }

    public record Field(String path, String label, String value, String packValue, boolean overridden, Kind kind) {

        public boolean editable() {
            return kind != Kind.READ_ONLY;
        }
    }

    private static final Set<String> SKIPPED = Set.of("art");

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("ride_height", "blocks"), Map.entry("track_width", "blocks"), Map.entry("wheel_offset", "blocks"),
            Map.entry("max_ride_height", "blocks"), Map.entry("width", "blocks"), Map.entry("radius", "blocks"),
            Map.entry("steer_full_lock_speed", "km/h"),
            Map.entry("design_vehicle_mass_blocks", "in-world"), Map.entry("real_vehicle_mass_kg", "real kg"),
            Map.entry("design_mass_kg", "in-world"), Map.entry("mass", "in-world"),
            Map.entry("engine_inertia", "kg m^2"), Map.entry("shift_interrupt_time", "s"),
            Map.entry("engage_time", "s"), Map.entry("apply_time", "s"));

    private static final List<Map.Entry<String, String>> SUFFIX_UNITS = List.of(
            Map.entry("nm_per_s", "Nm/s"), Map.entry("seconds", "s"), Map.entry("degrees", "deg"),
            Map.entry("blocks", "blocks"), Map.entry("hz", "Hz"), Map.entry("deg", "deg"), Map.entry("rpm", "rpm"),
            Map.entry("nm", "Nm"), Map.entry("kmh", "km/h"), Map.entry("kg", "kg"), Map.entry("s", "s"));

    private static final Set<String> ACRONYMS = Set.of("abs", "tc", "lsd", "rpm", "mgu");

    public static <T> List<Field> of(Codec<T> codec, T pack, CompoundTag overrides) {
        JsonElement packJson = codec.encodeStart(JsonOps.INSTANCE, pack)
                .resultOrPartial(err -> CreateMotorsport.LOGGER.warn("Tune screen cannot list {}: {}", pack, err))
                .orElse(new JsonObject());
        T applied = SpecOverrides.apply(codec, pack, overrides);
        JsonElement live = codec.encodeStart(JsonOps.INSTANCE, applied).result().orElse(packJson);
        List<Field> out = new ArrayList<>();
        if (live instanceof JsonObject liveObject && packJson instanceof JsonObject packObject) {
            walk(liveObject, packObject, applied, "", "", overrides, out);
        }
        return out;
    }

    private static void walk(JsonObject live, JsonObject pack, @Nullable Object spec, String path, String label,
                             CompoundTag overrides, List<Field> out) {
        for (Map.Entry<String, JsonElement> entry : ordered(live, spec)) {
            String name = entry.getKey();
            JsonElement value = entry.getValue();
            JsonElement packValue = pack.get(name);
            String childPath = path.isEmpty() ? name : path + "." + name;
            if (SKIPPED.contains(name)) {
                continue;
            }
            if (value instanceof JsonObject object) {
                walk(object, packValue instanceof JsonObject p ? p : new JsonObject(), component(spec, name),
                        childPath, label + words(name, false) + " > ", overrides, out);
                continue;
            }
            Kind kind = kind(value);
            if (kind == null) {
                continue;
            }
            out.add(new Field(childPath, label + words(name, true), text(value),
                    packValue == null ? "" : text(packValue), SpecOverrides.isSet(overrides, childPath), kind));
        }
    }

    private static List<Map.Entry<String, JsonElement>> ordered(JsonObject json, @Nullable Object spec) {
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(json.entrySet());
        if (spec != null && spec.getClass().isRecord()) {
            RecordComponent[] components = spec.getClass().getRecordComponents();
            entries.sort(Comparator.comparingDouble(e -> rank(e.getKey(), components, spec)));
        }
        return entries;
    }

    private static double rank(String key, RecordComponent[] components, Object spec) {
        for (int i = 0; i < components.length; i++) {
            if (snake(components[i].getName()).equals(key)) {
                return i;
            }
        }
        for (int i = 0; i < components.length; i++) {
            Object value = value(components[i], spec);
            if (value != null && value.getClass().isRecord()) {
                RecordComponent[] inner = value.getClass().getRecordComponents();
                for (int j = 0; j < inner.length; j++) {
                    if (snake(inner[j].getName()).equals(key)) {
                        return i + (j + 1) / 1000.0;
                    }
                }
            }
        }
        return components.length;
    }

    @Nullable
    private static Object component(@Nullable Object spec, String key) {
        if (spec == null || !spec.getClass().isRecord()) {
            return null;
        }
        for (RecordComponent c : spec.getClass().getRecordComponents()) {
            if (snake(c.getName()).equals(key)) {
                return value(c, spec);
            }
        }
        return null;
    }

    @Nullable
    private static Object value(RecordComponent component, Object spec) {
        try {
            return component.getAccessor().invoke(spec);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Kind kind(JsonElement value) {
        if (value instanceof JsonPrimitive p) {
            return p.isBoolean() ? Kind.FLAG : p.isNumber() ? Kind.NUMBER : null;
        }
        if (value instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
                    return Kind.READ_ONLY;
                }
            }
            return Kind.LIST;
        }
        return null;
    }

    static String text(JsonElement value) {
        if (value instanceof JsonPrimitive p) {
            return p.isNumber() ? p.getAsBigDecimal().stripTrailingZeros().toPlainString() : p.getAsString();
        }
        if (value instanceof JsonArray array) {
            List<String> parts = new ArrayList<>();
            for (JsonElement e : array) {
                if (e instanceof JsonObject o) {
                    List<String> inner = new ArrayList<>();
                    o.entrySet().forEach(x -> inner.add(text(x.getValue())));
                    parts.add(String.join(":", inner));
                } else {
                    parts.add(text(e));
                }
            }
            return String.join(", ", parts);
        }
        return value.toString();
    }

    static String words(String name, boolean withUnit) {
        String stem = name;
        String unit = UNITS.get(name);
        for (Map.Entry<String, String> suffix : SUFFIX_UNITS) {
            if (name.endsWith("_" + suffix.getKey())) {
                stem = name.substring(0, name.length() - suffix.getKey().length() - 1);
                unit = unit != null ? unit : suffix.getValue();
                break;
            }
        }
        String[] tokens = stem.split("_");
        int end = tokens.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            String t = tokens[i];
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (ACRONYMS.contains(t)) {
                sb.append(t.toUpperCase(Locale.ROOT));
            } else if (i == 0) {
                sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
            } else {
                sb.append(t);
            }
        }
        if (withUnit && unit != null) {
            sb.append(" (").append(unit).append(')');
        }
        return sb.toString();
    }
}
