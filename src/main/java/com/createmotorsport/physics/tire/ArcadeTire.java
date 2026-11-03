package com.createmotorsport.physics.tire;

import com.createmotorsport.Config;
import com.createmotorsport.physics.Tire;

/**
 * TYRE MODEL 1, "arcade"
 *
 *  Based on Bullet's raycast vehicle controllers (which is where Rapier's comes from)
 *
 * Corner calls solve() each substep through TireModels
 */
public final class ArcadeTire implements Tire {

    public static final ArcadeTire INSTANCE = new ArcadeTire();

    private ArcadeTire() {
    }

    @Override
    public Forces solve(Input in, Contact c) {
        // The wheel is held by the curve at the slip it ends on, and the car is pushed by the same force, as in TmeasyTire
        c.omega = TireModel.stepSpin(c.omega, in.radius(), in.wheelInertia(), in.driveTorque(),
                in.driveStiffness(), in.driveMin(), in.driveMax(), in.brakeTorque(), in.absOmega(),
                in.vLon(), in.dt(),
                w -> TireModel.longitudinalForce(in.normalForce(), in.effectiveMu(), w * in.radius(), in.vLon(),
                        in.spec()));
        double tireForce = TireModel.longitudinalForce(in.normalForce(), in.effectiveMu(),
                c.omega * in.radius(), in.vLon(), in.spec());
        // Speed-dreams runs FLOAT_RELAXATION2 on the tire force to relax longitudinal force towards its target, to damp oscillation
        c.curveLongForce = tireForce;
        tireForce = c.prevLongForce + (tireForce - c.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
        c.prevLongForce = tireForce;
        if (!in.powered()) {
            tireForce -= TireModel.rollingResistance(in.spec().rollingResistance(), in.normalForce(), in.vLon())
                    * in.surface().rollingResistanceScale();
        }
        double forwardImpulse = tireForce * in.dt();

        // cancel a fraction of the lateral velocity per substep same way Bullet does
        double sideImpulse = in.invSideMass() > 1.0e-9
                ? -in.vLat() * (1.0 / in.invSideMass()) * Config.LATERAL_GRIP_FRACTION.getAsDouble()
                : 0.0;

        sideImpulse += Camber.thrust(in.camberDeg(), in.camberStiffness(), in.gripForce(),
                sideImpulse / in.dt(), in.vLon(), in.camberFadeSpeed(), in.sideSign()) * in.dt();

        double maxImpulse = in.normalForce() * in.effectiveMu() * in.spec().grip() * in.dt();
        double ellipseScale = TireModel.frictionEllipseScale(forwardImpulse, sideImpulse, maxImpulse);
        forwardImpulse *= ellipseScale;
        sideImpulse *= ellipseScale;

        return new Forces(forwardImpulse, sideImpulse);
    }
}
