package com.createmotorsport.physics.tire;

import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.Config;
import net.minecraft.util.Mth;

public final class TireModel {

    private static final double REST_OMEGA = 0.05;

    private TireModel() {
    }

    public static double slipCurve(double slip, double b, double c, double e) {
        double bx = b * slip;
        return Math.sin(c * Math.atan(bx * (1.0 - e) + e * Math.atan(bx)));
    }

    public static double loadSensitivity(double load, double designLoad, TireSpec spec) {
        if (designLoad <= 0.0) {
            return 1.0;
        }
        double x = load / designLoad;
        return spec.loadFactorHeavy() + (spec.loadFactorLight() - spec.loadFactorHeavy()) * Math.exp(spec.loadFalloff() * x);
    }



    public static double longitudinalForce(double normalForce, double surfaceMu, double wheelSpeed, double groundSpeed, TireSpec spec) {
        double slipRatio = (wheelSpeed - groundSpeed) / Math.max(Math.abs(groundSpeed), 2.0);
        return normalForce * surfaceMu * spec.grip() * slipCurve(slipRatio, spec.pacejkaB(), spec.pacejkaC(), spec.pacejkaE());
    }

    private static final double ROLL_RESIST_SMOOTH = 0.5;

    public static double rollingResistance(double coefficient, double normalForce, double groundSpeed) {
        return coefficient * normalForce
                * Mth.clamp(groundSpeed / ROLL_RESIST_SMOOTH, -1.0, 1.0);
    }

    public static double frictionEllipseScale(double forwardImpulse, double sideImpulse, double maxImpulse) {
        double x = forwardImpulse * Config.FRICTION_ELLIPSE_LONG_WEIGHT.getAsDouble();
        double y = sideImpulse;
        double lenSq = x * x + y * y;
        double maxSq = maxImpulse * maxImpulse;
        if (lenSq <= maxSq || lenSq < 1.0e-12) {
            return 1.0;
        }
        return maxImpulse / Math.sqrt(lenSq);
    }

    public static double spinCoupling(double tireForce, double radius, double omega, double groundSpeed) {
        double slipOmega = omega - groundSpeed / radius;
        if (Math.abs(slipOmega) <= 1.0e-3) {
            return 0.0;
        }
        return Math.max(0.0, tireForce * radius / slipOmega);
    }

    public static double integrateSpin(double omega, double radius, double inertia, double driveTorque,
                                       double brakeTorque, double tireForce, double groundSpeed, double dt) {
        return integrateSpin(omega, radius, inertia, driveTorque, 0.0, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, brakeTorque, tireForce, groundSpeed, dt);
    }

    public static double integrateSpin(double omega, double radius, double inertia, double driveTorque,
                                       double driveStiffness, double driveMin, double driveMax,
                                       double brakeTorque, double tireForce, double groundSpeed, double dt) {
        return integrateCoupled(omega, radius, inertia, driveTorque, driveStiffness, driveMin, driveMax,
                brakeTorque, 0.0, spinCoupling(tireForce, radius, omega, groundSpeed), groundSpeed, dt);
    }

    public static double drive(double driveTorque, double driveStiffness, double driveMin, double driveMax,
                               double omega, double newOmega) {
        return Mth.clamp(driveTorque - driveStiffness * (newOmega - omega), driveMin, driveMax);
    }

    @FunctionalInterface
    public interface LongForce {
        double at(double omega);
    }

    private static final int SPIN_PASSES = 8;

    public static double stepSpin(double omega, double radius, double inertia, double driveTorque,
                                  double driveStiffness, double driveMin, double driveMax, double brakeTorque,
                                  double absOmega, double groundSpeed, double dt, LongForce curve) {
        double next = integrateCoupled(omega, radius, inertia, driveTorque, driveStiffness, driveMin, driveMax,
                brakeTorque, absOmega, spinCoupling(curve.at(omega), radius, omega, groundSpeed), groundSpeed, dt);
        for (int pass = 1; pass < SPIN_PASSES && next != 0.0; pass++) {
            double k = spinCoupling(curve.at(next), radius, next, groundSpeed);
            if (k <= 0.0) {
                break;
            }
            double again = integrateCoupled(omega, radius, inertia, driveTorque, driveStiffness, driveMin,
                    driveMax, brakeTorque, absOmega, k, groundSpeed, dt);
            boolean settled = Math.abs(again - next) < 1.0e-4;
            next = again;
            if (settled) {
                break;
            }
        }
        return next;
    }

    private static double integrateCoupled(double omega, double radius, double inertia, double driveTorque,
                                           double driveStiffness, double driveMin, double driveMax,
                                           double brakeTorque, double absOmega, double k, double groundSpeed,
                                           double dt) {
        double brake = -Math.signum(omega) * brakeTorque;
        double rollingOmega = groundSpeed / radius;
        double slipOmega = omega - rollingOmega;
        if (brakeTorque > 0.0 && Math.abs(omega) < REST_OMEGA) {
            double turning = Mth.clamp(driveTorque, driveMin, driveMax) - k * slipOmega;
            if (Math.abs(turning) <= brakeTorque) {
                return 0.0;
            }
            if (omega == 0.0) {
                brake = -Math.signum(turning) * brakeTorque;
            }
        }

        double d = driveTorque;
        double c = driveStiffness;
        double newOmega = (omega + dt / inertia * (d + c * omega + brake + k * rollingOmega))
                / (1.0 + dt * (k + c) / inertia);
        double passed = d - c * (newOmega - omega);
        if (passed > driveMax || passed < driveMin) {
            d = Mth.clamp(passed, driveMin, driveMax);
            c = 0.0;
            newOmega = (omega + dt / inertia * (d + brake + k * rollingOmega)) / (1.0 + dt * k / inertia);
        }

        double dir = Math.signum(groundSpeed);
        if (absOmega > 0.0 && brakeTorque > 0.0 && dir != 0.0 && dir * newOmega < absOmega) {
            double target = dir * absOmega;
            double held = (target * (1.0 + dt * (k + c) / inertia) - omega) * inertia / dt
                    - d - c * omega - k * rollingOmega;
            brake = -dir * Mth.clamp(-dir * held, 0.0, brakeTorque);
            newOmega = (omega + dt / inertia * (d + c * omega + brake + k * rollingOmega))
                    / (1.0 + dt * (k + c) / inertia);
            passed = d - c * (newOmega - omega);
            if (passed > driveMax || passed < driveMin) {
                d = Mth.clamp(passed, driveMin, driveMax);
                c = 0.0;
                newOmega = (omega + dt / inertia * (d + brake + k * rollingOmega)) / (1.0 + dt * k / inertia);
            }
        }

        if (brakeTorque > 0.0 && omega * newOmega < 0.0) {
            double noBrake = (omega + dt / inertia * (d + c * omega + k * rollingOmega))
                    / (1.0 + dt * (k + c) / inertia);
            if (omega * noBrake >= 0.0) {
                newOmega = 0.0;
            }
        }

        return Mth.clamp(newOmega, -400.0, 400.0);
    }
}
