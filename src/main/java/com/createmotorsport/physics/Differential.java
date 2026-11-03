package com.createmotorsport.physics;

import com.createmotorsport.physics.spec.DifferentialSpec;

// see physics/differential
public interface Differential {

    final class Axle {
        public double omegaLeft;
        public double omegaRight;
        public double inertiaLeft;
        public double inertiaRight;

        public double diffTorque;
        public double inputTorque;
    }

    void solve(Axle axle, DifferentialSpec spec, double massScale, boolean driftMode, double dt);
}
