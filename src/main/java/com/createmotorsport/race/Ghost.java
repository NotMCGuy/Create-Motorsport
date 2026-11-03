package com.createmotorsport.race;

import com.createmotorsport.network.GhostSyncPacket;
import net.minecraft.nbt.CompoundTag;

public final class Ghost {

    public static final int SAMPLE_TICKS = 2; // every 2 ticks is one sample
    public static final int MAX_SAMPLES = 18_000; // 30 minute lap at 10 hz should be enough, bound is safer

    private float[] samples = new float[0];
    private int syncedSampleCount; // samples arent synced, so client-side mirror of samples.length
    private long lapTicks;
    private long leadInTicks;
    private String name = "";
    private boolean enabled = true;

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean on) {
        this.enabled = on;
    }

    public boolean present(boolean clientSide) {
        int count = clientSide ? this.syncedSampleCount : this.samples.length;
        return count >= 6 && this.lapTicks > 0;
    }

    public long lapTicks() {
        return this.lapTicks;
    }

    public long leadInTicks() {
        return this.leadInTicks;
    }

    public String name() {
        return this.name;
    }

    public void clear() {
        this.samples = new float[0];
        this.syncedSampleCount = 0;
        this.lapTicks = 0;
        this.leadInTicks = 0L;
        this.name = "";
    }

    // keeps the lap if it beats the stored one
    public boolean offer(Entrant e, long lapTicks) {
        if (!this.enabled || lapTicks <= 0 || e.recording.size() < 2) {
            return false;
        }
        if (present(false) && lapTicks >= this.lapTicks) {
            return false;
        }
        float[] flat = new float[e.recording.size() * 3];
        for (int i = 0; i < e.recording.size(); i++) {
            float[] s = e.recording.get(i);
            flat[i * 3] = s[0];
            flat[i * 3 + 1] = s[1];
            flat[i * 3 + 2] = s[2];
        }
        this.samples = flat;
        this.lapTicks = lapTicks;
        this.leadInTicks = Math.max(0L, e.firstCrossTick);
        this.name = e.name;
        return true;
    }

    public GhostSyncPacket packet(long startGameTime, boolean playing) {
        return new GhostSyncPacket(this.samples, this.lapTicks, SAMPLE_TICKS, startGameTime, this.name, playing);
    }

    public void write(CompoundTag tag, boolean clientPacket) {
        tag.putBoolean("GhostEnabled", this.enabled);
        tag.putLong("GhostLapTicks", this.lapTicks);
        tag.putLong("GhostLeadIn", this.leadInTicks);
        tag.putString("GhostName", this.name);
        tag.putInt("GhostSampleCount", this.samples.length);
        if (clientPacket) {
            return;
        }
        int[] bits = new int[this.samples.length];
        for (int i = 0; i < this.samples.length; i++) {
            bits[i] = Float.floatToIntBits(this.samples[i]);
        }
        tag.putIntArray("GhostSamples", bits);
    }

    public void read(CompoundTag tag, boolean clientPacket) {
        this.enabled = !tag.contains("GhostEnabled") || tag.getBoolean("GhostEnabled");
        this.lapTicks = tag.getLong("GhostLapTicks");
        this.leadInTicks = tag.getLong("GhostLeadIn");
        this.name = tag.getString("GhostName");
        this.syncedSampleCount = tag.getInt("GhostSampleCount");
        if (clientPacket) {
            return;
        }
        int[] bits = tag.getIntArray("GhostSamples");
        if (bits.length > 3 * MAX_SAMPLES) {
            bits = new int[0];
            this.lapTicks = 0;
            this.name = "";
        }
        this.samples = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            this.samples[i] = Float.intBitsToFloat(bits[i]);
        }
    }
}
