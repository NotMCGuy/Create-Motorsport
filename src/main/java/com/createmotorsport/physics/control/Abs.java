package com.createmotorsport.physics.control;

import net.minecraft.util.Mth;

/**
 * ABS per corner, called by Corner.java every substep when ABS is on and car's in motion.
 * Returns the fraction of brake torque to apply
 * Rill, Vehicle Dynamics 8.3.6.
 */
public final class Abs {


    public static final double DEFAULT_FLOOR = 0.05; // underloaded wheel and strong brake will still lock, but the floor is needed. Will address later

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


    // How locked the wheel is, 0 rolling, 1 locked, negative under power
    public static double lockSlip(double omega, double radius, double vLon) {
        return -(omega * radius - vLon) * Math.signum(vLon) / Math.max(Math.abs(vLon), 2.0);
    }

    public static double holdOmega(double radius, double vLon, double threshold) {
        double v = Math.abs(vLon);
        return Math.max(0.0, (v - threshold * Math.max(v, 2.0)) / radius);
    }

    /**
     * Maybe I was missing something but Rills eq 8.39-8.40 were saying to have the dump be slower than the build,
     * but the opposite is normally true for ABS, and dump being faster is what works better here
     *
     * @param threshold is the peak slip its trying to maintain. Error gets scaled by this so itll probably work for other tire models
     * @return the fraction to multiply brake torque
     */
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
