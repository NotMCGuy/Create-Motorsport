package com.createmotorsport.physics.spec;

public record DriveInputSpec(
        double engageRateNmPerS,
        double lagSeconds
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
