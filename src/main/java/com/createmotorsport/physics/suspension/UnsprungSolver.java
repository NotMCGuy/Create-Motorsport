package com.createmotorsport.physics.suspension;

import com.createmotorsport.physics.spec.DamperSpec;
import net.minecraft.util.Mth;

/**
 * Steps one wheel between its suspension spring and its tyre spring
 * Corner calls solve() per wheel per substep, it returns the force the suspension puts into the body
 * No interface yet. A solid axle can't be solved a corner at a time, so a second geometry would want one per axle
 */
public final class UnsprungSolver {

    private UnsprungSolver() {
    }

    /**
     * @param responseMass       the body mode's effective mass, not one corner's
     * @param maxBodyModeOmegaDt above this the spring is detuned rather than allowed to ring
     * @param tireRate           N per block, from the fitted tyre
     * @param rollResponseMass   usually much smaller than responseMass
     */
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

    // zero for a missing mode, so it drops out of the max
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

        // Roll stiffness is 4*k*b^2 against Ixx; a given corners roll mode runs at sqrt(springK/responseMass)
        // semi implicit euler works here only to omega*dt < 2 based on my tests, alternates/vibrates real bad past that.
        // This can happen if the car is built like a pencil with no weight on either side of a 1 block chassis.
        // The solution for now is measuring that value and scaling frequency down when needed
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

        // Re seed if the state is nonsense or the car has moved somewhere else
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

        // tried averaging before but reading the state at the end seemed to be better
        double newCompression = Math.max(0.0, in.restLength() - s.springLength);
        double strutVel = s.unsprungVel - in.hardpointVel();
        double bodyC = SpringDamper.effectiveDamping(springC, strutVel, in.damper(), in.dt(),
                in.responseMass());
        s.dampFraction = springC > 0.0 ? bodyC / springC : 1.0;

        return springK * newCompression + bodyC * strutVel;
    }
}
