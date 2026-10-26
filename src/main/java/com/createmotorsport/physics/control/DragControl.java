package com.createmotorsport.physics.control;

import net.minecraft.util.Mth;

public final class DragControl {

    private DragControl() {
    }

    public static double step(double throttle, double slowSurfaceSpeed, double groundSpeed, double targetSlip,
                              double maxThrottle, double cutRate, double buildRate, double dt) {
        if (maxThrottle <= 0.0) {
            return 0.0;
        }
        double slip = (groundSpeed - slowSurfaceSpeed) / Math.max(groundSpeed, TractionControl.SPEED_FLOOR);
        double error = Mth.clamp((slip - targetSlip) / Math.max(targetSlip, 1.0e-6), -1.0, 1.0);
        double rate = error > 0.0 ? buildRate : cutRate;
        return Mth.clamp(throttle + rate * error * maxThrottle * dt, 0.0, maxThrottle);
    }
}
