package com.createmotorsport.physics.tire;

import com.createmotorsport.Config;
import com.createmotorsport.physics.spec.ModelParams;
import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.spec.TireSpec;
import net.minecraft.util.Mth;

/**
 * TYRE MODEL 3, "Fiala brush model"
 *
 * Ported from Project Chrono
 *
 * Corner calls solve() every substep through TireModels
 */
public final class FialaTire implements Tire {

    public static final FialaTire INSTANCE = new FialaTire();

    private FialaTire() {
    }

    /** Fiala brush tire model, ported from Project Chrono's ChFialaTire::FialaPatchForces
     * @param kappa longitudinal slip ratio (wheelSpeed - groundSpeed) / refSpeed
     * @param alpha slip angle (rad)
     * @param fz    vertical load (N)
     */
    private static void fialaForces(double[] out, double kappa, double alpha, double fz,
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

    private static double[] forces(Input in, Contact c, ModelParams.Fiala p, double vRef, double alpha) {
        double kappa = (c.omega * in.radius() - in.vLon()) / vRef;
        double[] fiala = new double[2];
        fialaForces(fiala, kappa, alpha, in.normalForce(),
                p.slipStiffness(), p.corneringStiffness(),
                p.muMax(), p.muMin(), in.effectiveMu() * in.spec().grip());
        return fiala;
    }

    /**
     * Where the brush goes into full slide, {@code alphaCritical} in {@code fialaForces}. Its friction falls
     * with slip, so the angle is the fixed point of {@code tan a = 3 * u * Fz / C_a} with {@code u} taken at it.
     */
    @Override
    public double peakSlipAngle(TireSpec spec, double load, double designLoad, SurfaceSpec surface, double mu) {
        ModelParams.Fiala p = spec.params(ModelParams.Fiala.class);
        double s = mu * spec.grip();
        double denom = p.corneringStiffness() + 3.0 * (p.muMax() - p.muMin()) * s * load;
        return denom > 0.0 ? Math.atan(Math.min(1.0, 3.0 * p.muMax() * s * load / denom)) : Double.NaN;
    }

    @Override
    public Forces solve(Input in, Contact c) {
        double vLon = in.vLon();
        double vLat = in.vLat();
        double dt = in.dt();

        double vRef = Math.max(Math.abs(vLon), Config.SIM_LOWSPEED_REF.getAsDouble());
        ModelParams.Fiala p = in.spec().params(ModelParams.Fiala.class);
        double relaxLen = Math.max(p.relaxLength(), 1.0e-3);
        double decayRate = vRef / relaxLen;
        double decay = Math.exp(-decayRate * dt);
        double tauEff = decayRate > 1.0e-9 ? (1.0 - decay) / decayRate : dt;
        c.deflAlpha = c.deflAlpha * decay + vLat * tauEff;
        double alpha = Math.atan2(c.deflAlpha, relaxLen);

        // The wheel is held by the curve at the slip it ends on, and the car is pushed by the same force, as in TmeasyTire
        c.omega = TireModel.stepSpin(c.omega, in.radius(), in.wheelInertia(), in.driveTorque(),
                in.driveStiffness(), in.driveMin(), in.driveMax(), in.brakeTorque(), in.absOmega(), vLon, dt, w -> {
                    c.omega = w;
                    return forces(in, c, p, vRef, alpha)[0];
                });
        double[] fiala = forces(in, c, p, vRef, alpha);
        double forwardForce = fiala[0];
        double lateralForce = fiala[1];

        c.curveLongForce = forwardForce;
        forwardForce = c.prevLongForce + (forwardForce - c.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
        c.prevLongForce = forwardForce;
        if (!in.powered()) {
            forwardForce -= TireModel.rollingResistance(in.spec().rollingResistance(), in.normalForce(), vLon)
                    * in.surface().rollingResistanceScale();
        }

        // Blend on forward speed, not total
        double speed = Math.abs(vLon);
        double blendSpeed = Config.SIM_LOWSPEED_BLEND_MS.getAsDouble();
        if (blendSpeed > 1.0e-6) {
            double slipBlend = Mth.clamp(speed / blendSpeed, 0.0, 1.0);
            double cancelForce = in.invSideMass() > 1.0e-9
                    ? (-vLat / in.invSideMass() * Config.LATERAL_GRIP_FRACTION.getAsDouble()) / dt : 0.0;
            lateralForce = Mth.lerp(slipBlend, cancelForce, lateralForce);
        }

        lateralForce += Camber.thrust(in.camberDeg(), in.camberStiffness(), in.gripForce(),
                lateralForce, vLon, in.camberFadeSpeed(), in.sideSign());

        return new Forces(forwardForce * dt, lateralForce * dt);
    }
}
