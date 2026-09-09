package com.createmotorsport.trailer;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrailerConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue CAPTURE = BUILDER.defineInRange("captureDistance", 0.28, 0.05, 1.0);
    public static final ModConfigSpec.DoubleValue ALIGNMENT = BUILDER.defineInRange("alignmentDegrees", 20.0, 1, 60);
    public static final ModConfigSpec.DoubleValue CAPTURE_SPEED = BUILDER.defineInRange("captureSpeed", 0.75, 0.05, 5);
    public static final ModConfigSpec.DoubleValue SAFE_SPEED = BUILDER.defineInRange("safeReleaseSpeed", 0.3, 0.01, 3);
    public static final ModConfigSpec.DoubleValue FIFTH_YAW = BUILDER.defineInRange("fifthWheelYawDegrees", 175.0, 30, 179);
    public static final ModConfigSpec.DoubleValue FIFTH_PITCH = BUILDER.defineInRange("fifthWheelPitchDegrees", 20.0, 5, 60);
    public static final ModConfigSpec.DoubleValue FIFTH_ROLL = BUILDER.defineInRange("fifthWheelRollDegrees", 8.0, 1, 30);
    public static final ModConfigSpec.DoubleValue BALL_YAW = BUILDER.defineInRange("towBallYawDegrees", 120.0, 30, 179);
    public static final ModConfigSpec.DoubleValue BALL_PITCH = BUILDER.defineInRange("towBallPitchDegrees", 40.0, 5, 80);
    public static final ModConfigSpec.DoubleValue BALL_ROLL = BUILDER.defineInRange("towBallRollDegrees", 35.0, 5, 80);
    public static final ModConfigSpec.IntValue COOLDOWN = BUILDER.defineInRange("releaseCooldownTicks", 60, 10, 1200);
    public static final ModConfigSpec.BooleanValue REMOTE_RELEASE = BUILDER.define("allowRemoteRelease", false);
    public static final ModConfigSpec.BooleanValue UNSAFE_RELEASE = BUILDER.define("allowUnsupportedRelease", false);
    public static final ModConfigSpec.BooleanValue UNSAFE_RETRACT = BUILDER.define("allowUncoupledRetraction", false);
    public static final ModConfigSpec.BooleanValue BALL_AUTO = BUILDER.define("towBallAutomaticCapture", false);
    public static final ModConfigSpec.BooleanValue SHARED = BUILDER.define("allowDifferentOwnersToCouple", false);
    public static final ModConfigSpec.BooleanValue UNBREAKABLE = BUILDER.define("unbreakableJoints", true);
    public static final ModConfigSpec.DoubleValue BREAK_FORCE = BUILDER.defineInRange("breakForceNewtons", 500000.0, 1000, 100000000);
    public static final ModConfigSpec.DoubleValue BREAK_TORQUE = BUILDER.defineInRange("breakTorqueNewtonMetres", 250000.0, 1000, 100000000);
    public static final ModConfigSpec.IntValue LEG_TICKS = BUILDER.defineInRange("landingLegDeploymentTicks", 100, 20, 1200);
    public static final ModConfigSpec.DoubleValue LEG_REACH = BUILDER.defineInRange("landingLegReach", 2.5, 0.5, 5);
    public static final ModConfigSpec.DoubleValue LEG_HZ = BUILDER.defineInRange("landingLegSpringFrequency", 2.0, 0.5, 4);
    public static final ModConfigSpec.DoubleValue LEG_DAMPING = BUILDER.defineInRange("landingLegDampingRatio", 0.9, 0.2, 2);
    public static final ModConfigSpec.DoubleValue LINK_RANGE = BUILDER.defineInRange("equipmentLinkRange", 16.0, 4, 32);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private TrailerConfig() {}
}
