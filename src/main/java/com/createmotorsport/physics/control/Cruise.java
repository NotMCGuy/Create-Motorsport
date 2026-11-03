package com.createmotorsport.physics.control;

import net.minecraft.util.Mth;

public final class Cruise {


    private static final double P = 1.92;
    private static final double I = 1.44;

    private static final double MIN_AUTHORITY = 0.5;

    private Cruise() {
    }

    public record Step(double demand, double integral) {
    }


    public static final class Controller {

        private static final double MIN_MS = 2.0;
        private static final double DROP_MS = 1.0;

        private boolean on;
        private double targetMs;
        private double integral;
        private boolean override;

        public boolean on() {
            return on;
        }

        public double targetMs() {
            return targetMs;
        }

        public int targetKmh() {
            return on ? (int) Math.round(targetMs * 3.6) : 0;
        }

        public void off() {
            on = false;
            targetMs = 0.0;
            integral = 0.0;
            override = false;
        }

        public float throttle(boolean toggle, double speedMs, float driverThrottle, float driverBrake,
                              double authority, double dt) {
            if (toggle) {
                if (on) {
                    off();
                } else if (speedMs >= MIN_MS) {
                    off();
                    on = true;
                    targetMs = speedMs;
                    integral = Mth.clamp(driverThrottle, 0.0F, 1.0F);
                }
            }
            if (!on) {
                return driverThrottle;
            }
            if (driverBrake > 0.02F || speedMs < DROP_MS) {
                off();
                return driverThrottle;
            }
            Step step = step(integral, targetMs, speedMs, authority, dt);
            double demand = step.demand();

            // a little different than regular cruise control because I'm having the drivers accelerating set a higher speed
            if (driverThrottle > demand) {
                override = true;
                return driverThrottle;
            }
            if (override) {
                override = false;
                targetMs = speedMs;
                return (float) demand;
            }

            integral = step.integral();
            return (float) demand;
        }
    }

    public static Step step(double integral, double targetMs, double speedMs, double authority, double dt) {
        double error = targetMs - speedMs;
        double perAuthority = 1.0 / Math.max(authority, MIN_AUTHORITY);
        double raw = P * perAuthority * error + integral;
        double demand = Mth.clamp(raw, 0.0, 1.0);

        boolean windingUp = raw > 1.0 && error > 0.0;
        boolean windingDown = raw < 0.0 && error < 0.0;
        double next = windingUp || windingDown
                ? integral
                : Mth.clamp(integral + I * perAuthority * error * dt, 0.0, 1.0);
        return new Step(demand, next);
    }
}
