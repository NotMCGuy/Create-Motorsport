package com.createmotorsport.physics.suspension;

import com.createmotorsport.physics.spec.DamperSpec;
import net.minecraft.util.Mth;

public final class UnsprungSolver {

    private UnsprungSolver() {
    }

    public record Input(double rateMass, double rigidLength, double hardpointVel, double responseMass,
                        double rollResponseMass,
                        double dt, double restLength, double maxTravel, double maxDroop,
                        double unsprungMass, double gravity,
                        double naturalFreqHz, double dampingRatio, DamperSpec damper,
                        double tireRate, double tireVerticalDamping,
                        double maxBodyModeOmegaDt, double maxSubStep) {
    }

    public static final class State {
        public double springLength;
        public double unsprungVel;

        public double tireDeflection;
        public double groundLength;
        public double contactLoad;
        public double dampFraction;
        public double bodyModeOmegaDt;
        public double rollModeOmegaDt;
    }

    private static double omegaDt(double springK, double modeMass, double dt) {
        return modeMass > 1.0e-9 ? Math.sqrt(springK / modeMass) * dt : 0.0;
    }

    public static double detunedFreq(double naturalFreqHz, double cornerOmegaDt, double rollOmegaDt,
                                     double maxOmegaDt) {
        double worst = Math.max(cornerOmegaDt, rollOmegaDt);
        return worst > maxOmegaDt ? naturalFreqHz * maxOmegaDt / worst : naturalFreqHz;
    }

    public static double solve(Input in, State s) {
        boolean rebound = in.hardpointVel() > 0.0;
        double springK = SpringDamper.springRate(in.rateMass(), in.naturalFreqHz());

        double cornerOmegaDt = omegaDt(springK, in.responseMass(), in.dt());
        double rollOmegaDt = omegaDt(springK, in.rollResponseMass(), in.dt());
        s.bodyModeOmegaDt = cornerOmegaDt;
        s.rollModeOmegaDt = rollOmegaDt;
        double freqHz = detunedFreq(in.naturalFreqHz(), cornerOmegaDt, rollOmegaDt,
                in.maxBodyModeOmegaDt());
        if (freqHz < in.naturalFreqHz()) {
            springK = SpringDamper.springRate(in.rateMass(), freqHz);
        }
        double springC = SpringDamper.springDamping(in.rateMass(), freqHz, in.dampingRatio(), rebound);
        double tireK = in.tireRate();
        double unsprungMass = in.unsprungMass();
        double tireC = 2.0 * in.tireVerticalDamping() * Math.sqrt(tireK * unsprungMass);

        if (!Double.isFinite(s.springLength) || !Double.isFinite(s.unsprungVel)
                || Math.abs(s.springLength - in.rigidLength()) > in.maxTravel() + in.maxDroop()) {
            s.springLength = Mth.clamp(in.rigidLength(), in.restLength() - in.maxTravel(),
                    in.restLength() + in.maxDroop());
            s.unsprungVel = 0.0;
        }

        int steps = Math.max(1, (int) Math.ceil(in.dt() / in.maxSubStep()));
        double h = in.dt() / steps;
        double groundLength = in.rigidLength();
        for (int i = 0; i < steps; i++) {
            double deflection = Math.max(0.0, s.springLength - groundLength);
            double compression = Math.max(0.0, in.restLength() - s.springLength);
            double strutC = SpringDamper.digressiveDamping(springC, in.hardpointVel() - s.unsprungVel,
                    in.damper());
            s.unsprungVel = SpringDamper.solveUnsprung(unsprungMass, h, s.unsprungVel, in.hardpointVel(),
                    springK, strutC, compression, tireK, tireC, deflection, in.gravity());
            s.springLength = Mth.clamp(s.springLength + h * (in.hardpointVel() - s.unsprungVel),
                    in.restLength() - in.maxTravel(), in.restLength() + in.maxDroop());
            groundLength += h * in.hardpointVel();
        }
        s.tireDeflection = Math.max(0.0, s.springLength - groundLength);
        s.groundLength = groundLength;
        s.contactLoad = tireK * s.tireDeflection;

        double newCompression = Math.max(0.0, in.restLength() - s.springLength);
        double strutVel = s.unsprungVel - in.hardpointVel();
        double bodyC = SpringDamper.effectiveDamping(springC, strutVel, in.damper(), in.dt(),
                in.responseMass());
        s.dampFraction = springC > 0.0 ? bodyC / springC : 1.0;

        return springK * newCompression + bodyC * strutVel;
    }
}
