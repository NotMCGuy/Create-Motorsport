package com.createmotorsport.physics.differential;

import com.createmotorsport.physics.Differential;
import com.createmotorsport.physics.spec.DiffParams;
import com.createmotorsport.physics.spec.DifferentialSpec;

import net.minecraft.util.Mth;

/**
 * Limited slip differential,
 * ported from vdrift (driveline.h ClutchJoint::solve, and the clutch set up at cardynamics.cpp InitDriveline2)
 */
public final class ClutchLsdDiff implements Differential {

    public static final ClutchLsdDiff INSTANCE = new ClutchLsdDiff();

    private static final double FULL_SLIP = 12.0;

    private ClutchLsdDiff() {
    }

    @Override
    public void solve(Axle axle, DifferentialSpec spec, double massScale, boolean driftMode, double dt) {
        DiffParams.ClutchLsd p = spec.params(DiffParams.ClutchLsd.class);
        double antiSlip = Math.max(0.0, (driftMode ? p.driftAntiSlipTorque() : p.antiSlipTorque()) * massScale);
        double carried = axle.diffTorque * dt;
        double total = 0.0;
        if (antiSlip > 0.0) {
            double load = axle.inputTorque >= 0.0 ? axle.inputTorque : -axle.inputTorque * p.decelerationFactor();
            double limit = (antiSlip + Math.max(0.0, p.torqueSensitivity()) * load) * dt;
            double softness = FULL_SLIP / (antiSlip * dt);
            double inverse = 1.0 / axle.inertiaLeft + 1.0 / axle.inertiaRight;
            double velErr = axle.omegaLeft - axle.omegaRight;
            total = Mth.clamp((carried * inverse - velErr) / (inverse + softness), -limit, limit);
        }
        axle.omegaLeft += (total - carried) / axle.inertiaLeft;
        axle.omegaRight -= (total - carried) / axle.inertiaRight;
        axle.diffTorque = total / dt;
    }
}
