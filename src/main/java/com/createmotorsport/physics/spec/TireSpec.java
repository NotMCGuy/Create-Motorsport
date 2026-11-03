package com.createmotorsport.physics.spec;

import net.minecraft.util.Mth;

public record TireSpec(
        double grip,
        double loadFactorLight,
        double loadFactorHeavy,
        double loadFalloff,

        ModelParams params,
        SurfaceResponse.ByCategory surfaces,
        ThermalSpec thermal,
        double rollingResistance
) {

    public static final double DEFAULT_ROLLING_RESISTANCE = 0.015;

    public Model model() {
        return params.model();
    }

    public <T extends ModelParams> T params(Class<T> type) {
        if (!type.isInstance(params)) {
            throw new IllegalStateException("compound runs " + model().id() + ", asked for " + type.getSimpleName());
        }
        return type.cast(params);
    }

    private ModelParams.TMeasy tmeasy() {
        return params(ModelParams.TMeasy.class);
    }

    public Axis longitudinal() {
        return tmeasy().longitudinal();
    }

    public Axis lateral() {
        return tmeasy().lateral();
    }

    public double relaxLengthLat() {
        return tmeasy().relaxLengthLat();
    }

    public enum Model {
        ARCADE("arcade", "Arcade"),
        PACEJKA("pacejka", "Pacejka"),
        FIALA("fiala", "Fiala"),
        TMEASY("tmeasy", "TMeasy");

        private final String id;
        private final String label;

        Model(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return this.id;
        }

        public String label() {
            return this.label;
        }

        public static Model byId(String id) {
            for (Model m : values()) {
                if (m.id.equals(id)) {
                    return m;
                }
            }
            return null;
        }
    }

    /** @param peakRatio peak force relative to lateral axis, lat = 1 */
    public record Axis(double slipAtPeak, double peakRatio, double slideGrip, double slideSlipFactor,
                       double initialStiffness) {

    }

    // Pacejka B/C/E done the same as Speed dreams: C = 2 - asin(RFactor)*2/PI, B = Ca/C, E = EFactor.
    // TireModel.longitudinalForce calls these for the arcade model too, so a compound with no Pacejka params
    // answers with PACEJKA_DEFAULTS
    private ModelParams.Pacejka pacejka() {
        return params instanceof ModelParams.Pacejka p ? p : PACEJKA_DEFAULTS;
    }

    public double pacejkaC() {
        return 2.0 - Math.asin(Mth.clamp(pacejka().rFactor(), 0.1, 1.0)) * 2.0 / Math.PI;
    }

    public double pacejkaB() {
        return pacejka().corneringStiffness() / pacejkaC();
    }

    public double pacejkaE() {
        return Math.min(1.0, pacejka().eFactor());
    }

    // Rill Fig 3.43 and 3.45, 0.65 m scaled by lambda
    public static final double DEFAULT_RELAX_LENGTH_LAT = ModelParams.TMeasy.DEFAULT_RELAX_LENGTH_LAT;

    private static final double SLICK_LOAD_FACTOR_LIGHT = 1.15;
    private static final double SLICK_LOAD_FACTOR_HEAVY = 0.80;
    private static final double SLICK_LOAD_FALLOFF = -0.6;

    public static final ModelParams.Pacejka PACEJKA_DEFAULTS = new ModelParams.Pacejka(30.0, 0.8, 0.7);

    public static final ModelParams.Fiala FIALA_DEFAULTS =
            new ModelParams.Fiala(5000.0, 1600.0, 1.5, 0.9, 2.0);

    public static final double TMEASY_BRISTLE_DAMPING = 1.0;
    public static final double TMEASY_STANDSTILL_LEAK = 2.0;
    public static final double TMEASY_STANDSTILL_SPEED = 2.0;

    private static ModelParams.TMeasy tmeasyParams(Axis lon, Axis lat, double loadRise) {
        return new ModelParams.TMeasy(lon, lat, loadRise, DEFAULT_RELAX_LENGTH_LAT,
                TMEASY_BRISTLE_DAMPING, TMEASY_STANDSTILL_LEAK, TMEASY_STANDSTILL_SPEED);
    }

    // Chrono's two TMeasy guess sets, ChTMeasyTire.cpp GuessPassCar70Par and GuessTruck80Par
    public static final TireSpec PASSENGER_CAR_70 = new TireSpec(
            0.80,
            SLICK_LOAD_FACTOR_LIGHT, SLICK_LOAD_FACTOR_HEAVY, SLICK_LOAD_FALLOFF,
            tmeasyParams(new Axis(0.13913, 1.12820, 0.70979, 4.79171, 2.26389),
                    new Axis(0.14852, 1.0,     0.83285, 6.49906, 2.37160), 1.24589),
            SurfaceResponse.ByCategory.NEUTRAL, ThermalSpec.DEFAULT, DEFAULT_ROLLING_RESISTANCE);

    public static final TireSpec TRUCK_80 = new TireSpec(
            1.0,
            SLICK_LOAD_FACTOR_LIGHT, SLICK_LOAD_FACTOR_HEAVY, SLICK_LOAD_FALLOFF,
            tmeasyParams(new Axis(0.10811, 1.10870, 0.51330, 6.17000, 3.06000),
                    new Axis(0.33167, 1.0,     0.89980, 3.10430, 3.23530), 1.00148),
            SurfaceResponse.ByCategory.NEUTRAL, ThermalSpec.DEFAULT, DEFAULT_ROLLING_RESISTANCE);

    public static final TireSpec MOTORCYCLE = new TireSpec(
            1.0,
            SLICK_LOAD_FACTOR_LIGHT, SLICK_LOAD_FACTOR_HEAVY, SLICK_LOAD_FALLOFF,
            tmeasyParams(new Axis(0.11, 1.05, 0.72, 4.2, 2.5),
                    new Axis(0.11, 1.0,  0.78, 5.0, 2.6), 1.10),
            SurfaceResponse.ByCategory.NEUTRAL, ThermalSpec.DEFAULT, DEFAULT_ROLLING_RESISTANCE);

    public static final TireSpec F1_SLICK_FRONT = f1Slick(1.7);
    public static final TireSpec F1_SLICK_REAR = f1Slick(1.9);

    private static TireSpec f1Slick(double grip) {
        return new TireSpec(
                grip,
                SLICK_LOAD_FACTOR_LIGHT, SLICK_LOAD_FACTOR_HEAVY, SLICK_LOAD_FALLOFF,
                tmeasyParams(new Axis(0.13913, 1.12820, 0.70979, 4.79171, 2.26389),
                        new Axis(0.14852, 1.0, 0.83285, 6.49906, 2.37160), 1.2459),
                SurfaceResponse.ByCategory.NEUTRAL, ThermalSpec.DEFAULT, DEFAULT_ROLLING_RESISTANCE);
    }

    public double latPeakAtLoad(double q) {
        ModelParams.TMeasy t = tmeasy();
        return t.lateral().slipAtPeak()
                * (1.0 + (t.slipAtPeakLatLoadRise() - 1.0) * (Mth.clamp(q, 0.0, 3.5) - 1.0));
    }
}
