package com.createmotorsport.physics.spec;

//   F = c * v                              for v <= knee
//   F = c * knee + c * slope * (v - knee)   for v > knee
public record DamperSpec(double kneeVelocity, double blowOffSlope) {

    public static final DamperSpec LINEAR = new DamperSpec(0.0, 1.0);
    public static final DamperSpec F1_DIGRESSIVE = new DamperSpec(0.08, 0.30);
}
