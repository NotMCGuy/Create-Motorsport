package com.createmotorsport.motorcycle;

import org.junit.jupiter.api.Test;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;

class MotorcycleHandlingTest {
    @Test void slowEntryFastExitKeepsTurningThenStraightens() {
        for (double dt : new double[]{.005, .02, .05}) for (double direction : new double[]{-1, 1}) {
            double yaw = 0;
            for (double time = 0; time < 6; time += dt) {
                double speed = 2 + 16 * Math.min(time / 4, 1);
                double input = direction * Math.toRadians(32) * Math.clamp((6-time)/2,0,1);
                double steer = MotorcycleHandling.steering(input,speed,3,.65);
                double target = MotorcycleHandling.yawRate(speed,steer,3,.65);
                double contact = MotorcycleHandling.headingContactGain(time < 2, true);
                yaw += MotorcycleHandling.yawAcceleration(target,yaw,dt)*contact*dt;
                assertTrue(yaw*direction > 0, "acceleration must preserve the chosen turn direction");
                assertTrue(speed*speed*Math.abs(Math.tan(steer))/3 <= 9.81*.65+1e-8);
            }
            for (double time = 0; time < 1.5; time += dt)
                yaw += MotorcycleHandling.yawAcceleration(0,yaw,dt)*dt;
            assertEquals(0,yaw,.001,"releasing steering should straighten the exit");
        }
    }
    @Test void headingAssistRequiresGroundSupport() {
        assertEquals(1,MotorcycleHandling.headingContactGain(true,true));
        assertEquals(.6,MotorcycleHandling.headingContactGain(false,true));
        assertEquals(0,MotorcycleHandling.headingContactGain(false,false));
        assertEquals(0,MotorcycleHandling.headingContactGain(true,false));
    }
    @Test void highSpeedSteeringStaysInsideCorneringBudgetInBothDirections() {
        for(double speed:new double[]{0,2,10,30,-30}) {
            double steer=MotorcycleHandling.steering(.6,speed,3,.65);
            assertTrue(steer>0 && steer<=Math.toRadians(50));
            assertEquals(-steer,MotorcycleHandling.steering(-.6,speed,3,.65),1e-10);
            assertTrue(speed*speed*Math.tan(steer)/3<=9.81*.65+1e-8);
        }
        assertEquals(0,MotorcycleHandling.steering(Double.NaN,2,3,.65));
    }
    @Test void leanWaitsForAchievedTurnAndReturnsUpright() {
        double requested=-.6;
        assertEquals(-.15,MotorcycleHandling.achievedLean(12,0,requested,.7),1e-10);
        assertTrue(MotorcycleHandling.achievedLean(12,.4,requested,.7)<-.15);
        assertEquals(0,MotorcycleHandling.achievedLean(0,0,0,.7));
        assertEquals(0,MotorcycleHandling.achievedLean(12,.4,requested,0));
        assertEquals(-MotorcycleHandling.yawRate(8,.2,3,.65),MotorcycleHandling.yawRate(-8,.2,3,.65),1e-10);
    }
    @Test void correctionDoesNotCoupleRollIntoPitchOnAnAsymmetricBike() {
        Matrix3d inertia=new Matrix3d(8,1,2, 1,12,3, 2,3,6);
        Vector3d acceleration=new Vector3d(0,0,-8);
        Vector3d impulse=MotorcycleHandling.angularImpulse(inertia,acceleration,.02,new Vector3d());
        Vector3d change=new Matrix3d(inertia).invert().transform(impulse);
        assertEquals(0,change.x,1e-10);assertEquals(0,change.y,1e-10);assertEquals(-.16,change.z,1e-10);
        assertEquals(-8,acceleration.z);
    }
    @Test void brakingBuildsProgressively() {
        double pressure=MotorcycleHandling.brakeResponse(0,1,.05);
        assertEquals(.2,pressure,1e-10);
        assertEquals(0,MotorcycleHandling.brakeResponse(pressure,0,.05));
    }
    @Test void walkingSpeedTurnsUseTighterLockInBothDirections() {
        for (double speed : new double[]{-2, 2}) for (double direction : new double[]{-1, 1}) {
            double steer = MotorcycleHandling.steering(direction * Math.toRadians(32), speed, 3, .65);
            double radius = Math.abs(speed / MotorcycleHandling.yawRate(speed, steer, 3, .65));
            assertTrue(radius < 2.6, "full-lock walking-speed radius should be about 2.52 blocks");
        }
        assertEquals(0, MotorcycleHandling.steering(0, 2, 3, .65));
        assertEquals(0, MotorcycleHandling.yawRate(0, Math.toRadians(50), 3, .65));
    }
    private double brake(double omega,double speed,double load,double grip,double bank,double pitch,double rate) {
        return MotorcycleHandling.brakeTorque(2000,.5,2,omega,0,0,speed,load,grip,bank,pitch,rate,.02);
    }
    @Test void brakesRespectContactLoadAndReleaseFullyOnLockup() {
        double rolling=brake(24,12,500,500,0,0,0);
        assertTrue(rolling>0 && rolling<=500*.55*.5);
        assertEquals(0,brake(0,12,500,500,0,0,0));
        assertEquals(0,brake(24,12,0,500,0,0,0));
        assertTrue(brake(24,12,50,500,0,0,0)<rolling);
        assertTrue(brake(24,12,500,50,0,0,0)<rolling);
        assertTrue(brake(24,12,500,500,.5,0,0)<rolling);
        assertTrue(brake(24,12,500,500,0,-.2,-.5)<rolling);
        assertTrue(brake(.6,.3,500,500,0,0,0)>0);
        assertEquals(rolling,brake(-24,-12,500,500,0,0,0),1e-10);
    }
    @Test void fullBrakeCannotLockRollingWheelInOnePhysicsStep() {
        for(double dt:new double[]{.005,.02,.05}) for(double direction:new double[]{-1,1})
            for(double drive:new double[]{-20,0,80}) for(double slip:new double[]{0,.04,.079}) {
                double speed=12*direction,omega=speed/.5*(1-slip);
                double previousForce=-200*direction,driveTorque=drive*direction;
                double torque=MotorcycleHandling.brakeTorque(2000,.5,2,omega,driveTorque,
                        previousForce,speed,500,500,0,0,0,dt);
                double next=com.createmotorsport.physics.tire.TireModel.integrateSpin(omega,.5,2,
                        driveTorque,torque,previousForce,speed,dt);
                assertTrue(next*direction>=Math.abs(speed/.5)*.92-1e-9,
                        "braking exceeded the permitted slip in one substep");
                assertTrue(torque>=0 && torque<=137.5);
            }
    }
    @Test void brakingPitchGuardRespondsBeforeTheBikeTips() {
        assertTrue(MotorcycleHandling.brakingPitchAcceleration(-.02,-.5,1)>0);
        assertTrue(MotorcycleHandling.brakingPitchAcceleration(.02,.5,1)<0);
        assertEquals(0,MotorcycleHandling.brakingPitchAcceleration(.2,1,0));
        assertTrue(Math.abs(MotorcycleHandling.brakingPitchAcceleration(-1,-100,1))<=8);
    }
    @Test void sustainedBrakingSlowsTheBikeWithoutSustainedWheelLock() {
        var tire=new com.createmotorsport.physics.spec.TireSpec(1,1,1,0,
                new com.createmotorsport.physics.spec.ModelParams.Pacejka(12,.8,.9),
                com.createmotorsport.physics.spec.SurfaceResponse.ByCategory.NEUTRAL,
                com.createmotorsport.physics.spec.ThermalSpec.DEFAULT,0);
        for(double dt:new double[]{.01,.02,.05}) for(double mu:new double[]{.4,1}) {
            double speed=12,omega=24,pressure=0;
            for(int step=0;step<20/dt && speed>.5;step++) {
                pressure=MotorcycleHandling.brakeResponse(pressure,1,dt);
                double force=com.createmotorsport.physics.tire.TireModel.longitudinalForce(245,mu,omega*.5,speed,tire);
                double torque=MotorcycleHandling.brakeTorque(2000*pressure,.5,2,omega,0,
                        force,speed,245,245*mu,.25,0,0,dt);
                omega=com.createmotorsport.physics.tire.TireModel.integrateSpin(omega,.5,2,0,torque,force,speed,dt);
                if(speed>2)assertTrue(omega*.5>=speed*.92-1e-8);
                speed=Math.max(0,speed+force/25*dt);
                assertTrue(Double.isFinite(speed+omega));
            }
            assertTrue(speed<=.5,"brakes must still bring the bike to walking speed");
        }
    }
    @Test void smallerHighSpeedInputsRemainDistinct() {
        double half=MotorcycleHandling.steering(.25,20,3,.65),full=MotorcycleHandling.steering(.5,20,3,.65);
        assertTrue(half>0&&half<full);
    }
    @Test void headingAndPitchGuardsStayBounded() {
        assertEquals(0,MotorcycleHandling.pitchAcceleration(Math.toRadians(8),1));
        assertTrue(MotorcycleHandling.pitchAcceleration(Math.toRadians(30),1)<0);
        assertTrue(MotorcycleHandling.pitchAcceleration(Math.toRadians(-30),-1)>0);
        assertEquals(3,Math.abs(MotorcycleHandling.pitchAcceleration(1,100)));
        for(double dt:new double[]{.005,.02,.05}) {
            double rate=0;
            for(int i=0;i<400;i++)rate+=MotorcycleHandling.yawAcceleration(.4,rate,dt)*dt;
            assertEquals(.4,rate,.001);
        }
        assertEquals(0,MotorcycleHandling.yawAcceleration(Double.NaN,0,.02));
    }
}
