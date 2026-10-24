package com.createmotorsport.physics.spec;

import java.util.Optional;

public record SuspensionSpec(Type type, Optional<Boolean> driven, double naturalFreqHz, double dampingRatio,
                             double rideHeight, double trackWidth, double wheelOffset,
                             double maxSteerDegrees, double ackermann,
                             double camberDeg, double camberStiffness,
                             double steerFullLockSpeed, double rollInfluence,
                             double maxRideHeight, SuspensionArt art,
                             BrakeSpec brakes, DamperSpec damper, TerrainAssistSpec terrainAssist) {

    public boolean drivenOr(boolean fallback) {
        return this.driven.orElse(fallback);
    }

    public SuspensionSpec withDriven(boolean driven) {
        return new SuspensionSpec(type, Optional.of(driven), naturalFreqHz, dampingRatio, rideHeight, trackWidth,
                wheelOffset, maxSteerDegrees, ackermann, camberDeg, camberStiffness, steerFullLockSpeed,
                rollInfluence, maxRideHeight, art, brakes, damper, terrainAssist);
    }

    public static final double DEFAULT_MAX_STEER_DEGREES = 26.0;
    public static final double DEFAULT_CAMBER_DEG = -3.0;
    public static final double DEFAULT_CAMBER_STIFFNESS = 5.3227;
    public static final double DEFAULT_STEER_FULL_LOCK_SPEED = 60.0;
    public static final double DEFAULT_ROLL_INFLUENCE = 0.2;
    public static final double DEFAULT_MAX_RIDE_HEIGHT = 1.2;

    public double wheelSteerK(double trackOverWheelbase) {
        return this.ackermann * trackOverWheelbase;
    }

    public enum Type {
        VERTICAL_STRUT("vertical_strut");

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

    public static final double DEFAULT_RIDE_HEIGHT = 0.0;
    public static final double DEFAULT_TRACK_WIDTH = 3.125;
    public static final double DEFAULT_WHEEL_OFFSET = 0.0;
    public static final double MAX_WHEEL_OFFSET = 0.5;

    public static final SuspensionSpec SOFT =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.empty(),
                    1.37, 0.35, DEFAULT_RIDE_HEIGHT, DEFAULT_TRACK_WIDTH, DEFAULT_WHEEL_OFFSET,
                    DEFAULT_MAX_STEER_DEGREES, 0.0, DEFAULT_CAMBER_DEG, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.F1_DIGRESSIVE, TerrainAssistSpec.DEFAULT);
    public static final SuspensionSpec MEDIUM =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.empty(),
                    2.18, 0.55, DEFAULT_RIDE_HEIGHT, DEFAULT_TRACK_WIDTH, DEFAULT_WHEEL_OFFSET,
                    DEFAULT_MAX_STEER_DEGREES, 0.0, DEFAULT_CAMBER_DEG, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.F1_DIGRESSIVE, TerrainAssistSpec.DEFAULT);
    public static final SuspensionSpec FIRM =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.empty(),
                    4.9, 0.70, DEFAULT_RIDE_HEIGHT, DEFAULT_TRACK_WIDTH, DEFAULT_WHEEL_OFFSET,
                    DEFAULT_MAX_STEER_DEGREES, 0.0, DEFAULT_CAMBER_DEG, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.F1_DIGRESSIVE, TerrainAssistSpec.DEFAULT);
    public static final SuspensionSpec RACE =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.of(false),
                    4.6, 0.70, DEFAULT_RIDE_HEIGHT, DEFAULT_TRACK_WIDTH, DEFAULT_WHEEL_OFFSET,
                    DEFAULT_MAX_STEER_DEGREES, 0.96, DEFAULT_CAMBER_DEG, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.F1_DIGRESSIVE, TerrainAssistSpec.DEFAULT);
    // only differs in camber
    public static final SuspensionSpec RACE_REAR =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.of(true),
                    4.6, 0.70, DEFAULT_RIDE_HEIGHT, DEFAULT_TRACK_WIDTH, DEFAULT_WHEEL_OFFSET,
                    DEFAULT_MAX_STEER_DEGREES, 0.96, -1.5, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.F1_DIGRESSIVE, TerrainAssistSpec.DEFAULT);
    public static final SuspensionSpec PASSENGER_STRUT =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.empty(),
                    1.8, 0.30, 0.20, 3.0, DEFAULT_WHEEL_OFFSET,
                    31.0, -0.80, -1.0, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.ROAD, TerrainAssistSpec.DEFAULT);
    public static final SuspensionSpec TRUCK_STRUT =
            new SuspensionSpec(Type.VERTICAL_STRUT, Optional.empty(),
                    2.08, 0.25, 0.40, 4.0, DEFAULT_WHEEL_OFFSET,
                    42.0, -1.00, -0.5, DEFAULT_CAMBER_STIFFNESS,
                    DEFAULT_STEER_FULL_LOCK_SPEED, DEFAULT_ROLL_INFLUENCE,
                    DEFAULT_MAX_RIDE_HEIGHT, SuspensionArt.DEFAULT,
                    BrakeSpec.DEFAULT, DamperSpec.TRUCK, TerrainAssistSpec.DEFAULT);

    // before the tyre's deflection
    public double staticSagBlocks(double gravity) {
        double omega0 = 2.0 * Math.PI * naturalFreqHz;
        return gravity / (omega0 * omega0);
    }
}
