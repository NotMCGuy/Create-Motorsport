package com.createmotorsport.physics;

import net.minecraft.util.Mth;

import java.util.List;

/**
 * Immutable definition of an engine tier with values stated in real SI units
 * (Nm, RPM); drivetrainTorqueScale is derived from vehicle mass and a premeasured reference
 */
public record EngineSpec(
        double idleRpm,
        double redlineRpm,
        double shiftUpRpm,
        double shiftDownRpm,
        double drivelineEfficiency,
        double engineBrakeFraction,
        double finalDrive,
        double realVehicleMassKg, //
        double designVehicleMassBlocks,
        List<TorquePoint> torqueCurve
) {

    public record TorquePoint(double rpm, double nm) {
    }


    public EngineSpec {
        if (torqueCurve == null || torqueCurve.isEmpty()) {
            throw new IllegalArgumentException("Engine torque curve is empty");
        }
        for (int i = 1; i < torqueCurve.size(); i++) {
            if (torqueCurve.get(i).rpm() <= torqueCurve.get(i - 1).rpm()) {
                throw new IllegalArgumentException("Engine torque curve rpm must ascend, at index " + i);
            }
        }
        torqueCurve = List.copyOf(torqueCurve);
    }
    // we could change this later, but this is based on logs and
    // subsequent calculations of trying to match the kW output to a realistic example
    private static final double REFERENCE_TORQUE_SCALE = 0.1207;

    private static final double REFERENCE_REAL_MASS_KG = 840.0;

    public static final EngineSpec RACING_V8_HYBRID = new EngineSpec(
            5000, 18000, 16000, 11000, 0.93, 0.15, 14.0,
            REFERENCE_REAL_MASS_KG, MassScale.REFERENCE_CAR_MASS,
            List.of(new TorquePoint(0, 96),
                    new TorquePoint(5000, 176),
                    new TorquePoint(9900, 256),
                    new TorquePoint(16560, 320),
                    new TorquePoint(18000, 320))
    );


    public static final EngineSpec TRUCK_DIESEL = new EngineSpec(
            600, 2400, 1900, 1200, 0.90, 0.22, 10.5,
            12000.0, 1000.0,
            List.of(new TorquePoint(600, 1200),
                    new TorquePoint(1000, 2300),
                    new TorquePoint(1400, 2500),
                    new TorquePoint(1800, 2400),
                    new TorquePoint(2200, 1900),
                    new TorquePoint(2400, 1400))
    );

    public double drivetrainTorqueScale() {
        return REFERENCE_TORQUE_SCALE * MassScale.design(designVehicleMassBlocks)
                * (REFERENCE_REAL_MASS_KG / Math.max(1.0, realVehicleMassKg));
    }

    public double peakTorque() {
        double peak = 0.0;
        for (TorquePoint p : torqueCurve) {
            peak = Math.max(peak, p.nm());
        }
        return peak;
    }

    // Crank torque (Nm) at full throttle, linear between breakpoints, flat below the first and above the
    // above the last point, and nothing at the redline
    public double torqueAt(double rpm) {
        if (rpm >= this.redlineRpm) {
            return 0.0;
        }
        List<TorquePoint> c = this.torqueCurve;
        if (rpm <= c.get(0).rpm()) {
            return c.get(0).nm();
        }
        for (int i = 1; i < c.size(); i++) {
            TorquePoint hi = c.get(i);
            if (rpm <= hi.rpm()) {
                TorquePoint lo = c.get(i - 1);
                double span = hi.rpm() - lo.rpm();
                return Mth.lerp(span > 0.0 ? (rpm - lo.rpm()) / span : 0.0, lo.nm(), hi.nm());
            }
        }
        return c.get(c.size() - 1).nm();
    }
}
