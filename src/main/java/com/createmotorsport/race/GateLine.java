package com.createmotorsport.race;

import net.minecraft.world.phys.Vec3;

// The lap gate calls this per car per gate each race tick
public final class GateLine {

    private GateLine() {
    }

    // A crossing is detected by watching which side of the segment a car is on, and looking for a sign flip between ticks
    public static boolean crossed(Vec3 a, Vec3 b, boolean reversed, double heightBand, Vec3 carPos, Entrant e,
                                  int gateIndex) {
        // which side of the A->B line the car is on, in the XZ plane
        double side = (b.x - a.x) * (carPos.z - a.z) - (b.z - a.z) * (carPos.x - a.x);
        double previous = e.lastSide[gateIndex];
        e.lastSide[gateIndex] = side;
        if (Double.isNaN(previous) || previous == 0.0 || Math.signum(side) == Math.signum(previous)) {
            return false;
        }
        if ((previous < 0.0) == reversed) {
            return false;
        }

        // Make sure car passed actually between the two blocks, and at gate height
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1.0e-6) {
            return false;
        }
        double t = ((carPos.x - a.x) * dx + (carPos.z - a.z) * dz) / lenSq;
        if (t < 0.0 || t > 1.0) {
            return false;
        }
        double gateY = (a.y + b.y) * 0.5;
        return Math.abs(carPos.y - gateY) <= heightBand;
    }
}
