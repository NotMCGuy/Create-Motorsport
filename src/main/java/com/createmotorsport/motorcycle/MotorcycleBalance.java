package com.createmotorsport.motorcycle;

import com.createmotorsport.block.entity.SuspensionBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

public final class MotorcycleBalance {
    private double target;

    public void step(MotorcycleSuspensionBlockEntity fork, ServerSubLevel body, RigidBodyHandle handle, double dt) {
        if (!MotorcycleBalanceConfig.ENABLED.get() || !(dt > 0 && dt <= 0.1) || !Double.isFinite(dt)) { target = 0; return; }
        MotorcycleSuspensionBlockEntity rear = null;
        int forks = 0;
        int rears = 0;
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof SuspensionBlockEntity suspension) || suspension.isRemoved()) continue;
            if (!(suspension instanceof MotorcycleSuspensionBlockEntity bike)) { target = 0; return; }
            if (bike.isFrontAxle()) forks++;
            else { rears++; rear = bike; }
        }
        if (forks != 1 || rears != 1 || rear == null || rear.getFacing() != fork.getFacing()
                || !fork.hasAnyTire() || !rear.hasAnyTire()) { target = 0; return; }
        boolean grounded = fork.getTelemetry(SuspensionBlockEntity.WheelSide.LEFT).grounded()
                || rear.getTelemetry(SuspensionBlockEntity.WheelSide.LEFT).grounded();
        if (!grounded) { target = 0; return; }

        var facing = fork.getFacing();
        Vector3d localForward = new Vector3d(facing.getStepX(), 0, facing.getStepZ());
        Vector3d forward = body.logicalPose().transformNormal(new Vector3d(localForward)).normalize();
        Vector3d up = body.logicalPose().transformNormal(new Vector3d(0, 1, 0)).normalize();
        if (up.y < Math.cos(Math.toRadians(75)) || Math.abs(forward.y) > 0.85) { target = 0; return; }
        Vector3d right = new Vector3d(forward).cross(0, 1, 0).normalize();
        double roll = Math.atan2(up.dot(right), up.y);
        double speed = handle.getLinearVelocity(new Vector3d()).dot(forward);
        double wheelbase = Math.abs((fork.axleCentre().x - rear.axleCentre().x) * localForward.x
                + (fork.axleCentre().z - rear.axleCentre().z) * localForward.z);
        if (wheelbase < 0.5) { target = 0; return; }
        wheelbase *= body.logicalPose().scale().get(facing.getAxis() == net.minecraft.core.Direction.Axis.X ? 0 : 2);
        double steer = fork.wheelSteerAngle(fork.getSteerAngleRad(), SuspensionBlockEntity.WheelSide.LEFT);
        double limit = Math.min(Math.toRadians(MotorcycleBalanceConfig.MAX_LEAN.get()), Math.atan(MotorcycleBalanceConfig.CORNERING_G.get()));
        double requested = desiredLean(speed, steer, wheelbase, limit);
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        boolean handling = MotorcycleBalanceConfig.HANDLING.get();
        if (handling) requested = MotorcycleHandling.achievedLean(speed,angular.y,requested,limit);
        target = approach(target, requested, Math.toRadians(45) * dt);
        double rate = angular.dot(forward);
        Vector3d localAcceleration = new Vector3d(localForward).mul(angularAcceleration(target-roll,rate,dt));
        if (handling) {
            var frontContact = fork.getTelemetry(SuspensionBlockEntity.WheelSide.LEFT);
            var rearContact = rear.getTelemetry(SuspensionBlockEntity.WheelSide.LEFT);
            double contactGain = MotorcycleHandling.headingContactGain(frontContact.grounded(),rearContact.grounded());
            if (contactGain > 0) {
                double grip = Math.clamp(frontContact.grounded()
                        ? Math.min(frontContact.surfaceMu(),rearContact.surfaceMu()) : rearContact.surfaceMu(),0,1);
                double yawTarget = MotorcycleHandling.yawRate(speed,steer,wheelbase,MotorcycleBalanceConfig.CORNERING_G.get()*Math.max(.1,grip));
                double yawAcceleration = MotorcycleHandling.yawAcceleration(yawTarget,angular.y,dt)*grip*contactGain;
                Vector3d localUp = body.logicalPose().transformNormalInverse(new Vector3d(0,1,0)).normalize();
                localAcceleration.fma(yawAcceleration,localUp);
            }
            Vector3d localRight = new Vector3d(getRightX(facing),0,getRightZ(facing));
            Vector3d bodyRight = body.logicalPose().transformNormal(new Vector3d(localRight)).normalize();
            double pitch = Math.atan2(forward.y,Math.hypot(forward.x,forward.z));
            double pitchRate=angular.dot(bodyRight);
            double braking=Math.max(fork.brakingAmount(),rear.brakingAmount());
            double pitchCorrection=braking>.01
                    ? MotorcycleHandling.brakingPitchAcceleration(pitch,pitchRate,braking)
                    : MotorcycleHandling.pitchAcceleration(pitch,pitchRate);
            localAcceleration.fma(pitchCorrection,localRight);
        }
        Vector3d impulse = MotorcycleHandling.angularImpulse(body.getMassTracker().getInertiaTensor(),localAcceleration,dt,new Vector3d());
        if (impulse.isFinite()) {
            try (var ignored = com.createmotorsport.physics.world.PocketSizeCompat.internalForces(body)) {
                handle.applyAngularImpulse(impulse);
            }
        }
    }

    private static int getRightX(net.minecraft.core.Direction facing) { return facing.getClockWise().getStepX(); }
    private static int getRightZ(net.minecraft.core.Direction facing) { return facing.getClockWise().getStepZ(); }

    public static double desiredLean(double speed, double steer, double wheelbase, double limit) {
        if (!Double.isFinite(speed) || !Double.isFinite(steer) || !Double.isFinite(wheelbase) || wheelbase <= 0
                || !Double.isFinite(limit) || limit <= 0) return 0;
        double fade = Math.clamp((Math.abs(speed) - 0.5) / 2.0, 0, 1);
        double angle = -Math.atan(speed * speed * Math.tan(Math.clamp(steer, -1, 1)) / (9.81 * wheelbase));
        return Math.clamp(angle, -limit, limit) * fade;
    }

    public static double angularAcceleration(double error, double rate, double dt) {
        if (!Double.isFinite(error) || !Double.isFinite(rate) || !Double.isFinite(dt) || dt <= 0 || dt > 0.1) return 0;
        double stiffness = 64, damping = 18;
        return Math.clamp((stiffness * error - (damping + stiffness * dt) * rate)
                / (1 + damping * dt + stiffness * dt * dt), -12, 12);
    }

    private static double approach(double value, double target, double step) {
        return value + Math.clamp(target - value, -step, step);
    }
}
