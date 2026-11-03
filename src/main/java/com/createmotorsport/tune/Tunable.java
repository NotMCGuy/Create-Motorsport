package com.createmotorsport.tune;

import com.createmotorsport.registry.SpecOverrides;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;


/**
 * Parts that the Tune screen can edit
 * */
public interface Tunable {

    record Section<T>(String key, String title, ResourceLocation specId, Codec<T> codec, T pack,
                      CompoundTag overrides, Consumer<CompoundTag> store, @Nullable IntConsumer cycle) {

        public DataResult<CompoundTag> check(String path, String text) {
            return SpecOverrides.set(codec, pack, overrides, path, text);
        }

        @Nullable
        public String set(String path, String text) {
            DataResult<CompoundTag> result = check(path, text);
            if (result.error().isPresent()) {
                return result.error().get().message();
            }
            store.accept(result.getOrThrow());
            return null;
        }

        public void clear(String path) {
            store.accept(SpecOverrides.clear(overrides, path));
        }
    }

    List<Section<?>> tuneSections();

    @Nullable
    default Section<?> tuneSection(String key) {
        for (Section<?> section : tuneSections()) {
            if (section.key().equals(key)) {
                return section;
            }
        }
        return null;
    }
}
