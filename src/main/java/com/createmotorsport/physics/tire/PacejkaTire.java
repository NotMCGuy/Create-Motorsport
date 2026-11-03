package com.createmotorsport.physics.tire;

import com.createmotorsport.Config;
import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.spec.TireSpec;
import net.minecraft.util.Mth;

/**
 * TYRE MODEL 2, "Pacejka Magic Formula"
 *
 * Dr. Pacejka's combined slip model and a friction ellipse
 *
 * Corner calls solve() every substep in TireModels
 */
public final class PacejkaTire implements Tire {

    public static final PacejkaTire INSTANCE = new PacejkaTire();

    private PacejkaTire() {
    }

    /** Longitudinal and lateral force on the steady curve at the wheel's current spin. */
    private static double[] curve(Input in, Contact c) {
        double vLon = in.vLon();
        double vLat = in.vLat();
        double wheelSpeed = c.omega * in.radius();
        double denom = Math.max(Math.abs(vLon), 2.0);
        double slipLon = (wheelSpeed - vLon) / denom;
        double slipLat = vLat / Math.max(Math.abs(vLon), Config.SIM_LOWSPEED_REF.getAsDouble());

        // friction ellipse instead of circle for configurable lateral slipperiness
        double latGrip = Config.SIM_LATERAL_GRIP.getAsDouble();
        double slipLatEff = slipLat / latGrip;
        double s = Math.sqrt(slipLon * slipLon + slipLatEff * slipLatEff);

        double forwardForce = 0.0;
        double lateralForce = 0.0;
        if (s > 1.0e-5) {
            double fMag = in.normalForce() * in.effectiveMu() * in.spec().grip()
                    * TireModel.slipCurve(Math.min(s, Config.SIM_SLIP_LIMIT.getAsDouble()),
                            in.spec().pacejkaB(), in.spec().pacejkaC(), in.spec().pacejkaE());
            forwardForce = fMag * slipLon / s;
            lateralForce = -fMag * slipLat / s;
        }
        return new double[]{forwardForce, lateralForce};
    }

    /**
     * Where {@code slipCurve} peaks, {@code C * atan(phi) = pi/2}, solved for the slip and taken back through the
     * lateral ellipse {@code curve} divides by. A curve with {@code C <= 1} only rises, so its peak is the slip limit.
     */
    @Override
    public double peakSlipAngle(TireSpec spec, double load, double designLoad, SurfaceSpec surface, double mu) {
        double limit = Config.SIM_SLIP_LIMIT.getAsDouble();
        double b = spec.pacejkaB(), c = spec.pacejkaC(), e = spec.pacejkaE();
        double x = limit;
        if (c > 1.0 && b > 0.0) {
            double target = Math.tan(Math.PI / (2.0 * c));
            double lo = 0.0, hi = limit;
            for (int i = 0; i < 40; i++) {
                double mid = 0.5 * (lo + hi);
                double bx = b * mid;
                if (bx * (1.0 - e) + e * Math.atan(bx) < target) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            x = 0.5 * (lo + hi);
        }
        return Math.atan(x * Config.SIM_LATERAL_GRIP.getAsDouble());
    }

    @Override
    public Forces solve(Input in, Contact c) {
        double vLon = in.vLon();
        double vLat = in.vLat();
        double dt = in.dt();

        c.omega = TireModel.stepSpin(c.omega, in.radius(), in.wheelInertia(), in.driveTorque(),
                in.driveStiffness(), in.driveMin(), in.driveMax(), in.brakeTorque(), in.absOmega(), vLon, dt, w -> {
                    c.omega = w;
                    return curve(in, c)[0];
                });
        double[] curve = curve(in, c);
        double forwardForce = curve[0];
        double lateralForce = curve[1];

        c.curveLongForce = forwardForce;
        forwardForce = c.prevLongForce + (forwardForce - c.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
        c.prevLongForce = forwardForce;
        if (!in.powered()) {
            forwardForce -= TireModel.rollingResistance(in.spec().rollingResistance(), in.normalForce(), vLon)
                    * in.surface().rollingResistanceScale();
        }

        double speed = Math.abs(vLon);
        double slipBlend = Mth.clamp(speed / Config.SIM_LOWSPEED_BLEND_MS.getAsDouble(), 0.0, 1.0);
        double cancelForce = in.invSideMass() > 1.0e-9
                ? (-vLat / in.invSideMass() * Config.LATERAL_GRIP_FRACTION.getAsDouble()) / dt : 0.0;
        lateralForce = Mth.lerp(slipBlend, cancelForce, lateralForce);

        lateralForce += Camber.thrust(in.camberDeg(), in.camberStiffness(), in.gripForce(),
                lateralForce, vLon, in.camberFadeSpeed(), in.sideSign());

        return new Forces(forwardForce * dt, lateralForce * dt);
    }
}
