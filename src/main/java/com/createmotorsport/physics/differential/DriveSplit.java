package com.createmotorsport.physics.differential;

import net.minecraft.util.Mth;

import java.util.List;

public final class DriveSplit {

    private DriveSplit() {
    }

    public static final class Wheel {
        public double omega;
        public double from;
        public double inertia;
        public double coupling = Double.NaN;
        public double own;
        public double torque;
        public double stiffness;
        public double min;
        public double max;
        public double drive;
        public double change;

        private double inv;
    }

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
