package com.createmotorsport.physics.world;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Arrays;

public final class TerrainCast {

    private static final double FOOTPRINT_FRACTION = 0.95;
    private static final int MAX_FOOTPRINT_SAMPLES = 8;

    private TerrainCast() {
    }

    public record Result(boolean hit, double rigid, double castLift, @NotNull Direction normal,
                         @Nullable SubLevel hitSubLevel, @Nullable BlockPos hitBlock) {
    }

    public static Result cast(Level level, @Nullable SubLevel ignored, Vec3 wheelCenter, Vector3d forward,
                              Vector3d axle, Pose3dc pose, double radius, double width) {
        double half = radius * FOOTPRINT_FRACTION;
        double[] offsets = new double[MAX_FOOTPRINT_SAMPLES];
        int sampleCount = footprintOffsets(wheelCenter, forward, pose, half, offsets);
        double[] lanes = new double[MAX_FOOTPRINT_SAMPLES];
        int laneCount = width > 0.0
                ? footprintOffsets(wheelCenter, axle, pose, 0.5 * width * FOOTPRINT_FRACTION, lanes) : 0;
        if (laneCount == 0) {
            lanes[0] = 0.0;
            laneCount = 1;
        }
        int centreLane = 0;
        for (int j = 1; j < laneCount; j++) {
            if (Math.abs(lanes[j]) < Math.abs(lanes[centreLane])) {
                centreLane = j;
            }
        }

        boolean hit = false;
        double minRigid = 0.0;
        double centerDist = 0.0;
        double centerX = Double.MAX_VALUE;
        Direction minNormal = Direction.UP;
        SubLevel minHitSubLevel = null;
        BlockPos minHitBlock = null;

        Vec3 forwardVec = JOMLConversion.toMojang(forward);
        Vec3 axleVec = JOMLConversion.toMojang(axle);
        for (int k = 0; k < laneCount * sampleCount; k++) {
            int lane = k / sampleCount;
            double x = offsets[k % sampleCount];
            Vec3 origin = wheelCenter.add(forwardVec.scale(x)).add(axleVec.scale(lanes[lane]));

            ClipContext clipContext = new ClipContext(origin, origin.subtract(0.0, 5.0, 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
            ((ClipContextExtension) clipContext).sable$setIgnoredSubLevel(ignored);
            BlockHitResult clipResult = level.clip(clipContext);

            if (clipResult.getType() == HitResult.Type.MISS) {
                continue;
            }

            SubLevel hitSubLevel = Sable.HELPER.getContaining(level, clipResult.getLocation());
            Vec3 localHitPos = pose.transformPositionInverse(hitSubLevel == null
                    ? clipResult.getLocation()
                    : hitSubLevel.logicalPose().transformPosition(clipResult.getLocation()));

            if (localHitPos.y > wheelCenter.y || origin.distanceTo(localHitPos) < 0.05) {
                continue;
            }

            double dist = wheelCenter.y - localHitPos.y;
            if (dist <= 1.0e-5) {
                continue;
            }

            Direction dir = clipResult.getDirection();
            Vector3d hitNormal = new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            if (hitSubLevel != null) {
                hitSubLevel.logicalPose().transformNormal(hitNormal);
            }
            pose.transformNormalInverse(hitNormal).normalize();
            if (hitNormal.dot(0.0, 1.0, 0.0) < 0.5) {
                continue;
            }

            double reach = Math.sqrt(Math.max(0.0, radius * radius - x * x));
            double rigid = dist - reach;

            if (lane == centreLane && Math.abs(x) < centerX) {
                centerX = Math.abs(x);
                centerDist = dist;
            }
            if (!hit || rigid < minRigid) {
                minRigid = rigid;
                minNormal = clipResult.getDirection();
                minHitSubLevel = hitSubLevel;
                minHitBlock = clipResult.getBlockPos();
            }
            hit = true;
        }

        if (!hit) {
            return new Result(false, 0.0, 0.0, Direction.UP, null, null);
        }
        return new Result(true, minRigid, centerDist - (minRigid + radius), minNormal, minHitSubLevel, minHitBlock);
    }


    public static double probe(Level level, @Nullable SubLevel ignored, Vec3 origin, Pose3dc pose, double radius) {
        ClipContext clipContext = new ClipContext(origin, origin.subtract(0.0, 5.0, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        ((ClipContextExtension) clipContext).sable$setIgnoredSubLevel(ignored);
        BlockHitResult clipResult = level.clip(clipContext);
        if (clipResult.getType() == HitResult.Type.MISS) {
            return Double.NaN;
        }
        SubLevel hitSubLevel = Sable.HELPER.getContaining(level, clipResult.getLocation());
        Vec3 localHitPos = pose.transformPositionInverse(hitSubLevel == null
                ? clipResult.getLocation()
                : hitSubLevel.logicalPose().transformPosition(clipResult.getLocation()));
        double dist = origin.y - localHitPos.y;
        return dist <= 1.0e-5 ? Double.NaN : dist - radius;
    }


    private static int footprintOffsets(Vec3 center, Vector3d forward, Pose3dc pose, double half,
                                        double[] out) {
        Vector3dc worldCenter = pose.transformPosition(JOMLConversion.toJOML(center));
        Vector3dc worldAhead = pose.transformPosition(JOMLConversion.toJOML(center).add(forward));

        double[] splits = new double[2 + 2 * MAX_FOOTPRINT_SAMPLES];
        int n = 0;
        splits[n++] = -half;
        splits[n++] = half;
        n = addGridCrossings(splits, n, worldCenter.x(), worldAhead.x() - worldCenter.x(), half);
        n = addGridCrossings(splits, n, worldCenter.z(), worldAhead.z() - worldCenter.z(), half);
        Arrays.sort(splits, 0, n);

        int count = 0;
        for (int i = 0; i + 1 < n && count < out.length; i++) {
            double a = splits[i];
            double b = splits[i + 1];
            if (b - a < 1.0e-4) {
                continue;
            }
            double inset = Math.min(1.0e-3, (b - a) * 0.25);
            out[count++] = Mth.clamp(0.0, a + inset, b - inset);
        }
        return count;
    }

    private static int addGridCrossings(double[] out, int n, double origin, double dir, double half) {
        if (Math.abs(dir) < 1.0e-9) {
            return n;
        }
        double span = Math.abs(dir) * half;
        for (int k = Mth.ceil(origin - span); k <= Mth.floor(origin + span) && n < out.length; k++) {
            double t = (k - origin) / dir;
            if (t > -half && t < half) {
                out[n++] = t;
            }
        }
        return n;
    }

    public static double fudgeFriction(double realValue) {
        return realValue < 1.0 ? 0.1 + 0.9 * realValue : realValue;
    }
}
