package com.createmotorsport.race;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class Entrant {
    public BlockPos wheelPos;
    public String name = "";
    public int grid = 0;
    public int lap;
    public int nextMarker; // which marker index we expect to cross next
    public long lastCrossTick;
    public long lapStartTick;
    public long firstCrossTick = -1L;
    public boolean finished;

    public final List<float[]> recording = new ArrayList<>(); // flat x,y,z samples of the lap being driven, for the ghost
    public final List<Long> splits = new ArrayList<>(); // game ticks since race start, one per crossing
    public double[] lastSide = new double[0]; // sign of which side of each gate line the car was on, indexed by gate order
    public long[] lastCrossAt = new long[0]; // game tick that each gate was last triggered, so a car sitting on the line doesnt mess things up

    public Entrant(BlockPos wheelPos, String name, int grid) {
        this.wheelPos = wheelPos;
        this.name = name;
        this.grid = grid;
    }
}
