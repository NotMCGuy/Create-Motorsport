package com.createmotorsport.physics;

public final class MassScale {

    private MassScale() {
    }

    public static final double REFERENCE_CAR_MASS = 68.5;

    public static double measured(double carMass) {
        return Math.max(0.05, carMass) / REFERENCE_CAR_MASS;
    }

    public static double design(double designVehicleMassBlocks) {
        return Math.max(0.05, designVehicleMassBlocks) / REFERENCE_CAR_MASS;
    }
}
