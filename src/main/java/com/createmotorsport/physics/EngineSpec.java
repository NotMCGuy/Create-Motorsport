package com.createmotorsport.physics;

import net.minecraft.util.Mth;

/**
 * Immutable definition of an engine + gearbox tier with values stated in real SI units
 * (Nm, RPM); DRIVETRAIN_TORQUE_SCALE in the common config, converts crank torque
 * into Sable's world scale, so users will see realistic car numbers, not Create/Sable's numbers
 */
public record EngineSpec(
        double idleRpm,
        double redlineRpm,
        double peakTorque,
        double shiftUpRpm,
        double shiftDownRpm,
        double drivelineEfficiency
) {

    // still working on tuning this
    public static final EngineSpec RACING_V8_HYBRID = new EngineSpec(
            5000, 18000, 320, 16000, 11000, 0.93
    );


    // Crank torque in (Nm) at full throttle for a 2011 F1 2.4L V8

    public double torqueAt(double rpm) {
        if (rpm >= this.redlineRpm) {
            return 0.0;
        }
        if (rpm <= 0.0) {
            return 0.20 * this.peakTorque;
        }

        double idleFrac = this.idleRpm / this.redlineRpm;
        double n = rpm / this.redlineRpm;

        double powerBandEntry = 0.55;
        double peakStart = 0.92;

        double factor;
        if (n < idleFrac) {
            factor = Mth.lerp(n / idleFrac, 0.30, 0.55);
        } else if (n < powerBandEntry) {
            factor = Mth.lerp((n - idleFrac) / (powerBandEntry - idleFrac), 0.55, 0.80);
        } else if (n < peakStart) {
            factor = Mth.lerp((n - powerBandEntry) / (peakStart - powerBandEntry), 0.80, 1.0);
        } else {
            factor = 1.0;
        }

        return factor * this.peakTorque;
    }



    // Passive drag form the engine when throttle is closed, in newton-meters (Nm)
    public double engineBrakeTorque(double rpm, double fraction) {
        return fraction * this.peakTorque * Mth.clamp(rpm / this.redlineRpm, 0.0, 1.1);
    }
}
