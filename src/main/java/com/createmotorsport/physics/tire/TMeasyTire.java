package com.createmotorsport.physics.tire;

import com.createmotorsport.Config;
import com.createmotorsport.physics.spec.ModelParams;
import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.spec.TireSpec;
import net.minecraft.util.Mth;

/**
 * TYRE MODEL 4, "TMeasy" (Rill)
 *
 *  Ported from Project Chrono's ChTMeasyTire. The combined curve is tmeasyCombined
 *
 * Corner calls solve() each substep through TireModels
 */
public final class TMeasyTire implements Tire {

    public static final TMeasyTire INSTANCE = new TMeasyTire();

    private TMeasyTire() {
    }

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

    private static void tmeasyCombined(double[] out, double s, double df0, double sm, double fm,
                                      double ss, double fs) {
        double df0loc = sm > 0.0 ? Math.max(2.0 * fm / sm, df0) : 0.0;
        if (s <= 0.0 || df0loc <= 0.0 || fm <= 0.0) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        double f;
        double fos;
        if (s > ss) {                                    // full sliding
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

    private record Steady(double forward, double lateral, double fym, double dfy0, double sy, double vta, double fxm) {
    }

    private static Steady steady(Input in, Contact c) {
        double vLon = in.vLon();
        double vLat = in.vLat();
        double wheelSpeed = c.omega * in.radius();

        // Slip inputs, this part is from Chrono's ChTMeasyTire 'Synchronize', covering the slip inputs and configurable
        // transport speed floor. Chrono used a tiny vnum of like 0.01 but due to our physics substeps this should be more like 1
        double vta = Math.max(Math.abs(wheelSpeed), Config.SIM_LOWSPEED_REF.getAsDouble());
        double sx = (wheelSpeed - vLon) / vta;
        double sy = -vLat / vta;

        // Chrono uses data driven tire inputs, instead we have estimatable values, inspired by a paper Dr. Rill wrote,
        // titled: "An Engineer's Guess on Tyre Model Parameter Made Possible with Tmeasy"
        double peakForce = in.normalForce() * in.effectiveMu() * in.spec().grip();
        TireSpec.Axis lon = in.spec().longitudinal();
        TireSpec.Axis lat = in.spec().lateral();
        double fxm = peakForce * lon.peakRatio(), fym = peakForce * lat.peakRatio();
        double fxs = fxm * Math.min(1.0, lon.slideGrip() * in.surface().slideGripScale());
        double fys = fym * Math.min(1.0, lat.slideGrip() * in.surface().slideGripScale());
        double sxm = lon.slipAtPeak() * in.surface().slipAtPeakScale();
        // Chrono interpolates the slip at peak between parameter sets at the nominal load and twice it
        // (ChTMeasyTire.cpp, sym = InterpL(sym_pn, sym_p2n)). latPeakAtLoad does the same from one set; keep the
        // load dependence, a car's lateral peak moves with load and anti-Ackermann relies on it
        double q = in.designLoad() > 0.0 ? in.normalForce() / in.designLoad() : 1.0;
        double sym = in.spec().latPeakAtLoad(q) * in.surface().slipAtPeakScale();
        double sxs = sxm * lon.slideSlipFactor(), sys = sym * lat.slideSlipFactor();
        double dfx0 = lon.initialStiffness() * fxm / sxm;
        double dfy0 = lat.initialStiffness() * fym / sym;

        // This part also deviates a bit from Chrono because I was reading Dr. Rill's textbook, and wanted to try
        // the refinement he suggests, its a combined slip normalization found in equations 3.129 - 3.140
        // intended to rescale the two slip values (throttle/steer) so they are more comparable for a combined slip model
        double fOverDf0X = fxm / dfx0;
        double fOverDf0Y = fym / dfy0;
        double shatX = sxm / (sxm + sym) + fOverDf0X / (fOverDf0X + fOverDf0Y);
        double shatY = sym / (sxm + sym) + fOverDf0Y / (fOverDf0X + fOverDf0Y);
        double sNx = sx / shatX;
        double sNy = sy / shatY;
        double sc = Math.hypot(sNx, sNy);
        double cphi = sc > 1.0e-9 ? sNx / sc : Math.sqrt(0.5);
        double sphi = sc > 1.0e-9 ? sNy / sc : Math.sqrt(0.5);
        double df0 = Math.hypot(dfx0 * shatX * cphi, dfy0 * shatY * sphi);
        double fm = Math.hypot(fxm * cphi, fym * sphi);
        double sm = Math.hypot(sxm / shatX * cphi, sym / shatY * sphi);
        double fs = Math.hypot(fxs * cphi, fys * sphi);
        double ss = Math.hypot(sxs / shatX * cphi, sys / shatY * sphi);
        double[] tm = new double[2];

        // this part is from Chrono's ChTMeasyTire 'Advanced' & 'tmxy_combined'
        tmeasyCombined(tm, sc, df0, sm, fm, ss, fs);
        double f = tm[0];
        c.telemPeakForce = peakForce;
        c.telemSlipCombined = sc;
        c.telemSlipAtPeak = sm;
        c.telemCurveForce = f;
        c.telemSlipRatio = sx;
        c.telemSlipAngleRad = Math.atan(sy);
        c.telemModelSlip = true;
        double forwardForce = sc > 1.0e-9 ? f * cphi : 0.0;
        double lateralForce = sc > 1.0e-9 ? f * sphi : 0.0;
        return new Steady(forwardForce, lateralForce, fym, dfy0, sy, vta, fxm);
    }

    // Chrono's sigma0 100000 N/m against fc = Fz mu saturates at about 4 cm on a road-car corner, x lambda 1.95
    private static final double LON_BRISTLE_LENGTH = 0.078;

    private static double lonBristle(Input in, Contact c, double omega, double fc) {
        if (fc <= 1.0e-6) {
            return 0.0;
        }
        ModelParams.TMeasy p = in.spec().params(ModelParams.TMeasy.class);
        double vsx = in.vLon() - omega * in.radius();
        double sigma0 = fc / LON_BRISTLE_LENGTH;
        double effMass = in.invSideMass() > 1.0e-9 ? 1.0 / in.invSideMass() : 1.0;
        double sigma1 = p.bristleDamping() * 2.0 * Math.sqrt(sigma0 * effMass);
        // Chrono's |vsx|
        double vWash = Math.abs(vsx);
        double zDot = vsx - sigma0 * c.lonBristle * vWash / fc;
        return Mth.clamp(-(sigma0 * c.lonBristle + sigma1 * zDot), -fc, fc);
    }

    @Override
    public double peakSlipAngle(TireSpec spec, double load, double designLoad, SurfaceSpec surface, double mu) {
        double q = designLoad > 0.0 ? load / designLoad : 1.0;
        return Math.atan(spec.latPeakAtLoad(q) * surface.slipAtPeakScale());
    }

    @Override
    public Forces solve(Input in, Contact c) {
        double vLon = in.vLon();
        double vLat = in.vLat();
        double dt = in.dt();

        // smooth blend from rest to steady curve at speed, from Chrono's ChTMeasyTire 'Advance'
        double frT = Mth.clamp(Math.abs(vLon) / in.spec().params(ModelParams.TMeasy.class).standstillSpeed(), 0.0, 1.0);
        double frBlend = frT * frT * (3.0 - 2.0 * frT);
        double lonBlend = in.brakeTorque() > 1.0e-3 ? frBlend : 1.0;
        c.omega = TireModel.stepSpin(c.omega, in.radius(), in.wheelInertia(), in.driveTorque(),
                in.driveStiffness(), in.driveMin(), in.driveMax(), in.brakeTorque(), in.absOmega(), vLon, dt, w -> {
                    c.omega = w;
                    Steady at = steady(in, c);
                    return lonBlend >= 1.0 ? at.forward()
                            : Mth.lerp(lonBlend, lonBristle(in, c, w, at.fxm()), at.forward());
                });
        Steady st = steady(in, c);
        double forwardForce = st.forward();
        double lateralForce = st.lateral();
        double fym = st.fym(), dfy0 = st.dfy0(), sy = st.sy(), vta = st.vta();

        // Flat relaxation, speed-dreams' FLOAT_RELAXATION, as the other three models
        c.curveLongForce = forwardForce;
        forwardForce = c.prevLongForce
                + (forwardForce - c.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
        c.prevLongForce = forwardForce;

        // a free wheel's only, for now, a driven wheel has none
        if (!in.powered()) {
            forwardForce -= TireModel.rollingResistance(in.spec().rollingResistance(), in.normalForce(), vLon)
                    * in.surface().rollingResistanceScale();
        }

        // Chrono's longitudinal half of 'CombinedCoulombForces'
        if (in.powered() && lonBlend < 1.0 && st.fxm() > 1.0e-6) {
            double fxBristle = lonBristle(in, c, c.omega, st.fxm());
            double vsx = vLon - c.omega * in.radius();
            double sigma0 = st.fxm() / LON_BRISTLE_LENGTH;
            double vWash = Math.abs(vsx);
            c.lonBristle = (st.fxm() * c.lonBristle + st.fxm() * dt * vsx) / (st.fxm() + dt * sigma0 * vWash);
            forwardForce = Mth.lerp(lonBlend, fxBristle, forwardForce);
        } else {
            c.lonBristle = 0.0;
        }

        // Rill 3.4.2 "Enhanced Force Dynamics", eqs 3.165 and 3.170
        double relaxLenLat = in.spec().relaxLengthLat();
        if (relaxLenLat > 1.0e-6 && dfy0 > 1.0e-9) {
            // f in 3.170 as the secant F/sy, not the slope, so the steady state lands on the curve
            double fSec = Math.abs(sy) > 1.0e-6 ? Math.min(Math.abs(lateralForce / sy), dfy0) : dfy0;
            double cy = dfy0 / relaxLenLat;
            // backward euler
            double yNew = (fSec * c.latRelax - dt * fSec * vLat) / (fSec + dt * vta * cy);
            c.latRelax = yNew;
            lateralForce = cy * yNew;
        }

        // This part is Chrono's ChTMeasyTire 'CombinedCoulombForces', which is TMeasy's own Dahl bristle stand-still model
        double relaxLen = Math.max(in.spec().relaxLengthLat(), 1.0e-3);
        double fc = fym;
        double fyBristle;
        if (fc > 1.0e-6) {
            double sigma0 = fc / relaxLen;
            double effMass = in.invSideMass() > 1.0e-9 ? 1.0 / in.invSideMass() : 1.0;
            double sigma1 = in.spec().params(ModelParams.TMeasy.class).bristleDamping() * 2.0 * Math.sqrt(sigma0 * effMass);
            double vWash = Math.max(Math.abs(vLat), in.spec().params(ModelParams.TMeasy.class).standstillLeak());
            double bry = c.latBristle;
            double bryDot = vLat - sigma0 * bry * vWash / fc;
            fyBristle = -(sigma0 * bry + sigma1 * bryDot);
            // implicit BDF1 update of the bristle deflection (unconditionally stable)
            c.latBristle = (fc * bry + fc * dt * vLat) / (fc + dt * sigma0 * vWash);
            fyBristle = Mth.clamp(fyBristle, -fc, fc);
        } else {
            c.latBristle = 0.0;
            fyBristle = 0.0;
        }
        lateralForce = Mth.lerp(frBlend, fyBristle, lateralForce);

        lateralForce += Camber.thrust(in.camberDeg(), in.camberStiffness(), in.gripForce(),
                lateralForce, vLon, in.camberFadeSpeed(), in.sideSign());

        return new Forces(forwardForce * dt, lateralForce * dt);
    }
}
