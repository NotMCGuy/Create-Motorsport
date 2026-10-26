package com.createmotorsport.physics.spec;

public record StarterSpec(
        double stallTorque,
        double freeRpm
) {

    public static final StarterSpec DEFAULT = new StarterSpec(0.0, 0.0);
}
