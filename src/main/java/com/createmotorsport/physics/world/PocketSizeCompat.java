package com.createmotorsport.physics.world;

import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.reflect.Method;

public final class PocketSizeCompat {
    private static final Method INTERNAL_FORCES = findInternalForces();
    private PocketSizeCompat() { }

    private static Method findInternalForces() {
        try {
            return Class.forName("com.misterblusky9.pym.api.Pym")
                    .getMethod("internalForces", ServerSubLevel.class);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException incompatible) {
            throw new IllegalStateException("Unsupported Pym internal-forces API", incompatible);
        }
    }

    public static boolean available() { return INTERNAL_FORCES != null; }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    public static Scope internalForces(ServerSubLevel body) {
        if (!available()) return () -> { };
        try {
            AutoCloseable scope = (AutoCloseable) INTERNAL_FORCES.invoke(null, body);
            return () -> {
                try { scope.close(); }
                catch (Exception failure) { throw new IllegalStateException("Cannot close Pym force context", failure); }
            };
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot enter Pym force context", failure);
        }
    }

    public static void applyForces(ServerSubLevel body, RigidBodyHandle handle, ForceTotal forces) {
        if (available()) {
            double scale = body.logicalPose().scale().y;
            forces.getLocalTorque().mul(1.0 / (scale * scale));
        }
        try (Scope ignored = internalForces(body)) {
            handle.applyForcesAndReset(forces);
        }
    }
}
