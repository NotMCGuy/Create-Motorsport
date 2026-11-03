package com.createmotorsport.physics.vehicle;

import com.createmotorsport.physics.spec.DriveInputSpec;

/**
 * The torque ramp into one axle (DriveInputSpec), one per axle in CarState
 */
public final class AxleDrive {

    private double taken;

    // Per wheel Nm that the axle took last substep, not counting traction control
    public void took(double perWheel) {
        this.taken = perWheel;
    }

    public double min(DriveInputSpec input, double limit, double dt) {
        return input.isInstant() ? -limit : Math.max(-limit, input.step(this.taken, -limit, dt));
    }

    public double max(DriveInputSpec input, double limit, double dt) {
        return input.isInstant() ? limit : Math.min(limit, input.step(this.taken, limit, dt));
    }
}
