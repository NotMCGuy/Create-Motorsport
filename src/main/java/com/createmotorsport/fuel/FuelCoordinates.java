package com.createmotorsport.fuel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class FuelCoordinates {
    private FuelCoordinates() {}

    public static Vec3 attachment(Direction facing) {
        return rotate(new Vec3(5.0 / 16, 16.0 / 16, 5.5 / 16), facing);
    }

    public static Vec3 rotate(Vec3 point, Direction facing) {
        double x = point.x - 0.5;
        double z = point.z - 0.5;
        return switch (facing) {
            case EAST -> new Vec3(0.5 - z, point.y, 0.5 + x);
            case SOUTH -> new Vec3(0.5 - x, point.y, 0.5 - z);
            case WEST -> new Vec3(0.5 + z, point.y, 0.5 - x);
            default -> point;
        };
    }

    public static Vec3 transform(Vec3 local, Pose3dc pose) {
        return pose == null ? local : pose.transformPosition(local);
    }

    public static Vec3 global(Level level, Vec3 local) {
        var sub = Sable.HELPER.getContaining(level, local);
        return transform(local, sub == null ? null : sub.logicalPose());
    }

    public static Vec3 attachment(Level level, BlockPos pos, Direction facing) {
        return global(level, Vec3.atLowerCornerOf(pos).add(attachment(facing)));
    }

    public static boolean inRange(Vec3 a, Vec3 b, double range) {
        return Double.isFinite(a.distanceToSqr(b)) && a.distanceToSqr(b) <= range * range;
    }
}
