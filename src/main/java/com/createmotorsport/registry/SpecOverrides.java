package com.createmotorsport.registry;

import com.createmotorsport.CreateMotorsport;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * A part's Tune screen edits, saved as only the fields that changed
 */
public final class SpecOverrides {

    private SpecOverrides() {
    }

    // the pack spec unchanged if they no longer decode
    public static <T> T apply(Codec<T> codec, T base, CompoundTag overrides) {
        if (overrides.isEmpty()) {
            return base;
        }
        return merged(codec, base, overrides)
                .resultOrPartial(err -> CreateMotorsport.LOGGER.warn("Ignoring spec overrides {}: {}", overrides, err))
                .orElse(base);
    }

    // returns a new tag, a part replaces its tag, which is what Memo notices
    public static <T> DataResult<CompoundTag> set(Codec<T> codec, T base, CompoundTag overrides, String path,
                                                  String text) {
        CompoundTag next = overrides.copy();
        String[] keys = path.split("\\.");
        CompoundTag node = next;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!node.contains(keys[i], Tag.TAG_COMPOUND)) {
                node.put(keys[i], new CompoundTag());
            }
            node = node.getCompound(keys[i]);
        }
        node.put(keys[keys.length - 1], parse(text));
        return merged(codec, base, next).map(spec -> next);
    }

    public static CompoundTag clear(CompoundTag overrides, String path) {
        CompoundTag next = overrides.copy();
        remove(next, path.split("\\."), 0);
        return next;
    }

    public static boolean isSet(CompoundTag overrides, String path) {
        String[] keys = path.split("\\.");
        CompoundTag node = overrides;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!node.contains(keys[i], Tag.TAG_COMPOUND)) {
                return false;
            }
            node = node.getCompound(keys[i]);
        }
        return node.contains(keys[keys.length - 1]);
    }

    public static <T> CompoundTag diff(Codec<T> codec, T base, T value) {
        CompoundTag out = new CompoundTag();
        if (codec.encodeStart(NbtOps.INSTANCE, base).result().orElse(null) instanceof CompoundTag b
                && codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null) instanceof CompoundTag v) {
            diff(b, v, out);
        }
        return out;
    }

    private static void diff(CompoundTag base, CompoundTag value, CompoundTag out) {
        for (String key : value.getAllKeys()) {
            Tag v = value.get(key);
            Tag b = base.get(key);
            if (v instanceof CompoundTag vc && b instanceof CompoundTag bc) {
                CompoundTag child = new CompoundTag();
                diff(bc, vc, child);
                if (!child.isEmpty()) {
                    out.put(key, child);
                }
            } else if (!same(b, v)) {
                out.put(key, v.copy());
            }
        }
    }

    private static boolean same(Tag a, Tag b) {
        if (a instanceof NumericTag x && b instanceof NumericTag y) {
            double p = x.getAsDouble(), q = y.getAsDouble();
            return Math.abs(p - q) <= 1.0e-6 * Math.max(1.0, Math.abs(p));
        }
        return b.equals(a);
    }

    // list is comma-separated, a trailing comma makes a one item list
    static Tag parse(String text) {
        String t = text.trim();
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
            return ByteTag.valueOf(Boolean.parseBoolean(t));
        }
        if (t.contains(",")) {
            ListTag list = new ListTag();
            for (String part : t.split(",")) {
                if (part.isBlank()) {
                    continue;
                }
                Double number = number(part.trim());
                if (number == null) {
                    return StringTag.valueOf(t);
                }
                list.add(DoubleTag.valueOf(number));
            }
            return list;
        }
        Double number = number(t);
        return number != null ? DoubleTag.valueOf(number) : StringTag.valueOf(t);
    }

    private static Double number(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T> DataResult<T> merged(Codec<T> codec, T base, CompoundTag overrides) {
        return codec.encodeStart(NbtOps.INSTANCE, base)
                .flatMap(encoded -> encoded instanceof CompoundTag fields
                        ? DataResult.success(fields.copy().merge(overrides))
                        : DataResult.<CompoundTag>error(() -> "spec does not encode as a record"))
                .flatMap(tag -> decode(codec, tag));
    }

    // if the constructor refuses
    private static <T> DataResult<T> decode(Codec<T> codec, CompoundTag tag) {
        try {
            return codec.parse(NbtOps.INSTANCE, tag);
        } catch (RuntimeException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static boolean remove(CompoundTag node, String[] keys, int depth) {
        String key = keys[depth];
        if (depth == keys.length - 1) {
            node.remove(key);
        } else if (node.contains(key, Tag.TAG_COMPOUND)) {
            CompoundTag child = node.getCompound(key);
            if (remove(child, keys, depth + 1)) {
                node.remove(key);
            }
        }
        return node.isEmpty();
    }


    // caches apply(), redone only when the pack spec or the tag is a different object
    public static final class Memo<T> {
        private T base;
        private CompoundTag overrides;
        private T result;

        public T get(Codec<T> codec, T base, CompoundTag overrides) {
            if (base != this.base || overrides != this.overrides) {
                this.base = base;
                this.overrides = overrides;
                this.result = apply(codec, base, overrides);
            }
            return this.result;
        }
    }
}
