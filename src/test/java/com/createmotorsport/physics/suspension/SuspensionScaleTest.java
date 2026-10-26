package com.createmotorsport.physics.suspension;

import com.createmotorsport.physics.spec.DamperSpec;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SuspensionScaleTest {
    private static final double[] SCALES = {1.0 / 16, .125, .25, .5, 1, 2};
    private static final double MASS = 100, GRAVITY = 11, REST = .21875, HZ = 3.5, DT = 1.0 / 120;

    private static UnsprungSolver.Input input(double height, double velocity) {
        return new UnsprungSolver.Input(MASS, height, velocity, MASS, Double.POSITIVE_INFINITY,
                DT, REST, .2, .1, 5, GRAVITY, HZ, .7, DamperSpec.LINEAR,
                50000, .8, 1.8, .004);
    }

    @Test void fullSizePreservesExistingSolver() {
        var old = new UnsprungSolver.State();
        var scaled = new UnsprungSolver.State();
        old.springLength = scaled.springLength = .19;
        old.unsprungVel = scaled.unsprungVel = -.1;
        double expected = UnsprungSolver.solve(input(.17, -.2), old);
        assertEquals(expected, SuspensionScale.solve(input(.17, -.2), scaled, 1), 1e-10);
        assertEquals(old.springLength, scaled.springLength, 1e-12);
        assertEquals(old.unsprungVel, scaled.unsprungVel, 1e-12);
    }

    @Test void compliantSuspensionSettlesWithoutLaunchingAtSmallScales() {
        for (double scale : SCALES) {
            var wheel = new UnsprungSolver.State();
            wheel.springLength = REST;
            double height = (REST - .02) * scale;
            double velocity = -.2 * scale;
            double peakForce = 0;
            for (int step = 0; step < 1200; step++) {
                double force = SuspensionScale.solve(input(height / scale, velocity / scale), wheel, scale);
                assertTrue(Double.isFinite(force), "non-finite force at scale " + scale);
                peakForce = Math.max(peakForce, Math.abs(force));
                velocity += (force / MASS - GRAVITY) * DT;
                height += velocity * DT;
                assertTrue(height / scale < REST + .15, "launched at scale " + scale);
                assertTrue(height > 0, "collapsed at scale " + scale);
            }
            assertTrue(peakForce < MASS * GRAVITY * 12, "runaway force at scale " + scale);
            assertEquals(0, velocity, .02 * scale, "not settled at scale " + scale);
            double expectedSag = GRAVITY / Math.pow(2 * Math.PI * HZ, 2);
            assertEquals(expectedSag, REST - wheel.springLength, .004, "ride height at scale " + scale);
        }
    }

    @Test void rigidSuspensionRetainsRelativeSagAndDampsAfterCompression() {
        for (double scale : SCALES) {
            double height = (REST - .06) * scale, velocity = 0;
            double frequency = SuspensionScale.frequency(HZ, scale);
            for (int step = 0; step < 1200; step++) {
                double force = SpringDamper.suspensionForce(MASS, frequency, .7,
                        Math.max(0, REST * scale - height), velocity, DamperSpec.LINEAR, MASS, DT);
                velocity += (force / MASS - GRAVITY) * DT;
                height += velocity * DT;
                assertTrue(height / scale < REST + .15, "launched at scale " + scale);
                assertTrue(height > 0, "collapsed at scale " + scale);
            }
            assertEquals(0, velocity, .001, "not settled at scale " + scale);
            assertEquals(GRAVITY / Math.pow(2 * Math.PI * HZ, 2), REST - height / scale, .001);
        }
    }

    @Test void impulseAndResponseMassUseTheSameScaledLeverArm() {
        MassData mass = new MassData() {
            public double getMass() { return MASS; }
            public double getInverseMass() { return 1 / MASS; }
            public Matrix3dc getInertiaTensor() { return new Matrix3d().scale(200); }
            public Matrix3dc getInverseInertiaTensor() { return new Matrix3d().scale(1.0 / 200); }
            public Vector3dc getCenterOfMass() { return new Vector3d(1000, 64, -800); }
        };
        Vector3d local = new Vector3d(mass.getCenterOfMass()).add(2, 0, 0);
        for (double scale : SCALES) {
            Vector3d point = SuspensionScale.point(mass, local, new Vector3d(scale));
            ForceTotal forces = new ForceTotal();
            forces.applyImpulseAtPoint(mass, point, new Vector3d(0, 10, 0));
            assertEquals(10, forces.getLocalForce().y(), 1e-12);
            assertEquals(20 * scale, forces.getLocalTorque().z(), 1e-10);
            assertEquals(1 / MASS + Math.pow(2 * scale, 2) / 200,
                    mass.getInverseNormalMass(point, new Vector3d(0, 1, 0)), 1e-10);
        }
    }

    @Test void localVelocityConversionMatchesMeasuredMotion() {
        for (double scale : SCALES) {
            Pose3d pose = new Pose3d();
            pose.scale().set(scale);
            pose.orientation().rotateZ(.4);
            Vector3d worldVelocity = pose.transformNormal(new Vector3d(0, -.3, 0));
            assertEquals(-.3, pose.transformNormalInverse(worldVelocity).y(), 1e-12);
        }
        assertFalse(SuspensionScale.valid(new Vector3d(0)));
        assertFalse(SuspensionScale.valid(new Vector3d(Double.NaN, 1, 1)));
        assertFalse(SuspensionScale.valid(new Vector3d(-1, 1, 1)));
    }
}
