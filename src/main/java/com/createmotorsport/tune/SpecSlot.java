package com.createmotorsport.tune;

import com.createmotorsport.registry.SpecOverrides;
import com.createmotorsport.registry.SpecRegistries;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class SpecSlot<T> {

    private final String key;
    private final String title;
    private final ResourceKey<Registry<T>> registry;
    private final Codec<T> codec;
    private final List<ResourceKey<T>> builtIn;
    private final BiFunction<RegistryAccess, ResourceKey<T>, T> resolver;
    private final String idTag;
    private final String overridesTag;

    private ResourceKey<T> id;
    private CompoundTag overrides = new CompoundTag();
    private final SpecOverrides.Memo<T> memo = new SpecOverrides.Memo<>();

    public SpecSlot(String key, String title, ResourceKey<Registry<T>> registry, Codec<T> codec,
                    List<ResourceKey<T>> builtIn, BiFunction<RegistryAccess, ResourceKey<T>, T> resolver,
                    String idTag, ResourceKey<T> initial) {
        this.key = key;
        this.title = title;
        this.registry = registry;
        this.codec = codec;
        this.builtIn = builtIn;
        this.resolver = resolver;
        this.idTag = idTag;
        this.overridesTag = idTag + "Overrides";
        this.id = initial;
    }

    public ResourceKey<T> id() {
        return this.id;
    }

    public void select(ResourceKey<T> id) {
        this.id = id;
        this.overrides = new CompoundTag();
    }

    public CompoundTag overrides() {
        return this.overrides;
    }

    public void setOverrides(CompoundTag overrides) {
        if (!overrides.equals(this.overrides)) {
            this.overrides = overrides;
        }
    }

    public T pack(@Nullable RegistryAccess access) {
        return this.resolver.apply(access, this.id);
    }

    public T fitted(@Nullable RegistryAccess access) {
        return this.memo.get(this.codec, pack(access), this.overrides);
    }

    public ResourceKey<T> cycle(@Nullable RegistryAccess access, int step) {
        this.id = SpecRegistries.cycle(access, this.registry, this.id, this.builtIn, step);
        this.overrides = new CompoundTag();
        return this.id;
    }

    public Tunable.Section<T> section(@Nullable RegistryAccess access, Runnable changed, @Nullable IntConsumer cycle) {
        return new Tunable.Section<>(this.key, this.title, this.id.location(), this.codec, pack(access),
                this.overrides, tag -> {
                    this.overrides = tag;
                    changed.run();
                }, cycle);
    }

    public void write(CompoundTag tag) {
        tag.putString(this.idTag, this.id.location().toString());
        if (!this.overrides.isEmpty()) {
            tag.put(this.overridesTag, this.overrides.copy());
        }
    }

    public void read(CompoundTag tag, Supplier<ResourceKey<T>> absent) {
        ResourceLocation stored = tag.contains(this.idTag) ? ResourceLocation.tryParse(tag.getString(this.idTag)) : null;
        this.id = stored != null ? ResourceKey.create(this.registry, stored) : absent.get();
        setOverrides(tag.getCompound(this.overridesTag));
    }
}
