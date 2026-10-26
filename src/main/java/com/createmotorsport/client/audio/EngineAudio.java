package com.createmotorsport.client.audio;

import com.createmotorsport.audio.EngineAudioProfile;
import com.createmotorsport.audio.EngineSoundMix;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.fuel.FuelCoordinates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public final class EngineAudio {
    private static final WeakHashMap<EngineBlockEntity, Loop[]> LOOPS = new WeakHashMap<>();
    private EngineAudio() { }

    public static void tick(EngineBlockEntity engine) {
        var mc = Minecraft.getInstance();
        var previous = LOOPS.get(engine);
        String profile = engine.getEngineId().location().getPath();
        if (!engine.soundRunning() || engine.isRemoved()) {
            if (previous != null) for (var loop : previous) loop.finish();
            LOOPS.remove(engine);
            return;
        }
        if (previous != null && !previous[0].profile.equals(profile)) {
            for (var loop : previous) loop.finish();
            LOOPS.remove(engine);
            previous = null;
        }
        if (previous == null) {
            previous = new Loop[4];
            for (int band = 0; band < 4; band++) {
                String optional = EngineAudioProfile.optional(profile, band);
                ResourceLocation id = ResourceLocation.parse(EngineAudioProfile.fallback(profile, band));
                if (optional != null && ModList.get().isLoaded("snassets_engines")) {
                    var candidate = ResourceLocation.parse(optional);
                    var event = mc.getSoundManager().getSoundEvent(candidate);
                    if (event != null && event.getWeight() > 0) id = candidate;
                }
                previous[band] = new Loop(engine, profile, band, id);
                mc.getSoundManager().play(previous[band]);
            }
            LOOPS.put(engine, previous);
        } else if (engine.getLevel().getGameTime() % 20 == 0) {
            for (var loop : previous) if (!loop.isStopped() && !mc.getSoundManager().isActive(loop))
                mc.getSoundManager().play(loop);
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final WeakReference<EngineBlockEntity> engine;
        private final String profile;
        private final int band;

        Loop(EngineBlockEntity engine, String profile, int band, ResourceLocation id) {
            super(SoundEvent.createVariableRangeEvent(id), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.engine = new WeakReference<>(engine);
            this.profile = profile;
            this.band = band;
            looping = true;
            delay = 0;
            tick();
        }

        void finish() { stop(); }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            var block = engine.get();
            if (block == null || block.isRemoved() || block.getLevel() != Minecraft.getInstance().level
                    || !block.soundRunning()) { stop(); return; }
            Vec3 position = FuelCoordinates.global(block.getLevel(), Vec3.atCenterOf(block.getBlockPos()));
            x = position.x; y = position.y; z = position.z;
            double rpm = block.soundRpmFraction();
            volume = EngineSoundMix.weight(rpm, band) * (.35f + .5f * block.getThrottle());
            pitch = EngineSoundMix.pitch(rpm, band, profile.contains("truck"));
        }
    }
}
