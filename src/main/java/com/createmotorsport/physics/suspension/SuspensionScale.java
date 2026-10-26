package com.createmotorsport.physics.suspension;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SuspensionScale {
    private SuspensionScale() { }

    public static boolean valid(Vector3dc scale) {
        return Double.isFinite(scale.x()) && Double.isFinite(scale.y()) && Double.isFinite(scale.z())
                && scale.x() > 0 && scale.y() > 0 && scale.z() > 0;
    }

    public static double frequency(double frequency, double scale) {
        return frequency / Math.sqrt(scale);
    }

    public static Vector3d point(MassData mass, Vector3dc point, Vector3dc scale) {
        return new Vector3d(point).sub(mass.getCenterOfMass()).mul(scale).add(mass.getCenterOfMass());
    }

    public static double solve(UnsprungSolver.Input local, UnsprungSolver.State state, double scale) {
        var world = new UnsprungSolver.Input(local.rateMass(), local.rigidLength() * scale,
                local.hardpointVel() * scale, local.responseMass(), local.rollResponseMass(), local.dt(),
                local.restLength() * scale, local.maxTravel() * scale, local.maxDroop() * scale,
                local.unsprungMass(), local.gravity(), frequency(local.naturalFreqHz(), scale),
                local.dampingRatio(), local.damper(), local.tireRate() / scale, local.tireVerticalDamping(),
                scale < 1.0 ? Math.min(0.8, local.maxBodyModeOmegaDt()) : local.maxBodyModeOmegaDt(),
                local.maxSubStep());
        state.springLength *= scale;
        state.unsprungVel *= scale;
        double force = UnsprungSolver.solve(world, state);
        state.springLength /= scale;
        state.unsprungVel /= scale;
        state.tireDeflection /= scale;
        state.groundLength /= scale;
        return force;
    }
}
