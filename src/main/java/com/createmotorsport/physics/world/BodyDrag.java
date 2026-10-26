package com.createmotorsport.physics.world;

import com.createmotorsport.Config;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

public final class BodyDrag {

    private BodyDrag() {
    }

    public static void apply(ServerLevel level, ServerSubLevel subLevel, ForceTotal forceTotal, double dt) {
        MassData massData = subLevel.getMassTracker();
        if (massData == null || massData.isInvalid()) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null) {
            return;
        }
        Vector3d localVel = subLevel.logicalPose().orientation()
                .transformInverse(handle.getLinearVelocity(new Vector3d()));
        double speed = localVel.length();

        if (speed < 1.0e-4) {
            return;
        }

        double keep = Config.SABLE_DRAG_SCALE.getAsDouble();
        double drag = DimensionPhysicsData.getUniversalDrag(level);
        if (keep < 1.0 && drag > 0.0) {
            double mass = massData.getMass();
            forceTotal.applyLinearImpulse(new Vector3d(localVel).mul(mass * drag * (1.0 - keep) * dt));
        }

        double k = Config.AERO_DRAG_COEFFICIENT.getAsDouble()
                * com.createmotorsport.motorcycle.MotorcycleSuspensionBlockEntity.aerodynamicScale(subLevel);
        if (k <= 0.0) {
            return;
        }
        forceTotal.applyLinearImpulse(new Vector3d(localVel).mul(-k * speed * dt));
    }
}
