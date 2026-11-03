package com.createmotorsport.physics.spec;

/**
 * Differential l/r split for an axle
 * Front/rear split is TorqueSplitSpec
 */
public record DifferentialSpec(DiffParams params, DriveInputSpec input) {

    public enum Type {
        OPEN("open"),
        CLUTCH_LSD("clutch_lsd"),
        LOCKED("locked");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Type byId(String id) {
            for (Type t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            return null;
        }
    }

    public Type type() {
        return this.params.type();
    }

    public static final DifferentialSpec DEFAULT = new DifferentialSpec(
            new DiffParams.ClutchLsd(200.0, 40.0, 0.0, 0.0), DriveInputSpec.INSTANT);

    public static final DifferentialSpec OPEN = new DifferentialSpec(
            new DiffParams.Open(), DriveInputSpec.INSTANT);

    public static final DifferentialSpec SPOOL = new DifferentialSpec(
            new DiffParams.Locked(), DriveInputSpec.INSTANT);

    public static final DifferentialSpec TRUCK_TANDEM = new DifferentialSpec(
            new DiffParams.ClutchLsd(600.0, 120.0, 0.0, 0.0), new DriveInputSpec(900.0, 0.0));

    public <T extends DiffParams> T params(Class<T> type) {
        if (!type.isInstance(this.params)) {
            throw new IllegalStateException(
                    "differential is " + type() + ", not " + type.getSimpleName());
        }
        return type.cast(this.params);
    }
}
