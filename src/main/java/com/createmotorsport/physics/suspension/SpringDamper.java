package com.createmotorsport.physics.suspension;

import com.createmotorsport.physics.spec.DamperSpec;

/**
 * Spring and damper maths for one corner, used by UnsprungSolver and Corner
 */
public final class SpringDamper {

    private SpringDamper() {
    }

    /** Suspension force for one corner (N)
     * @param rateMass      sprung mass this corner holds up (kg), sets the spring rate
     * @param naturalFreqHz ~1.5 Hz road car, ~3.5 Hz race car
     * @param dampingRatio  0.2 = boat, 0.7 = sporty, 1.0 = no overshooting
     * @param compression   rest length minus current spring length (m), positive compressed
     * @param relVelocity   hardpoint velocity along the suspension axis (m/s), positive extending
     * @param damper        force-velocity curve, DamperSpec.LINEAR for no knee
     * @param responseMass  mass the body shows to a force here, Sable's normal mass
     * @param dt            substep length (s)
     */
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

    // Damper curve first, then the timestep correction
    public static double effectiveDamping(double c, double relVelocity, DamperSpec damper,
                                          double dt, double responseMass) {
        return implicitDamping(digressiveDamping(c, relVelocity, damper), dt, responseMass);
    }

    // See DamperSpec for the curve
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

    // Suspension spring rate in N/m
    public static double springRate(double effectiveMass, double naturalFreqHz) {
        double omega0 = 2.0 * Math.PI * naturalFreqHz;
        return effectiveMass * omega0 * omega0;
    }

    // Suspension damping coefficient in N*s/m
    public static double springDamping(double effectiveMass, double naturalFreqHz, double dampingRatio,
                                       boolean rebound) {
        double zeta = rebound ? dampingRatio * 1.15 : dampingRatio;
        return 2.0 * zeta * effectiveMass * 2.0 * Math.PI * naturalFreqHz;
    }

    /** One step of the unsprung mass. Backwards euler fixes all our Sable substep problems
     * @param unsprungMass  wheel mass (kg)
     * @param dt            substep (s)
     * @param unsprungVel   current wheel vertical velocity (m/s, positive up)
     * @param hardpointVel  body vertical velocity at the hardpoint (m/s, positive up)
     * @param springRate    suspension rate (N/m)
     * @param springDamp    suspension damping (N.s/m)
     * @param compression   current suspension compression (m, positive)
     * @param tireRate      tyre vertical rate (N/m)
     * @param tireDamp      tyre damping (N.s/m)
     * @param tireDeflect   current tyre squash (m, positive, zero when off the ground)
     * @return the new wheel vertical velocity (m/s, positive up)
     */
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
