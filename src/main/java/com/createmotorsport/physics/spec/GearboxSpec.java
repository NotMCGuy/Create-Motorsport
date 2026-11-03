package com.createmotorsport.physics.spec;

import java.util.List;
import java.util.Optional;

public record GearboxSpec(Type type, List<Double> forwardRatios, double reverseRatio,
                          Optional<Double> finalDrive, boolean semiAuto, double shiftInterruptTime,
                          boolean revMatch, ClutchSpec clutch,
                          // lives here until a centre diff has its own block
                          TorqueSplitSpec centerDiff,
                          // a tandem's power divider, same as centerDiff
                          TorqueSplitSpec interAxleDiff) {

    // a torque converter and CVT will be added later too
    public enum Type {
        SEQUENTIAL("sequential");

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

    public GearboxSpec {
        if (forwardRatios == null || forwardRatios.isEmpty()) {
            throw new IllegalArgumentException("Gearbox has no forward gears");
        }
        for (double r : forwardRatios) {
            if (!(r > 0.0)) {
                throw new IllegalArgumentException("Gear ratios must be positive, got " + r);
            }
        }
        forwardRatios = List.copyOf(forwardRatios);
    }

    public static final GearboxSpec F1_SEAMLESS_7 = new GearboxSpec(
            Type.SEQUENTIAL, List.of(3.2, 2.6, 2.0, 1.6, 1.3, 1.0, 0.85), 3.2, Optional.empty(),
            true, 0.0, true, ClutchSpec.F1_AUTO, TorqueSplitSpec.DEFAULT, TorqueSplitSpec.OPEN);

    public static final GearboxSpec ROAD_MANUAL_5 = new GearboxSpec(
            Type.SEQUENTIAL, List.of(3.55, 1.95, 1.28, 0.95, 0.76), 3.40, Optional.of(11.0),
            false, 0.0, false, ClutchSpec.ROAD, TorqueSplitSpec.DEFAULT, TorqueSplitSpec.OPEN);

    public static final GearboxSpec PASSENGER_DCT_6 = new GearboxSpec(
            Type.SEQUENTIAL, List.of(3.77, 2.09, 1.47, 1.10, 0.87, 0.72), 3.99, Optional.empty(),
            true, 0.05, true, ClutchSpec.PASSENGER_DCT, TorqueSplitSpec.DEFAULT, TorqueSplitSpec.OPEN);

    public static final GearboxSpec TRUCK_MANUAL_10 = new GearboxSpec(
            Type.SEQUENTIAL,
            List.of(12.9, 9.2, 6.7, 4.9, 3.6, 2.6, 1.9, 1.4, 1.0, 0.73), 12.0, Optional.empty(),
            false, 0.30, false, ClutchSpec.TRUCK, TorqueSplitSpec.DEFAULT, TorqueSplitSpec.OPEN);

    public static final GearboxSpec MOTORCYCLE_QUICKSHIFT_6 = new GearboxSpec(
            Type.SEQUENTIAL, List.of(2.600, 2.176, 1.842, 1.579, 1.381, 1.250), 2.600, Optional.empty(),
            true, 0.05, true, ClutchSpec.MOTORCYCLE, TorqueSplitSpec.DEFAULT, TorqueSplitSpec.OPEN);

    public int gearCount() {
        return this.forwardRatios.size();
    }

    public double finalDriveOr(double engineFinalDrive) {
        return this.finalDrive.orElse(engineFinalDrive);
    }

    public double forwardRatio(int i) {
        return this.forwardRatios.get(Math.max(0, Math.min(this.forwardRatios.size() - 1, i)));
    }

}
