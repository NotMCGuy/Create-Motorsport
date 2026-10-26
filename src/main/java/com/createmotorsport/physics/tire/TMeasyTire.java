package com.createmotorsport.physics.tire;

import com.createmotorsport.Config;
import com.createmotorsport.physics.spec.ModelParams;
import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.spec.TireSpec;
import net.minecraft.util.Mth;

public final class TMeasyTire implements Tire {

    public static final TMeasyTire INSTANCE = new TMeasyTire();

    private TMeasyTire() {
    }



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
        if (s > ss) {
            f = fs;
            fos = f / s;
        } else if (s < sm) {
            double p = df0loc * sm / fm - 2.0;
            double sn = s / sm;
            double dn = 1.0 + (sn + p) * sn;
            f = df0loc * sm * sn / dn;
            fos = df0loc / dn;
        } else {
            double a = (fm / sm) * (fm / sm) / (df0loc * sm);
            double sstar = sm + (fm - fs) / (a * (ss - sm));
            if (sstar <= ss) {
                if (s <= sstar) {
                    f = fm - a * (s - sm) * (s - sm);
                } else {
                    double b = a * (sstar - sm) / (ss - sstar);
                    f = fs + b * (ss - s) * (ss - s);
                }
            } else {
                double sn = (s - sm) / (ss - sm);
                f = fm - (fm - fs) * sn * sn * (3.0 - 2.0 * sn);
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

        double vta = Math.max(Math.abs(wheelSpeed), Config.SIM_LOWSPEED_REF.getAsDouble());
        double sx = (wheelSpeed - vLon) / vta;
        double sy = -vLat / vta;

        double peakForce = in.normalForce() * in.effectiveMu() * in.spec().grip();
        TireSpec.Axis lon = in.spec().longitudinal();
        TireSpec.Axis lat = in.spec().lateral();
        double fxm = peakForce * lon.peakRatio(), fym = peakForce * lat.peakRatio();
        double fxs = fxm * Math.min(1.0, lon.slideGrip() * in.surface().slideGripScale());
        double fys = fym * Math.min(1.0, lat.slideGrip() * in.surface().slideGripScale());
        double sxm = lon.slipAtPeak() * in.surface().slipAtPeakScale();
        double q = in.designLoad() > 0.0 ? in.normalForce() / in.designLoad() : 1.0;
        double sym = in.spec().latPeakAtLoad(q) * in.surface().slipAtPeakScale();
        double sxs = sxm * lon.slideSlipFactor(), sys = sym * lat.slideSlipFactor();
        double dfx0 = lon.initialStiffness() * fxm / sxm;
        double dfy0 = lat.initialStiffness() * fym / sym;

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

        c.curveLongForce = forwardForce;
        forwardForce = c.prevLongForce
                + (forwardForce - c.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
        c.prevLongForce = forwardForce;

        if (!in.powered()) {
            forwardForce -= TireModel.rollingResistance(in.spec().rollingResistance(), in.normalForce(), vLon)
                    * in.surface().rollingResistanceScale();
        }

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

        double relaxLenLat = in.spec().relaxLengthLat();
        if (relaxLenLat > 1.0e-6 && dfy0 > 1.0e-9) {
            double fSec = Math.abs(sy) > 1.0e-6 ? Math.min(Math.abs(lateralForce / sy), dfy0) : dfy0;
            double cy = dfy0 / relaxLenLat;
            double yNew = (fSec * c.latRelax - dt * fSec * vLat) / (fSec + dt * vta * cy);
            c.latRelax = yNew;
            lateralForce = cy * yNew;
        }

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
