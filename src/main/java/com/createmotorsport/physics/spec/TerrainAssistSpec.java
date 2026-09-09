package com.createmotorsport.physics.spec;

public record TerrainAssistSpec(double maxVerticalRate, double maxClimbFraction,
                                double lookAheadBlocks, double extraMaxSlope,
                                double extraMaxClimbBlocks, double extraLookAheadBlocks) {

    public static final TerrainAssistSpec OFF = new TerrainAssistSpec(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    public static final TerrainAssistSpec DEFAULT =
            new TerrainAssistSpec(1.0, 1.0, 6.0, 0.4, 1.25, 14.0);

    public boolean enabled() {
        return maxVerticalRate > 0.0 && maxClimbFraction > 0.0 && lookAheadBlocks > 0.0;
    }
}
