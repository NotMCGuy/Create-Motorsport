package com.createmotorsport.physics.control;

import net.minecraft.util.Mth;

public final class Abs {


    public static final double DEFAULT_FLOOR = 0.05;

    private Abs() {
    }

    public static final class State {
        public double factor = 1.0;
        private double prevSlip = Double.NaN;

        public void reset() {
            factor = 1.0;
            prevSlip = Double.NaN;
        }
    }


    public static double lockSlip(double omega, double radius, double vLon) {
        return -(omega * radius - vLon) * Math.signum(vLon) / Math.max(Math.abs(vLon), 2.0);
    }

    public static double holdOmega(double radius, double vLon, double threshold) {
        double v = Math.abs(vLon);
        return Math.max(0.0, (v - threshold * Math.max(v, 2.0)) / radius);
    }

    public static double step(State s, double omega, double radius, double vLon,
                              double threshold, double dumpRate, double buildRate, double floor, double dt) {
        double slip = lockSlip(omega, radius, vLon);
        double slipRate = Double.isFinite(s.prevSlip) && dt > 0.0 ? (slip - s.prevSlip) / dt : 0.0;
        s.prevSlip = slip;
        double ahead = slip + slipRate / Math.max(dumpRate, 1.0e-6);
        double error = Mth.clamp((threshold - ahead) / threshold, -1.0, 1.0);
        double rate = error < 0.0 ? dumpRate : buildRate;
        s.factor = Mth.clamp(s.factor + rate * error * dt, floor, 1.0);
        return s.factor;
    }
}
