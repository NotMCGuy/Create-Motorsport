package com.createmotorsport.physics.suspension;

import com.createmotorsport.physics.spec.DamperSpec;

public final class SpringDamper {

    private SpringDamper() {
    }

    public static double suspensionForce(double rateMass, double naturalFreqHz, double dampingRatio,
                                         double compression, double relVelocity, DamperSpec damper,
                                         double responseMass, double dt) {
        double omega0 = 2.0 * Math.PI * naturalFreqHz;
        double k = rateMass * omega0 * omega0;
        double zeta = relVelocity > 0.0 ? dampingRatio * 1.15 : dampingRatio;
        double c = 2.0 * zeta * Math.sqrt(k * rateMass);
        double force = k * compression
                - effectiveDamping(c, relVelocity, damper, dt, responseMass) * relVelocity;
        return Math.max(0.0, force);
    }

    public static double effectiveDamping(double c, double relVelocity, DamperSpec damper,
                                          double dt, double responseMass) {
        return implicitDamping(digressiveDamping(c, relVelocity, damper), dt, responseMass);
    }

    public static double digressiveDamping(double c, double relVelocity, DamperSpec damper) {
        if (damper == null) {
            return c;
        }
        double v = Math.abs(relVelocity);
        double knee = damper.kneeVelocity();
        if (knee <= 0.0 || v <= knee || c <= 0.0) {
            return c;
        }
        double force = c * knee + c * damper.blowOffSlope() * (v - knee);
        return force / v;
    }

    public static double implicitDamping(double c, double dt, double responseMass) {
        if (responseMass <= 1.0e-9 || dt <= 0.0 || c <= 0.0) {
            return c;
        }
        double r = c * dt / responseMass;
        if (r < 1.0e-4) {
            return c;
        }
        return responseMass / dt * (1.0 - Math.exp(-r));
    }

    public static double springRate(double effectiveMass, double naturalFreqHz) {
        double omega0 = 2.0 * Math.PI * naturalFreqHz;
        return effectiveMass * omega0 * omega0;
    }

    public static double springDamping(double effectiveMass, double naturalFreqHz, double dampingRatio,
                                       boolean rebound) {
        double zeta = rebound ? dampingRatio * 1.15 : dampingRatio;
        return 2.0 * zeta * effectiveMass * 2.0 * Math.PI * naturalFreqHz;
    }

    public static double solveUnsprung(double unsprungMass, double dt,
                                       double unsprungVel, double hardpointVel,
                                       double springRate, double springDamp, double compression,
                                       double tireRate, double tireDamp, double tireDeflect,
                                       double gravity) {
        double denom = unsprungMass / dt + tireRate * dt + tireDamp + springRate * dt + springDamp;
        double rhs = unsprungMass * unsprungVel / dt
                + tireRate * tireDeflect
                - springRate * compression
                + (springRate * dt + springDamp) * hardpointVel
                - unsprungMass * gravity;
        return rhs / denom;
    }
}
