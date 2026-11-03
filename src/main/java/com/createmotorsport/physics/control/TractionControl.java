package com.createmotorsport.physics.control;

import net.minecraft.util.Mth;

/**
 * Traction Control that works much better now since its stepped every physics substep within the driven axle itself
 * Rill, Vehicle Dynamics eq 8.40
 */
public final class TractionControl {

    public static final double SPEED_FLOOR = 2.0;

    private static final double SIDE_SLIP = 4.0; // m/s sideways slide before it cuts for oversteering
    private static final double SIDE_RANGE = 8.0; // m/s past that over which that cutting goes to the floor
    private static final double SIDE_FLOOR = 0.5; // the most the throttle can be trimmed to by lateral cut

    private TractionControl() {
    }

    public record Result(double factor, double applied, double slipRatio, double slipTarget,
                         double capacityCap) {

        public static final Result OFF = off(0.0);

        public static Result off(double slipTarget) {
            return new Result(1.0, 1.0, 0.0, slipTarget, 1.0);
        }
    }


    // recalcs the capacity cap to the demand about to be applied
    public static Result recap(Result result, double tractionForce, double repRadius, double demandTorque) {
        if (tractionForce <= 0.0 || demandTorque <= 1.0e-6) {
            return result;
        }
        double cap = Mth.clamp(tractionForce * repRadius / demandTorque, 0.0, 1.0);
        if (cap >= result.applied()) {
            return result;
        }
        return new Result(result.factor(), cap, result.slipRatio(), result.slipTarget(),
                Math.min(cap, result.capacityCap()));
    }

    public static Result step(double factor, double prevSlip, double wheelSurfaceSpeed, double groundSpeed,
                              double sideSlip, double latUse, double targetSlip, double cutRate, double buildRate,
                              double minThrottle, double tractionForce, double repRadius,
                              double demandTorque, double dt) {
        double slipRatio = (wheelSurfaceSpeed - groundSpeed) / Math.max(groundSpeed, SPEED_FLOOR);
        double use = Mth.clamp(latUse, 0.0, 1.0);
        double slipTarget = targetSlip * Math.sqrt(Math.max(0.0, 1.0 - use * use));

        double slipRate = dt > 0.0 ? (slipRatio - prevSlip) / dt : 0.0;
        double ahead = slipRatio + slipRate / Math.max(cutRate, 1.0e-6);
        double error = Mth.clamp((slipTarget - ahead) / Math.max(slipTarget, 1.0e-6), -1.0, 1.0);
        double rate = error < 0.0 ? cutRate : buildRate;
        double next = Mth.clamp(factor + rate * error * dt, minThrottle, 1.0);

        double capacityCap = 1.0;
        if (tractionForce > 0.0 && demandTorque > 1.0e-6) {
            capacityCap = Mth.clamp(tractionForce * repRadius / demandTorque, 0.0, 1.0);
        }
        double applied = Math.min(next, capacityCap);
        if (sideSlip > SIDE_SLIP) {
            applied *= Mth.clamp(1.0 - (sideSlip - SIDE_SLIP) / SIDE_RANGE, SIDE_FLOOR, 1.0);
        }
        return new Result(next, applied, slipRatio, slipTarget, capacityCap);
    }
}
