package com.createmotorsport.physics.spec;

public record TerrainAssistSpec(double maxVerticalRate, double maxClimbFraction,
                                double lookAheadBlocks) {

    public static final TerrainAssistSpec OFF = new TerrainAssistSpec(0.0, 0.0, 0.0);
    public static final TerrainAssistSpec DEFAULT = new TerrainAssistSpec(1.0, 1.0, 6.0);

    public boolean enabled() {
        return maxVerticalRate > 0.0 && maxClimbFraction > 0.0 && lookAheadBlocks > 0.0;
    }
}
