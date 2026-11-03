package com.createmotorsport.effects;

import com.createmotorsport.Config;
import com.createmotorsport.physics.vehicle.CornerState;
import com.createmotorsport.physics.world.TerrainCast;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

// Tyre smoke and skidmarks, emitted from the server
public final class                          WheelEffects {

    // Tire smoke tuning
    private static final double SMOKE_HEAT_COLD_C = 60.0; // no smoke boost at/below this temp
    private static final double SMOKE_HEAT_HOT_C = 130.0; // full smoke boost at/above this
    private static final double SMOKE_DRIFT_SPEED = 0.09;
    private static final double SMOKE_CLOUD_LEAN = 1.6;
    private static final double SMOKE_CENTER_HEIGHT = 0.45;
    private static final double SMOKE_H_SPREAD_RADIUS = 0.55;
    private static final double SMOKE_H_SPREAD_BASE = 0.22;
    private static final double SMOKE_V_SPREAD_RADIUS = 0.7;
    private static final double SMOKE_DRIFT_MAX = 0.05;
    private static final int SMOKE_MAX_PUFFS = 32;

    private WheelEffects() {
    }

    public static final class State {
        public double smokeSeverity;
        public double smokeX, smokeY, smokeZ;
        public double smokeVx, smokeVy, smokeVz;
        public double smokeRadius = 0.5;
        public double smokeAccum;
        public BlockState smokeSurface;

        // skidmark ribbon
        public double skidAxleX, skidAxleY, skidAxleZ;
        public boolean skidActive;
        public double skidPrevLX, skidPrevLY, skidPrevLZ;
        public double skidPrevRX, skidPrevRY, skidPrevRZ;
        public boolean skidFreshContact;// set each grounded tick
        public int skidHold; // ticks of trailing mark left
        public double skidLastIntensity;
    }

    // once per game tick
    public static void emit(ServerLevel serverLevel, BlockPos at, State left, State right) {
        emitTireSmoke(serverLevel, left);
        emitTireSmoke(serverLevel, right);
        emitSkidmarks(serverLevel, at, left, right);
    }

