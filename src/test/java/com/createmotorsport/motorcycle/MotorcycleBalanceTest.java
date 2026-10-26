package com.createmotorsport.motorcycle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotorcycleBalanceTest {
    @Test void standsUprightAtRestAndWalkingSpeed() {
        assertEquals(0, MotorcycleBalance.desiredLean(0, .5, 3, .7), 1e-10);
        assertEquals(0, MotorcycleBalance.desiredLean(.4, .5, 3, .7), 1e-10);
        assertEquals(0, MotorcycleBalance.desiredLean(20, 0, 3, .7), 1e-10);
    }
    @Test void leansIntoBothTurnsAndCapsHighSpeedLean() {
        double left = MotorcycleBalance.desiredLean(6, .2, 3, .7);
        assertTrue(left < 0);
        assertEquals(-left, MotorcycleBalance.desiredLean(6, -.2, 3, .7), 1e-10);
        assertEquals(left, MotorcycleBalance.desiredLean(-6, .2, 3, .7), 1e-10);
        assertEquals(-.7, MotorcycleBalance.desiredLean(100, .6, 3, .7), 1e-10);
        assertTrue(Math.abs(left) > Math.abs(MotorcycleBalance.desiredLean(3, .2, 3, .7)));
    }
    @Test void dampingSettlesPerturbedBikeAtDifferentSubsteps() {
        for (double dt : new double[]{.005, .02, .05}) {
            double roll = .35, rate = 1;
            for (double t = 0; t < 4; t += dt) {
                rate += MotorcycleBalance.angularAcceleration(-roll, rate, dt) * dt;
                roll += rate * dt;
            }
            assertEquals(0, roll, .001);
            assertEquals(0, rate, .001);
        }
    }
    @Test void rejectsInvalidPhysicsAndGeometry() {
        assertEquals(0, MotorcycleBalance.desiredLean(Double.NaN, .5, 3, .7));
        assertEquals(0, MotorcycleBalance.desiredLean(10, .5, 0, .7));
        assertEquals(0, MotorcycleBalance.angularAcceleration(1, 0, 0));
        assertEquals(0, MotorcycleBalance.angularAcceleration(1, Double.NaN, .02));
    }
}
