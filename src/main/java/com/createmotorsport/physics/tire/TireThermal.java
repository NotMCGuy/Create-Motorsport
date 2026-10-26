package com.createmotorsport.physics.tire;

import net.minecraft.util.Mth;

public final class TireThermal {

    private static final double MIN_GRIP_FACTOR = 0.1;

    public static final double SPEED_COOL = 0.25;

    private TireThermal() {
    }

    public static double startTemp(double ambientC) {
        return ambientC;
    }

    public static double gripFactor(double tempC, double optimalC, double ambientC, double coldGrip) {
        double span = optimalC - ambientC;
        double k = span != 0.0 ? (1.0 - coldGrip) / (span * span) : 0.0;
        double dT = tempC - optimalC;
        return Mth.clamp(1.0 - k * dT * dT, MIN_GRIP_FACTOR, 1.0);
    }

    public static double frictionWork(double longForce, double latForce,
                                      double slipVelLon, double vLat, double dt) {
        return (Math.abs(longForce * slipVelLon) + Math.abs(latForce * vLat)) * dt;
    }


    public static double step(double tempC, double frictionWorkJ, double speedMs, double ambientC,
                              double heatingRate, double coolingRate, double dt) {
        double heated = tempC + frictionWorkJ * heatingRate;
        return heated - coolingRate * (1.0 + SPEED_COOL * Math.abs(speedMs)) * (heated - ambientC) * dt;
    }
}
