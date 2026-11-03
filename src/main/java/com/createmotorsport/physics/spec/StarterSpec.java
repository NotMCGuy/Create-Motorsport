package com.createmotorsport.physics.spec;

public record StarterSpec(
        double stallTorque, // 0 is 2.5x breakaway friction of the engine
        double freeRpm // 0 is engine idle
) {

    public static final StarterSpec DEFAULT = new StarterSpec(0.0, 0.0);
}
