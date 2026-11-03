package com.createmotorsport.physics.tire;

import com.createmotorsport.physics.spec.TireSpec;
import net.minecraft.util.Mth;

/**
 * Camber thrust, from Dr. Rill's Vehicle Dynamics 3.3.7.3.
 * Every tyre model adds thrust() to its lateral force
 */
public final class Camber {

    private Camber() {
    }

    public static double lateralCamberSlip(double camberDeg) {
        return Math.sin(Math.toRadians(camberDeg)) / 3.0;
    }

    public static double linearityMargin(double camberDeg, TireSpec spec) {
        return spec.lateral().slipAtPeak() > 1.0e-9
                ? Math.abs(lateralCamberSlip(camberDeg)) / spec.lateral().slipAtPeak()
                : Double.POSITIVE_INFINITY;
    }

    public static double rillStiffness(TireSpec spec) {
        return spec.lateral().slipAtPeak() > 1.0e-9
                ? spec.lateral().initialStiffness() / (3.0 * spec.lateral().slipAtPeak())
                : 0.0;
    }

    public static double thrust(double camberDeg, double stiffness, double peakForce,
                                double currentLateralForce, double vLon, double fadeSpeed,
                                double sideSign) {
        if (camberDeg == 0.0 || stiffness <= 0.0 || peakForce <= 0.0) {
            return 0.0;
        }
        double speedFade = fadeSpeed > 0.0 ? Mth.clamp(Math.abs(vLon) / fadeSpeed, 0.0, 1.0) : 1.0;
        double latUse = Mth.clamp(Math.abs(currentLateralForce) / peakForce, 0.0, 1.0);
        double slipFade = 1.0 - latUse * latUse;
        double force = -Math.sin(Math.toRadians(camberDeg)) * stiffness * peakForce * sideSign
                * speedFade * slipFade;
        return Mth.clamp(force, -peakForce, peakForce);
    }
}
