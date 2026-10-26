package com.createmotorsport.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrivingInputResponseTest {
    @Test void brakeBitesFasterAndPedalsReleaseImmediately() {
        assertEquals(.3f, DrivingInputResponse.pedal(0, 1, .3f, false), 1e-6);
        assertEquals(.6f, DrivingInputResponse.pedal(0, 1, .3f, true), 1e-6);
        assertEquals(0, DrivingInputResponse.pedal(1, 0, .01f, false));
        assertEquals(0, DrivingInputResponse.pedal(1, 0, .01f, true));
        assertEquals(1, DrivingInputResponse.pedal(.9f, 1, .3f, true));
    }

    @Test void steeringUnwindsAndReversesWithoutOvershoot() {
        assertEquals(.3f, DrivingInputResponse.steering(0, 1, .3f), 1e-6);
        assertEquals(.4f, DrivingInputResponse.steering(1, 0, .3f), 1e-6);
        assertEquals(0, DrivingInputResponse.steering(.4f, 0, .3f));
        float steer = 1;
        for (int i=0;i<5;i++) steer=DrivingInputResponse.steering(steer, -1, .3f);
        assertEquals(-1, steer);
        assertEquals(1, DrivingInputResponse.steering(.9f, 1, .3f));
    }
}
