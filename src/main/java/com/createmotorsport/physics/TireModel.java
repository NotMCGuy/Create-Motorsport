package com.createmotorsport.physics;

import com.createmotorsport.Config;
import com.createmotorsport.physics.spec.DamperSpec;
import net.minecraft.util.Mth;

// slip-based tire friction
// based on Rapier's DynamicRayCastVehicleController (which came from Bullet's btRaycastVehicle)
// added onto by referring to speed dreams a lot and vdrift
// with a third model from Project Chrono

public final class TireModel {

    private TireModel() {
    }

    // normalize forze response to slip ratio, odd function, |f| <= 1
    public static double slipCurve(double slip, double b, double c, double e) {
        double bx = b * slip;
        return Math.sin(c * Math.atan(bx * (1.0 - e) + e * Math.atan(bx)));
    }

    // better tire design load modeling; the light/heavy/falloff factors come from the tier
    public static double loadSensitivity(double load, double designLoad, TireSpec spec) {
        if (designLoad <= 0.0) {
            return 1.0;
        }
        double x = load / designLoad;
        return spec.loadFactorHeavy() + (spec.loadFactorLight() - spec.loadFactorHeavy()) * Math.exp(spec.loadFalloff() * x);
    }



    /** Longitudinal Force (N)
     * @param normalForce -> current load on the tire from suspension (N)
     * @param surfaceMu -> friction coefficient of the block under the tire, from Sable
     * @param wheelSpeed -> omega * radius, contact patch speed (m/s)
     * @param groundSpeed -> longitudinal velocity of the hub over the ground (m/s)
     * @param spec -> the tire tier (curve shape + base grip)
     */
    public static double longitudinalForce(double normalForce, double surfaceMu, double wheelSpeed, double groundSpeed, TireSpec spec) {
        double slipRatio = (wheelSpeed - groundSpeed) / Math.max(Math.abs(groundSpeed), 2.0);
        return normalForce * surfaceMu * spec.grip() * slipCurve(slipRatio, spec.pacejkaB(), spec.pacejkaC(), spec.pacejkaE());
    }

    /** Fiala brush tire model, ported from Project Chrono's ChFialaTire::FialaPatchForces
     * @param kappa longitudinal slip ratio (wheelSpeed - groundSpeed) / refSpeed
     * @param alpha slip angle (rad)
     * @param fz    vertical load (N)
     */
    public static void fialaForces(double[] out, double kappa, double alpha, double fz,
                                   double cKappa, double cAlpha, double muMax, double muMin, double frictionScale) {
        if (fz <= 0.0 || cKappa <= 0.0 || cAlpha <= 0.0) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        double tanA = Math.tan(alpha);
        double ssa = Math.min(1.0, Math.sqrt(kappa * kappa + tanA * tanA));
        double u = (muMax - (muMax - muMin) * ssa) * Math.max(0.0, frictionScale);
        double uFz = u * fz;
        if (uFz <= 1.0e-9) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }

        // Longitudinal: linear below the critical slip, brush-saturation toward U*Fz above it
        double sCritical = Math.abs(uFz / (2.0 * cKappa));
        double fx;
        if (Math.abs(kappa) < sCritical) {
            fx = cKappa * kappa;
        } else {
            double fx2 = (uFz * uFz) / (4.0 * Math.abs(kappa) * cKappa);
            fx = Math.signum(kappa) * (uFz - fx2);
        }

        // Lateral: brush cubic below the critical slip angle, full slide above it
        double alphaCritical = Math.atan(3.0 * uFz / cAlpha);
        double fy;
        if (Math.abs(alpha) <= alphaCritical) {
            double h = 1.0 - cAlpha * Math.abs(tanA) / (3.0 * uFz);
            fy = -uFz * (1.0 - h * h * h) * Math.signum(alpha);
        } else {
            fy = -uFz * Math.signum(alpha);
        }

