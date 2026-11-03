package com.createmotorsport.physics;

public final class MassScale {

    private MassScale() {
    }

    // Dont mess with this, unless you want to retune everything
    public static final double REFERENCE_CAR_MASS = 68.5;

    // For things that should scale perfectly with mass, so brakes, differentials, wheel inertia
    public static double measured(double carMass) {
        return Math.max(0.05, carMass) / REFERENCE_CAR_MASS;
    }

    // For anything that needs to scale with the design load but not the measured mass,
    // so that we dont cancel out power-to-weight realism.
    public static double design(double designVehicleMassBlocks) {
        return Math.max(0.05, designVehicleMassBlocks) / REFERENCE_CAR_MASS;
    }
}
