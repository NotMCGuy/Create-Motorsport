package com.createmotorsport.physics.differential;

import net.minecraft.util.Mth;

import java.util.List;

/**
 * Shares one engine's clutch stiffness across every wheel it drives
 * AssembledCar calls solve() after the driven wheels have stepped
 */
public final class DriveSplit {

    private DriveSplit() {
    }

    // owned by its axle, which keeps change between substeps
    public static final class Wheel {
        public double omega;
        public double from;
        public double inertia;
        // the tyre's hold where the step ended; NaN for a wheel held by the brake, which takes no correction
        public double coupling = Double.NaN;
        // the clutch drive the step took, without the differential's torque
        public double own;
        public double torque;
        public double stiffness;
        public double min;
        public double max;
        public double drive;
        public double change;

        private double inv;
    }

    /** @return the corrected mean change, for the next substep's warm start */
    public static double solve(List<Wheel> wheels, double dt) {
        if (wheels.isEmpty()) {
            return 0.0;
        }
        double a = 1.0 / wheels.size();
        double num = 0.0;
        double den = 1.0;
        for (Wheel w : wheels) {
            w.inv = Double.isNaN(w.coupling) ? 0.0 : 1.0 / (w.inertia / dt + w.coupling);
            num += a * (w.omega - w.from) + a * (w.torque - w.own) * w.inv;
            den += a * w.stiffness * w.inv;
        }
        double mean = num / den;
        for (Wheel w : wheels) {
            w.drive = Mth.clamp(w.torque - w.stiffness * mean, w.min, w.max);
            w.omega += (w.drive - w.own) * w.inv;
        }
        return mean;
    }
}
