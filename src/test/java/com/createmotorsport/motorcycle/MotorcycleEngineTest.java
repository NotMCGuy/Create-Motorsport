package com.createmotorsport.motorcycle;

import com.createmotorsport.physics.spec.EngineSpec;
import com.createmotorsport.physics.spec.GearboxSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotorcycleEngineTest {
    @Test void currentBikeProfileHasUsableTorqueAndLimiter() {
        var spec = EngineSpec.MOTORCYCLE_I4;
        assertEquals(14000, spec.redlineRpm());
        assertTrue(spec.torqueAt(spec.idleRpm()) > 0);
        assertTrue(spec.torqueAt(spec.shiftUpRpm()) > 0);
        assertEquals(0, spec.torqueAt(spec.redlineRpm()));
        assertTrue(spec.engineInertia() < EngineSpec.TRUCK_DIESEL.engineInertia());
    }

    @Test void quickshiftRatiosKeepEngineInRunningRange() {
        var engine = EngineSpec.MOTORCYCLE_I4;
        var gearbox = GearboxSpec.MOTORCYCLE_QUICKSHIFT_6;
        assertEquals(6, gearbox.gearCount());
        assertTrue(gearbox.semiAuto());
        for (int gear = 1; gear < gearbox.gearCount(); gear++) {
            double rpm = engine.shiftUpRpm() * gearbox.forwardRatio(gear) / gearbox.forwardRatio(gear - 1);
            assertTrue(rpm > engine.idleRpm() && rpm < engine.redlineRpm());
            assertTrue(engine.torqueAt(rpm) > 0);
        }
    }
}
