package com.createmotorsport.physics.spec;

/**
 * Torque ramp into an axle with rate limit and optional lag. every substep by AxleDrive
 * Modeled after an engaging clutch like Chrono's hang_on_clutch
 */
public record DriveInputSpec(
        double engageRateNmPerS, // rate limit for torque change, 0 is instant
        double lagSeconds // seconds of first-order lag
) {

    public static final DriveInputSpec INSTANT = new DriveInputSpec(0.0, 0.0);

    public boolean isInstant() {
        return engageRateNmPerS <= 0.0 && lagSeconds <= 0.0;
    }

    public double step(double current, double target, double dt) {
        double limited = target;
        if (engageRateNmPerS > 0.0 && dt > 0.0) {
            double maxStep = engageRateNmPerS * dt;
            double delta = target - current;
            limited = current + Math.max(-maxStep, Math.min(maxStep, delta));
        }
        if (lagSeconds > 0.0 && dt > 0.0) {
            double alpha = 1.0 - Math.exp(-dt / lagSeconds);
            limited = current + (limited - current) * alpha;
        }
        return limited;
    }
}
