package com.createmotorsport.race;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class Entrant {
    public BlockPos wheelPos;
    public String name = "";
    public int grid = 0;
    public int lap;
    public int nextMarker;
    public long lastCrossTick;
    public long lapStartTick;
    public long firstCrossTick = -1L;
    public boolean finished;

    public final List<float[]> recording = new ArrayList<>();
    public final List<Long> splits = new ArrayList<>();
    public double[] lastSide = new double[0];
    public long[] lastCrossAt = new long[0];

    public Entrant(BlockPos wheelPos, String name, int grid) {
        this.wheelPos = wheelPos;
        this.name = name;
        this.grid = grid;
    }
}