        out[0] = fx;
        out[1] = fy;
    }
    // --------------------------------------------------

    /**
     *  This is Dr. Rill's TMeasy model ported directly from Project Chrono. Dr. Rill's textbook Vehicle Dynamics has been a great reference too
     *  So in this model, there is one combined slip (s) mapping to a force (f) and defined very intuitively by
     *  df0 (initial slope), fm (peak force) at sm (slip) , and fs (sliding force) reached at ss (slip)
     *
     *  Im also going to copy some of their notes below
     */

    // Ref: Georg Rill, "Road Vehicle Dynamics - Fundamentals and Modeling",
    //          https://www.routledge.com/Road-Vehicle-Dynamics-Fundamentals-and-Modeling-with-MATLAB/Rill-Castro/p/book/9780367199739
    //      Georg Rill, "An Engineer's Guess On Tyre Model Parameter Made Possible With TMeasy",
    //          https://www.researchgate.net/publication/317036908_An_Engineer's_Guess_on_Tyre_Parameter_made_possible_with_TMeasy
    //      Georg Rill, "Simulation von Kraftfahrzeugen",
    //          https://www.researchgate.net/publication/317037037_Simulation_von_Kraftfahrzeugen
    //
    // Known differences to the commercial version:
    //  - No parking slip calculations
    //  - No dynamic parking torque
    //  - No dynamic tire inflation pressure
    //  - No belt dynamics
    //  - Simplified stand still handling

    public static void tmeasyCombined(double[] out, double s, double df0, double sm, double fm,
                                      double ss, double fs) {
        double df0loc = sm > 0.0 ? Math.max(2.0 * fm / sm, df0) : 0.0;
        if (s <= 0.0 || df0loc <= 0.0 || fm <= 0.0) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        double f;
        double fos;
        if (s > ss) {                             // full sliding
            f = fs;
            fos = f / s;
        } else if (s < sm) {                      // adhesion
            double p = df0loc * sm / fm - 2.0;
            double sn = s / sm;
            double dn = 1.0 + (sn + p) * sn;
            f = df0loc * sm * sn / dn;
            fos = df0loc / dn;
        } else {                            // transition from peak toward sliding
            double a = (fm / sm) * (fm / sm) / (df0loc * sm);   // from 2nd deriv of f at s = sm
            double sstar = sm + (fm - fs) / (a * (ss - sm));    // where the two parabolas would join
            if (sstar <= ss) {
                if (s <= sstar) {
                    f = fm - a * (s - sm) * (s - sm);            // 1st parabola
                } else {
                    double b = a * (sstar - sm) / (ss - sstar);
                    f = fs + b * (ss - s) * (ss - s);            // 2nd parabola
                }
            } else {
                double sn = (s - sm) / (ss - sm);
                f = fm - (fm - fs) * sn * sn * (3.0 - 2.0 * sn); // cubic fallback (smoothstep)
            }
            fos = f / s;
        }
        out[0] = f;
        out[1] = fos;
    }

    // stop moving when still, based on how offroad seems to do it
    private static final double ROLL_RESIST_SMOOTH = 0.5;

    public static double rollingResistance(double normalForce, double groundSpeed) {
        return Config.ROLLING_RESISTANCE_COEF.getAsDouble() * normalForce
                * Mth.clamp(groundSpeed / ROLL_RESIST_SMOOTH, -1.0, 1.0);
    }

    public static double gripUtilisation(double forwardImpulse, double sideImpulse, double maxImpulse) {
        if (maxImpulse <= 1.0e-9) {
            return 0.0;
        }
        double x = forwardImpulse * Config.FRICTION_ELLIPSE_LONG_WEIGHT.getAsDouble();
        return Math.sqrt(x * x + sideImpulse * sideImpulse) / maxImpulse;
    }

    /** Clamps a pair of impulses from each side to the friction ellipse
    // maxImpulse = N * mu * dt, same thing that Rapier does in DynamicRayCastVehicleController for the sliding check
    // Longitudinal is weighted 0.5, Bullet does this fwd_factor that is for braking/driving to feel smoother I think
    // return is scaled factoer in (0,1] to apply to both impulses; 1 is inside the ellipse
    */
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
        // real dampers have stiffer rebound
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
                                       double tireRate, double tireDamp, double tireDeflect) {
        double denom = unsprungMass / dt + tireRate * dt + tireDamp + springRate * dt + springDamp;
        double rhs = unsprungMass * unsprungVel / dt
                + tireRate * tireDeflect
                - springRate * compression
                + (springRate * dt + springDamp) * hardpointVel
                - unsprungMass * 9.81;
        return rhs / denom;
    }


    /** Integrate wheel spin with one Sable substep, return the new angular velocity
     * @param omega       current angular velocity (rad/s)
     * @param radius      tire radius (m)
     * @param inertia     wheel spin inertia (kg m^2)
     * @param driveTorque torque from the drivetrain (Nm)
     * @param brakeTorque maximum braking torque magnitude (Nm)
     * @param tireForce   longitudinal force the tire is currently transmitting (N)
     * @param groundSpeed longitudinal hub speed (m/s)
     * @param dt          substep length (s)
     */

    public static double integrateSpin(double omega, double radius, double inertia, double driveTorque,
                                       double brakeTorque, double tireForce, double groundSpeed, double dt) {
        double reaction = tireForce * radius;
        double brake = -Math.signum(omega) * brakeTorque;

        // implicitly solve k so it doesnt overshoot
        double rollingOmega = groundSpeed / radius;
        double slipOmega = omega - rollingOmega;
        double k = 0.0;
        if (Math.abs(slipOmega) > 1.0e-3) {
            k = Math.max(0.0, reaction / slipOmega);
        }
        double newOmega = (omega + dt / inertia * (driveTorque + brake + k * rollingOmega))
                / (1.0 + dt * k / inertia);


        // dont let brakes reverse the wheel
        if (brakeTorque > 0.0 && Math.signum(newOmega) != Math.signum(omega) && Math.abs(driveTorque) < brakeTorque) {
            newOmega = 0.0;
        }

        double slipBefore = slipOmega;
        double slipAfter = newOmega - rollingOmega;
        if (slipBefore * slipAfter < 0.0 && brakeTorque < 1.0e-3) {
            newOmega = rollingOmega;
        }
        return Mth.clamp(newOmega, -400.0, 400.0);
    }
}
