package com.createmotorsport.physics.vehicle;

import com.createmotorsport.physics.spec.DriveInputSpec;

public final class AxleDrive {

    private double taken;

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
