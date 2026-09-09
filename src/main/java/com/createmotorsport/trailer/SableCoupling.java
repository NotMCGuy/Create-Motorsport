package com.createmotorsport.trailer;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.EnumSet;

public final class SableCoupling {
    private SableCoupling() {}
    public static ServerSubLevel body(EquipmentBlockEntity be) {
        var sub = Sable.HELPER.getContaining(be);
        return sub instanceof ServerSubLevel server && !server.isRemoved() ? server : null;
    }
    public static Quaterniond localOrientation(Direction facing) {
        return new Quaterniond().rotationY(Math.toRadians(switch (facing) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        }));
    }
    public static Vector3d point(EquipmentBlockEntity be) {
        var point = be.localAttachment();
        var sub = Sable.HELPER.getContaining(be);
        if (sub != null) point = sub.logicalPose().transformPosition(point);
        return new Vector3d(point.x, point.y, point.z);
    }
    public static double captureDistance(EquipmentBlockEntity a,EquipmentBlockEntity b) {
        EquipmentBlockEntity receiver=a.kind().provider()?b:a;
        EquipmentBlockEntity provider=a.kind().provider()?a:b;
        var sub=Sable.HELPER.getContaining(receiver);
        if(sub==null)return Double.POSITIVE_INFINITY;
        Vec3 attachment=receiver.localAttachment();
        Vec3 start=attachment,end=attachment;
        if(receiver.kind()==CouplingRules.Kind.KINGPIN) {
            start=attachment.add(0,-4.5/16,0);end=attachment.add(0,4.5/16,0);
        } else if(receiver.kind()==CouplingRules.Kind.COUPLING_HEAD) {
            var direction=localOrientation(receiver.facing()).transform(new Vector3d(0,0,3.0/16));
            start=attachment.add(-direction.x,0,-direction.z);end=attachment.add(direction.x,0,direction.z);
        }
        Vec3 worldStart=sub.logicalPose().transformPosition(start),worldEnd=sub.logicalPose().transformPosition(end);
        Vector3d first=new Vector3d(worldStart.x,worldStart.y,worldStart.z);
        Vector3d axis=new Vector3d(worldEnd.x-worldStart.x,worldEnd.y-worldStart.y,worldEnd.z-worldStart.z);
        Vector3d point=point(provider);
        double length=axis.lengthSquared();
        double t=length>1e-12?Math.clamp(new Vector3d(point).sub(first).dot(axis)/length,0,1):0;
        double distance=point.distance(first.add(axis.mul(t)));
        return Double.isFinite(distance)?distance:Double.POSITIVE_INFINITY;
    }
    public static Quaterniond orientation(EquipmentBlockEntity be) {
        var local = localOrientation(be.facing());
        var sub = Sable.HELPER.getContaining(be);
        return sub == null ? local : new Quaterniond(sub.logicalPose().orientation()).mul(local);
    }
    public static Vector3d velocity(ServerSubLevel body, Vector3d point) {
        if (body == null) return new Vector3d();
        var handle = RigidBodyHandle.of(body);
        if (handle == null) return new Vector3d(Double.NaN);
        return handle.getAngularVelocity(new Vector3d()).cross(new Vector3d(point).sub(body.logicalPose().position()))
                .add(handle.getLinearVelocity(new Vector3d()));
    }
    public static GenericConstraintConfiguration configuration(EquipmentBlockEntity a, EquipmentBlockEntity b) {
        Vec3 p = a.localAttachment(), q = b.localAttachment();
        return new GenericConstraintConfiguration(new Vector3d(p.x, p.y, p.z), new Vector3d(q.x, q.y, q.z),
                localOrientation(a.facing()), localOrientation(b.facing()),
                EnumSet.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z));
    }
    public static GenericConstraintHandle create(EquipmentBlockEntity a, EquipmentBlockEntity b) {
        var first = body(a); var second = body(b);
        if (first == null || second == null || first == second || first.getLevel() != second.getLevel()) return null;
        var container = SubLevelContainer.getContainer(first.getLevel());
        if (container == null) return null;
        var handle = container.physicsSystem().getPipeline().addConstraint(first, second, configuration(a, b));
        if (handle == null) return null;
        try {
            boolean fifth = a.kind().fifth();
            limit(handle, ConstraintJointAxis.ANGULAR_X, (fifth ? TrailerConfig.FIFTH_PITCH : TrailerConfig.BALL_PITCH).get());
            limit(handle, ConstraintJointAxis.ANGULAR_Y, (fifth ? TrailerConfig.FIFTH_YAW : TrailerConfig.BALL_YAW).get());
            limit(handle, ConstraintJointAxis.ANGULAR_Z, (fifth ? TrailerConfig.FIFTH_ROLL : TrailerConfig.BALL_ROLL).get());
            handle.setContactsEnabled(true);
            container.physicsSystem().getPipeline().wakeUp(first);
            container.physicsSystem().getPipeline().wakeUp(second);
            return handle;
        } catch (RuntimeException failure) {
            if (handle.isValid()) handle.remove();
            throw failure;
        }
    }
    private static void limit(GenericConstraintHandle handle, ConstraintJointAxis axis, double degrees) {
        double radians = Math.toRadians(degrees);
        handle.setLimit(axis, -radians, radians);
    }
}
