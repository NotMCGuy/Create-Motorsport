package com.createmotorsport.physics.spec;

import com.createmotorsport.physics.MassScale;

import net.minecraft.util.Mth;

import java.util.List;

public record EngineSpec(
        double designVehicleMassBlocks,
        double idleRpm,
        double redlineRpm,
        double shiftUpRpm,
        double shiftDownRpm,
        double drivelineEfficiency,
        double engineBrakeFraction,
        double engineInertia,
        double finalDrive,
        double realVehicleMassKg, //
        List<TorquePoint> torqueCurve,
        TractionControlSpec tractionControl,
        double stallRpm, // 0 is idle x 2/5
        StarterSpec starter
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

    public static final EngineSpec F1_V8_2011 = new EngineSpec(
            MassScale.REFERENCE_CAR_MASS,
            5000, 18000, 16000, 11000, 0.93, 0.15, 0.05, 14.0,
            REFERENCE_REAL_MASS_KG,
            List.of(new TorquePoint(0, 96),
                    new TorquePoint(5000, 176),
                    new TorquePoint(9900, 256),
                    new TorquePoint(16560, 320),
                    new TorquePoint(18000, 320)),
            TractionControlSpec.DEFAULT, 0.0, StarterSpec.DEFAULT
    );

    public static final EngineSpec TRUCK_DIESEL = new EngineSpec(
            200.0,
            600, 2400, 1900, 1200, 0.90, 0.22, 1.46, 8.1375,
            12000.0,
            List.of(new TorquePoint(600, 1200),
                    new TorquePoint(1000, 2300),
                    new TorquePoint(1400, 2500),
                    new TorquePoint(1800, 2400),
                    new TorquePoint(2200, 1900),
                    new TorquePoint(2400, 1400)),
            TractionControlSpec.DEFAULT, 0.0, StarterSpec.DEFAULT
    );

    public static final EngineSpec PASSENGER_I4_TURBO = new EngineSpec(
            75.0,
            750, 6500, 6200, 2400, 0.91, 0.18, 0.26, 6.25,
            1400.0,
            List.of(new TorquePoint(800, 130),
                    new TorquePoint(1500, 350),
                    new TorquePoint(4400, 350),
                    new TorquePoint(5500, 293),
                    new TorquePoint(6500, 235)),
            TractionControlSpec.DEFAULT, 0.0, StarterSpec.DEFAULT
    );

    public static final EngineSpec MOTORCYCLE_I4 = new EngineSpec(
            30.0,
            1300, 14000, 13500, 8500, 0.92, 0.15, 0.05, 6.14,
            275.0,
            List.of(new TorquePoint(1300, 45),
                    new TorquePoint(4000, 72),
                    new TorquePoint(8000, 92),
                    new TorquePoint(11500, 112),
                    new TorquePoint(13500, 104),
                    new TorquePoint(14000, 98)),
            TractionControlSpec.DEFAULT, 0.0, StarterSpec.DEFAULT
    );

    public double drivetrainTorqueScale() {
        return REFERENCE_TORQUE_SCALE * MassScale.design(designVehicleMassBlocks)
                * (REFERENCE_REAL_MASS_KG / Math.max(1.0, realVehicleMassKg));
    }

    public double stallRpmOrDerived() {
        return this.stallRpm > 0.0 ? this.stallRpm : 0.4 * this.idleRpm;
    }

    public double peakTorque() {
        double peak = 0.0;
        for (TorquePoint p : torqueCurve) {
            peak = Math.max(peak, p.nm());
        }
        return peak;
    }

    // Crank torque (Nm) at full throttle, linear between breakpoints, flat below the 1st and above last point,
    // nothing at the redline, which may impact keyboard users and need some additional assist there later
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