    private static void emitSkidmarks(ServerLevel serverLevel, BlockPos at, State left, State right) {
        if (!Config.SKIDMARKS.get()) {
            left.skidActive = false;
            right.skidActive = false;
            return;
        }
        java.util.List<float[]> quads = new java.util.ArrayList<>(2);
        appendSkidQuad(left, quads);
        appendSkidQuad(right, quads);
        if (!quads.isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    192.0, new com.createmotorsport.network.SkidmarkPacket(quads));
        }
    }

    private static void appendSkidQuad(State fx, java.util.List<float[]> out) {
        boolean hardContact = fx.skidFreshContact && isHardSkidSurface(fx.smokeSurface);
        boolean slipping = hardContact && fx.smokeSeverity > 0.0;

        double intensity;
        int trailTicks = Math.max(1, (int) Math.round(Config.SKIDMARK_TRAIL_TIME.getAsDouble() * 20.0));
        if (slipping) {
            fx.skidHold = trailTicks;
            fx.skidLastIntensity = fx.smokeSeverity;
            intensity = fx.smokeSeverity;
        } else if (hardContact && fx.skidHold > 0) {
            fx.skidHold--;
            intensity = fx.skidLastIntensity * ((double) fx.skidHold / trailTicks);
        } else {
            fx.skidActive = false;
            fx.skidFreshContact = false;
            return;
        }

        double halfWidth = Config.SKIDMARK_WIDTH.getAsDouble() * 0.5;
        double lx = fx.smokeX - fx.skidAxleX * halfWidth;
        double ly = fx.smokeY - fx.skidAxleY * halfWidth;
        double lz = fx.smokeZ - fx.skidAxleZ * halfWidth;
        double rx = fx.smokeX + fx.skidAxleX * halfWidth;
        double ry = fx.smokeY + fx.skidAxleY * halfWidth;
        double rz = fx.smokeZ + fx.skidAxleZ * halfWidth;

        if (fx.skidActive && intensity > 0.0) {
            out.add(new float[]{
                    (float) fx.skidPrevLX, (float) fx.skidPrevLY, (float) fx.skidPrevLZ,
                    (float) fx.skidPrevRX, (float) fx.skidPrevRY, (float) fx.skidPrevRZ,
                    (float) rx, (float) ry, (float) rz,
                    (float) lx, (float) ly, (float) lz,
                    (float) Mth.clamp(intensity, 0.0, 1.0)});
        }
        fx.skidPrevLX = lx;
        fx.skidPrevLY = ly;
        fx.skidPrevLZ = lz;
        fx.skidPrevRX = rx;
        fx.skidPrevRY = ry;
        fx.skidPrevRZ = rz;
        fx.skidActive = true;
        fx.skidFreshContact = false;
    }

    // Skid marks only work on hard surfaces for now, so not dirt or sand or gravel
    private static boolean isHardSkidSurface(BlockState surface) {
        if (surface == null || surface.isAir()) {
            return false;
        }
        return !(surface.is(BlockTags.DIRT) || surface.is(BlockTags.SAND)
                || surface.is(BlockTags.SNOW) || surface.is(Blocks.GRAVEL));
    }

    private static void emitTireSmoke(ServerLevel serverLevel, State fx) {
        if (fx.smokeSeverity <= 0.0) {
            fx.smokeAccum = 0.0;
            return;
        }
        fx.smokeAccum += fx.smokeSeverity * Config.TIRE_SMOKE_DENSITY.getAsDouble();
        int puffs = (int) fx.smokeAccum;
        if (puffs <= 0) {
            return;
        }
        fx.smokeAccum -= puffs;
        puffs = Math.min(puffs, SMOKE_MAX_PUFFS);

        ParticleOptions particle = smokeParticleFor(fx.smokeSurface);
        double r = fx.smokeRadius;
        double cx = fx.smokeX + fx.smokeVx * SMOKE_CLOUD_LEAN;
        double cy = fx.smokeY + r * SMOKE_CENTER_HEIGHT;
        double cz = fx.smokeZ + fx.smokeVz * SMOKE_CLOUD_LEAN;
        double hSpread = r * SMOKE_H_SPREAD_RADIUS + SMOKE_H_SPREAD_BASE;
        double vSpread = r * SMOKE_V_SPREAD_RADIUS + 0.05;
        serverLevel.sendParticles(particle, cx, cy, cz, puffs, hSpread, vSpread, hSpread, SMOKE_DRIFT_MAX);
    }

    // Will improve this later to be more data driven but for now this detects some surfaces to kick up dirt instead of smoke the tire
    private static ParticleOptions smokeParticleFor(BlockState surface) {
        if (surface != null && Config.TIRE_SMOKE_GROUND_DUST.get()
                && (surface.is(BlockTags.DIRT) || surface.is(BlockTags.SAND) || surface.is(BlockTags.SNOW)
                    || surface.is(Blocks.GRAVEL))) {
            return new BlockParticleOption(ParticleTypes.BLOCK, surface);
        }
        return ParticleTypes.CAMPFIRE_COSY_SMOKE;
    }

    // Whether to smoke tires, for the server tick. Keeping it separate from force solver
    public static void record(Level level, State fx, CornerState wheel, Pose3d pose, Vector3d contactPoint,
                              Vector3d forward, Vector3d axle, double vLon, double vLat, double radius,
                              TerrainCast.Result cast) {
        if (!Config.TIRE_SMOKE.get()) {
            fx.smokeSeverity = 0.0;
            fx.skidFreshContact = false;
            return;
        }

        // recorded every grounded tick for the skid ribbon
        Vector3d groundLocal = new Vector3d(contactPoint).sub(0.0, radius - 0.05, 0.0);
        Vec3 world = pose.transformPosition(JOMLConversion.toMojang(groundLocal));
        fx.smokeX = world.x;
        fx.smokeY = world.y;
        fx.smokeZ = world.z;
        fx.smokeRadius = radius;
        fx.smokeSurface = cast.hitBlock() != null ? level.getBlockState(cast.hitBlock()) : null;

        Vector3d worldAxle = new Vector3d(axle);
        pose.transformNormal(worldAxle);
        double axleLen = worldAxle.length();
        if (axleLen > 1.0e-6) {
            worldAxle.div(axleLen);
        }
        fx.skidAxleX = worldAxle.x;
        fx.skidAxleY = worldAxle.y;
        fx.skidAxleZ = worldAxle.z;
        fx.skidFreshContact = true;

        double speed = Math.hypot(vLon, vLat);
        if (speed < Config.TIRE_SMOKE_MIN_SPEED.getAsDouble()) {
            fx.smokeSeverity = 0.0;
            return;
        }
        // taking the worse of lateral or longitudinal slip to determine if slipping
        double slipLong = Math.abs(wheel.slipRatio);
        double slipLat = Math.abs(Math.sin(wheel.slipAngleRad));
        double slip = Math.max(slipLong, slipLat);
        double threshold = Config.TIRE_SMOKE_SLIP_THRESHOLD.getAsDouble();
        if (slip < threshold) {
            fx.smokeSeverity = 0.0;
            return;
        }
        double severity = Mth.clamp((slip - threshold) / (1.0 - threshold), 0.0, 1.0);

        // temperature affects smoke levels
        double heatBoost = Config.TIRE_SMOKE_HEAT_BOOST.getAsDouble();
        double heat = Mth.clamp((wheel.tireTemp - SMOKE_HEAT_COLD_C) / (SMOKE_HEAT_HOT_C - SMOKE_HEAT_COLD_C), 0.0, 1.0);
        fx.smokeSeverity = severity * ((1.0 - heatBoost) + heatBoost * heat);

        Vector3d drift = new Vector3d(forward).mul(vLon - wheel.omega * radius).add(new Vector3d(axle).mul(vLat));
        pose.transformNormal(drift);
        double len = drift.length();
        if (len > 1.0e-3) {
            drift.div(len).mul(SMOKE_DRIFT_SPEED);
        } else {
            drift.set(0.0, 0.0, 0.0);
        }
        fx.smokeVx = drift.x;
        fx.smokeVy = drift.y;
        fx.smokeVz = drift.z;
    }
}
