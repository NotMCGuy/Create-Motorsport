package com.createmotorsport.physics.spec;

/**
 * For making tyre compounds have different surface responses
 * Corner uses SurfaceSpec.scaledBy
 */
public record SurfaceResponse(double peakMuScale, double slipAtPeakScale, double slideGripScale,
                              double rollingResistanceScale) {

    public static final SurfaceResponse NEUTRAL = new SurfaceResponse(1.0, 1.0, 1.0, 1.0);


    public record ByCategory(SurfaceResponse road, SurfaceResponse dirt, SurfaceResponse gravel,
                             SurfaceResponse sand) {

        public static final ByCategory NEUTRAL =
                new ByCategory(SurfaceResponse.NEUTRAL, SurfaceResponse.NEUTRAL,
                        SurfaceResponse.NEUTRAL, SurfaceResponse.NEUTRAL);


        public SurfaceResponse of(SurfaceSpec.Category category) {
            return switch (category) {
                case ROAD -> road;
                case DIRT -> dirt;
                case GRAVEL -> gravel;
                case SAND -> sand;
            };
        }

        public static ByCategory offRoad(SurfaceResponse onRoad, SurfaceResponse onLoose) {
            return new ByCategory(onRoad, onLoose, onLoose, onLoose);
        }

        public static final ByCategory SLICK = offRoad(
                new SurfaceResponse(1.10, 0.85, 1.00, 0.90),
                new SurfaceResponse(0.65, 0.90, 0.95, 1.20));

        public static final ByCategory ALL_TERRAIN = offRoad(
                new SurfaceResponse(0.90, 1.15, 1.00, 1.10),
                new SurfaceResponse(1.45, 1.10, 1.05, 0.70));
    }
}
