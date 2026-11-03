package com.createmotorsport.physics;

import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.physics.spec.SurfaceSpec;

public interface Tire {

    record Input(double vLon, double vLat, double normalForce, double effectiveMu, double gripForce,
                 double radius, double dt, boolean powered, double driveTorque, double driveStiffness,
                 double driveMin, double driveMax, double brakeTorque, double absOmega,
                 double wheelInertia, double invSideMass, double designLoad,
                 double camberDeg, double camberStiffness, double camberFadeSpeed, double sideSign,
                 TireSpec spec, SurfaceSpec surface) {
    }

    final class Contact {
        public double omega;
        public double deflAlpha; // Fiala's relaxation deflection
        public double prevLongForce;
        public double curveLongForce; // steady curve's force on this slip, before relaxation
        public double latBristle;// TMeasy's Dahl standstill bristle
        public double lonBristle; // and the longitudinal
        public double latRelax; // Rill 3.4.2 lateral carcass deflection y_e

        public double telemPeakForce;
        public double telemSlipCombined;
        public double telemSlipAtPeak;
        public double telemCurveForce;
        public double telemSlipRatio;
        public double telemSlipAngleRad;
        public boolean telemModelSlip;
    }

    record Forces(double forward, double side) { // impulses for this substep, N*s
    }

    Forces solve(Input in, Contact contact);


    default double peakSlipAngle(TireSpec spec, double load, double designLoad, SurfaceSpec surface, double mu) {
        return Double.NaN;
    }
}
