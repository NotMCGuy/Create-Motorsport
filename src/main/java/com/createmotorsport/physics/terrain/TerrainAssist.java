package com.createmotorsport.physics.terrain;

import com.createmotorsport.physics.spec.TerrainAssistSpec;
import net.minecraft.util.Mth;


public final class TerrainAssist {

    // the log's assist_why column
    public static final int OFF = 0;
    public static final int AIRBORNE = 1;
    public static final int SLOPE_OK = 2;
    public static final int NO_RISE = 3;
    public static final int ACTIVE = 4;

    private static final int MIN_PROBES = 4;
    private static final int MAX_PROBES = 12;
    private static final double PROBE_SPACING = 1.5;
    private static final double RELEASE_RATIO = 3.0; // drops faster

    private TerrainAssist() {
    }

    public enum Level {
        OFF("Off"),
        NORMAL("Normal"),
        EXTRA("Extra");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public Level next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean enabled() {
            return this != OFF;
        }
    }

    // rigid length at distance ahead, or NaN for no ground
    @FunctionalInterface
    public interface Probe {
        double rigidAhead(double distance);
    }

    // everything but lift is telemetry
    public static final class State {
        public double lift;

        public double target;
        public double rise;
        public double probeDistance;
        public int why;
    }

    /**
     * @param airborneAbove rigid length above which the wheel is off the ground
     * @param carSpeed blocks per second
     */
    public static double lift(TerrainAssistSpec spec, boolean enabled, boolean extra, double radius,
                              double rigidHere, double airborneAbove, double carSpeed, double dt,
                              Probe probe, State s) {
        s.target = 0.0;
        s.rise = 0.0;
        s.probeDistance = 0.0;
        if (!spec.enabled() || !enabled) {
            s.why = OFF;
            s.lift = 0.0;
            return 0.0;
        }

        if (rigidHere > airborneAbove) {
            s.why = AIRBORNE;
            return release(spec, dt, s);
        }

        double maxClimb = extra ? Math.max(radius * spec.maxClimbFraction(), spec.extraMaxClimbBlocks())
                : radius * spec.maxClimbFraction();
        double lookAhead = extra ? spec.extraLookAheadBlocks() : spec.lookAheadBlocks();

        double maxSlope = carSpeed > 0.05 ? spec.maxVerticalRate() / carSpeed : Double.MAX_VALUE;
        if (extra) {
            maxSlope = Math.min(maxSlope, spec.extraMaxSlope());
        } else if (!Double.isFinite(maxSlope) || maxSlope >= 1.0) {
            s.why = SLOPE_OK;
            return release(spec, dt, s);
        }

        int probes = Mth.clamp((int) Math.ceil(lookAhead / PROBE_SPACING), MIN_PROBES, MAX_PROBES);
        double best = 0.0;
        for (int i = 1; i <= probes; i++) {
            double d = lookAhead * i / probes;
            double rigidAhead = probe.rigidAhead(d);
            if (Double.isNaN(rigidAhead)) {
                continue;
            }
            double rise = rigidHere - rigidAhead;
            if (rise <= 0.0 || rise > maxClimb) {
                continue;
            }
            double thisLift = rise - maxSlope * d;
            if (thisLift > best) {
                best = thisLift;
                s.rise = rise;
                s.probeDistance = d;
            }
        }
        double target = Math.max(0.0, best);
        s.target = target;
        s.why = target > 0.0 ? ACTIVE : NO_RISE;
        return rateLimit(spec, dt, target, s);
    }

    // moves stored lift toward target no more than maxVerticalRate
    private static double rateLimit(TerrainAssistSpec spec, double dt, double target, State s) {
        double step = spec.maxVerticalRate() * dt;
        s.lift = target > s.lift
                ? Math.min(target, s.lift + step)
                : Math.max(target, s.lift - step * RELEASE_RATIO);
        return s.lift;
    }

    private static double release(TerrainAssistSpec spec, double dt, State s) {
        return rateLimit(spec, dt, 0.0, s);
    }
}
