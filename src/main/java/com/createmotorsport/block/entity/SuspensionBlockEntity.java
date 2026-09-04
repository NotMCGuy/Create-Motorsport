package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.SuspensionBlock;
import com.createmotorsport.physics.TireModel;
import com.createmotorsport.physics.TireSpec;
import com.createmotorsport.physics.spec.DamperSpec;
import com.createmotorsport.physics.spec.TerrainAssistSpec;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.SimpleContainer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SuspensionBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, GeoBlockEntity {
    public static final double REST_LENGTH = 3.5 / 16.0;
    private static final double TRACK_HALF_WIDTH = 25.0 / 16.0;
    private static final double BUMP_STOP_GAP = 0.04;
    private static final double UNSPRUNG_MAX_STEP = 0.004;
    private static final double FOOTPRINT_FRACTION = 0.95;
    private static final int MAX_FOOTPRINT_SAMPLES = 8;

    private double lastCastLift;
    private double lastAssistLift;
    private static final int ASSIST_PROBES = 4;
    private static final double ASSIST_RELEASE_RATIO = 3.0;
    private double sprungMassPerWheel = 1.0;
    private int cachedWheelCount = 4;
    private double cachedCarSpeed;
    private long wheelCountStamp = Long.MIN_VALUE;
    public static final double MAX_TRAVEL = REST_LENGTH - BUMP_STOP_GAP;
    public static final double MAX_DROOP_RENDER = 0.15;
    private static final double GROUND_MARGIN = 0.15;
    private static final int SYNC_INTERVAL_TICKS = 2;


    public static final double MAX_LIFT = 0.25 + MAX_DROOP_RENDER;
    public static final int LIFT_STEPS = 8;
    public static final int MAX_LIFT_STEPS = 24;
    public static final double LIFT_STEP_HEIGHT = MAX_LIFT / LIFT_STEPS;

    private static final double TIRE_SPEED_COOL = 0.25;

    private static final double CAMBER_FADE_SPEED = 2.0; // full strength camber thrust at this speed

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

    // Server-side registries for engine lookup and batched force flushing
    private static final Collection<SuspensionBlockEntity> LOADED = new ObjectOpenHashSet<>();
    private static final Collection<SuspensionBlockEntity> QUEUED = new ObjectOpenHashSet<>();

    public enum WheelSide {
        LEFT, RIGHT
    }


    // suspension type can be set per car
    public enum SuspensionSetting {
        SOFT("Soft"),
        MEDIUM("Medium"),
        FIRM("Firm"),
        RACE("Race");

        private final String displayName;

        SuspensionSetting(String displayName) {
            this.displayName = displayName;
        }

        public double naturalFreqHz() {
            return switch (this) {
                case SOFT -> Config.SUSPENSION_SOFT_HZ.getAsDouble();
                case MEDIUM -> Config.SUSPENSION_MEDIUM_HZ.getAsDouble();
                case FIRM -> Config.SUSPENSION_FIRM_HZ.getAsDouble();
                case RACE -> Config.SUSPENSION_RACE_HZ.getAsDouble();
            };
        }

        public double dampingRatio() {
            return switch (this) {
                case SOFT -> Config.SUSPENSION_SOFT_DAMPING.getAsDouble();
                case MEDIUM -> Config.SUSPENSION_MEDIUM_DAMPING.getAsDouble();
                case FIRM -> Config.SUSPENSION_FIRM_DAMPING.getAsDouble();
                case RACE -> Config.SUSPENSION_RACE_DAMPING.getAsDouble();
            };
        }

        public DamperSpec damper() {
            return DamperSpec.F1_DIGRESSIVE;
        }

        public TerrainAssistSpec terrainAssist() {
            return TerrainAssistSpec.DEFAULT;
        }

        public double staticSagBlocks() {
            double omega0 = 2.0 * Math.PI * naturalFreqHz();
            return 9.81 / (omega0 * omega0);
        }

        public String getDisplayName() {
            return displayName;
        }

        public SuspensionSetting next() {
            SuspensionSetting[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static class WheelState {
        // simulation (server)
        double omega;
        double springLength = REST_LENGTH;
        boolean grounded;
        double surfaceMu = 1.0;

        double unsprungVel;
        double tireDeflection;
        double contactLoad;

        // interpolation (client)
        double clientSpringLength = REST_LENGTH;
        double lastClientSpringLength = REST_LENGTH;
        double angle;
        double lastAngle;

        // tire placing animation (client): 0 = none, 1 = deployed
        double deploy;
        double lastDeploy;

        // sync bookkeeping (server)
        double syncedOmega;
        double syncedSpringLength = REST_LENGTH;

        // last used physics values, for csv logging
        double telemLoad;
        double telemSlipRatio;
        double telemSlipAngleRad;
        double telemVLon;
        double telemVLat;
        double telemLongForce;
        double telemLatForce;
        double telemWheelSpeed;
        double telemCompression;
        double telemBrakeTorque;
        double telemGripMult = 1.0;
        double telemPeakMu = 1.0;
        double telemRigidLength = Double.NaN;
        double prevRigidLength = Double.NaN;
        double telemEffMass = Double.NaN;
        double telemHardpointVel = Double.NaN;
        double telemSpringForce = Double.NaN;
        double telemVelWorld = Double.NaN;
        double telemVelBody = Double.NaN;
        double telemVelDiff = Double.NaN;
        double telemVelNormal = Double.NaN;
        double telemCastLift = Double.NaN;
        double telemDampFraction = Double.NaN;
        double telemAssistLift = Double.NaN;
        double assistLift;
        double telemGripUse;
        double latUse;
        double telemGripLon;
        double telemGripLat;

        double prevLongForce; // last longitudinal tire force, for the relaxation low-pass
        double deflAlpha;     // lateral contact-patch deflection in (m), fiala lateral relaxation state
        double latBristle;    // lateral bristle deflection in (m), TMeasy dahl stand-still model
        double tireTemp = 20.0; // tire temperature (deg C), thermal model

        // tire smoke
        double smokeSeverity;
        double smokeX, smokeY, smokeZ;
        double smokeVx, smokeVy, smokeVz;
        double smokeRadius = 0.5;
        double smokeAccum;
        BlockState smokeSurface;

        // skidmark ribbon
        double skidAxleX, skidAxleY, skidAxleZ;
        boolean skidActive;
        double skidPrevLX, skidPrevLY, skidPrevLZ;
        double skidPrevRX, skidPrevRY, skidPrevRZ;
        boolean skidFreshContact;// set each grounded tick
        int skidHold; // ticks of trailing mark left
        double skidLastIntensity;
    }

    // snapshot of a wheel's last physics step, for csv logging
    public record WheelTelemetry(boolean grounded, double loadN, double slipRatio, double slipAngleDeg,
                                 double vLonMs, double vLatMs, double longForceN, double latForceN,
                                 double omega, double wheelSpeedMs, double springLenM, double compressionM,
                                 double surfaceMu, double brakeTorqueNm, double gripMult, double driveTorqueNm,
                                 double tireTempC, double rigidLenM, double tireDeflM, double unsprungVMs,
                                 double effMassKg, double hardpointVMs, double springForceN,
                                 double velWorld, double velBody, double velDiff, double velNormal,
                                 double castLift, double dampFraction, double assistLift,
                                 double gripUse, double gripLon, double gripLat) {
    }

    public WheelTelemetry getTelemetry(WheelSide side) {
        WheelState w = getWheel(side);
        return new WheelTelemetry(w.grounded, w.telemLoad, w.telemSlipRatio,
                Math.toDegrees(w.telemSlipAngleRad), w.telemVLon, w.telemVLat, w.telemLongForce,
                w.telemLatForce, w.omega, w.telemWheelSpeed, w.springLength, w.telemCompression,
                w.surfaceMu * w.telemGripMult, w.telemBrakeTorque, w.telemGripMult, driveTorquePerWheel,
                w.tireTemp, w.telemRigidLength, w.tireDeflection, w.unsprungVel, w.telemEffMass,
                w.telemHardpointVel, w.telemSpringForce,
                w.telemVelWorld, w.telemVelBody, w.telemVelDiff, w.telemVelNormal, w.telemCastLift,
                w.telemDampFraction, w.telemAssistLift, w.telemGripUse,
                w.telemGripLon, w.telemGripLat);
    }


    // redstone link control channels, used by steering menu
    public enum SteerChannel {
        STEER_LEFT("Steer Left"),
        STEER_RIGHT("Steer Right"),
        BRAKE("Brake");

        private final String displayName;

        SteerChannel(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final SteerChannel[] CHANNELS = SteerChannel.values();
    public static final int CONTROL_SLOT_COUNT = CHANNELS.length * 2;

    public static int channelSlotA(SteerChannel channel) {
        return channel.ordinal() * 2;
    }

    public static int channelSlotB(SteerChannel channel) {
        return channel.ordinal() * 2 + 1;
    }

    private final WheelState leftWheel = new WheelState();
    private final WheelState rightWheel = new WheelState();
    private final NonNullList<ItemStack> tires = NonNullList.withSize(2, ItemStack.EMPTY);
    private final ForceTotal forceTotal = new ForceTotal();

    // two create redstone link frequency slots per named channel
    private final NonNullList<ItemStack> controlItems = NonNullList.withSize(CONTROL_SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleContainer controls = new SimpleContainer(CONTROL_SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            for (int slot = 0; slot < CONTROL_SLOT_COUNT; slot++) {
                controlItems.set(slot, getItem(slot));
            }
            refreshLinkNetwork();
            SuspensionBlockEntity.this.setChanged();
        }
    };
    private final int[] receivedSignals = new int[CHANNELS.length];
    private final boolean[] registeredLinks = new boolean[CHANNELS.length];
    private final IRedstoneLinkable[] channelLinks = new IRedstoneLinkable[CHANNELS.length];


    private double driverSteer; // -15 to 15, + is left
    private double driverBrake; // 0 to 15
    private long driverControlTime = Long.MIN_VALUE;

    private SuspensionSetting setting = SuspensionSetting.RACE;
    private double driveTorquePerWheel;
    private long driveTorqueGameTime = Long.MIN_VALUE;
    private double brake01;
    private double steerSignal;
    private double chasingSteer;
    private double lastChasingSteer;
    private int syncCooldown;
    private boolean syncDirty;
    private int liftSteps;
    private boolean driftDiffMode;
    private boolean steerAssistOff; // true is off

    // defaults to rear axle, which means the user does need to change the other to 'front' for now, will fix later
    private boolean frontAxle;



    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public SuspensionBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), pos, state);
        for (int slot = 0; slot < CONTROL_SLOT_COUNT; slot++) {
            controls.setItem(slot, controlItems.get(slot));
        }
        for (SteerChannel channel : CHANNELS) {
            channelLinks[channel.ordinal()] = new ChannelLink(channel);
        }
    }

    public SimpleContainer getControls() {
        return controls;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ===================================================
    // GeckoLib (client)
    // ========================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // =======================================
    // Registry and batching force application
    // ====================================

    // =
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            LOADED.add(this);
            refreshLinkNetwork();
        }
    }

    @Override
    public void remove() {
        LOADED.remove(this);
        QUEUED.remove(this);
        removeLinks();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        LOADED.remove(this);
        QUEUED.remove(this);
        removeLinks();
        super.onChunkUnloaded();
    }

    // =======================
    // Steering chanels & redstone links
    // ==========================


    public boolean isFrontAxle() {
        return frontAxle;
    }

    // Toggle front/rear from the menu; sendData() pushes the new state to clients for the button's label
    public void toggleAxleEnd() {
        if (level == null || level.isClientSide) {
            return;
        }
        frontAxle = !frontAxle;
        setChanged();
        sendData();
    }

    public int getLiftSteps() {
        return liftSteps;
    }

    public double liftHeight() {
        return liftSteps * LIFT_STEP_HEIGHT;
    }

    private double animatedLiftHeight() {
        return Math.min(liftSteps, LIFT_STEPS) * LIFT_STEP_HEIGHT;
    }

    // Lift beyond the animation
    public double breakawayDrop() {
        return liftHeight() - animatedLiftHeight();
    }

    private double restLength() {
        return REST_LENGTH + liftHeight();
    }

    // user clicking the menu
    public void adjustLift(int delta) {
        if (level == null || level.isClientSide) {
            return;
        }
        liftSteps = Mth.clamp(liftSteps + delta, 0, MAX_LIFT_STEPS);
        setChanged();
        sendData();
    }

    public void setDriverSteering(double steer, double brake) {
        if (level == null || level.isClientSide) {
            return;
        }
        driverSteer = Mth.clamp(steer, -15.0, 15.0);
        driverBrake = Mth.clamp(brake, 0.0, 15.0);
        driverControlTime = level.getGameTime();
    }

    // drifting mode set by driver that changes differential settings
    public void setDriftDiffMode(boolean drift) {
        driftDiffMode = drift;
    }

    // driver toggle for steering assist too
    public void setSteerAssistOff(boolean off) {
        if (off != steerAssistOff) {
            steerAssistOff = off;
            syncDirty = true;
        }
    }

    // peak Mu for the HUD grip readout
    public double getPeakMu(WheelSide side) {
        return getWheel(side).telemPeakMu;
    }

    private boolean driverActive() {
        return level != null && driverControlTime >= level.getGameTime() - 2;
    }

    private int channelSignal(SteerChannel channel) {
        return receivedSignals[channel.ordinal()];
    }

    private boolean hasLink(SteerChannel channel) {
        return !controls.getItem(channelSlotA(channel)).isEmpty()
                || !controls.getItem(channelSlotB(channel)).isEmpty();
    }

    private void refreshLinkNetwork() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (SteerChannel channel : CHANNELS) {
            int i = channel.ordinal();
            if (registeredLinks[i]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, channelLinks[i]);
                registeredLinks[i] = false;
            }
            if (hasLink(channel)) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, channelLinks[i]);
                registeredLinks[i] = true;
            } else {
                receivedSignals[i] = 0;
            }
        }
    }

    private void removeLinks() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (SteerChannel channel : CHANNELS) {
            int i = channel.ordinal();
            if (registeredLinks[i]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, channelLinks[i]);
                registeredLinks[i] = false;
            }
        }
    }

    private class ChannelLink implements IRedstoneLinkable {
        private final SteerChannel channel;

        private ChannelLink(SteerChannel channel) {
            this.channel = channel;
        }

        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int power) {
            receivedSignals[channel.ordinal()] = power;
        }

        @Override
        public boolean isListening() {
            return hasLink(channel);
        }

        @Override
        public boolean isAlive() {
            return level != null && !level.isClientSide && !isRemoved() && level.isLoaded(worldPosition);
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(controls.getItem(channelSlotA(channel))),
                    RedstoneLinkNetworkHandler.Frequency.of(controls.getItem(channelSlotB(channel)))
            );
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }


    // server thread only; collection of all suspensions loaded, engines filters by sub-level.
    public static Collection<SuspensionBlockEntity> allLoaded() {
        return LOADED;
    }


    // applies queued force once per physics substep from the Sable pre-tick event
    public static void flushBatchedForces(ServerLevel level, double timeStep) {
        ObjectOpenHashSet<ServerSubLevel> dragged = new ObjectOpenHashSet<>();
        for (SuspensionBlockEntity be : QUEUED) {
            if (be.isRemoved()) {
                continue;
            }
            SubLevel subLevel = Sable.HELPER.getContaining(be);
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
                continue;
            }
            RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
            if (handle != null) {
                if (dragged.add(serverSubLevel)) {
                    applyBodyDrag(level, serverSubLevel, be.forceTotal, timeStep);
                }
                handle.applyForcesAndReset(be.forceTotal);
            }
        }
        QUEUED.clear();
    }


    private static void applyBodyDrag(ServerLevel level, ServerSubLevel subLevel,
                                      ForceTotal forceTotal, double dt) {
        MassData massData = subLevel.getMassTracker();
        if (massData == null || massData.isInvalid()) {
            return;
        }
        Vector3d localVel = subLevel.logicalPose().orientation()
                .transformInverse(new Vector3d(subLevel.latestLinearVelocity));

        double keep = Config.SABLE_DRAG_SCALE.getAsDouble();
        double drag = DimensionPhysicsData.getUniversalDrag(level);
        if (keep < 1.0 && drag > 0.0) {
            double mass = massData.getMass();
            forceTotal.applyLinearImpulse(new Vector3d(localVel).mul(mass * drag * (1.0 - keep) * dt));
        }

        double k = Config.AERO_DRAG_COEFFICIENT.getAsDouble();
        if (k <= 0.0) {
            return;
        }
        double speed = localVel.length();
        if (speed < 1.0e-4) {
            return;
        }
        forceTotal.applyLinearImpulse(new Vector3d(localVel).mul(-k * speed * dt));
    }

    // ==============================================
    // Game tick
    // ===============================

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientTick();
            return;
        }


        // trying to auto recognize steering signal on the same sub-level
        Direction facing = getFacing();
        double newSteer;
        double newBrake;
        if (driverActive()) {
            newSteer = driverSteer;
            newBrake = driverBrake / 15.0;
        } else {
            int adjacentRight = level.getSignal(worldPosition.relative(facing), facing);
            int adjacentLeft = level.getSignal(worldPosition.relative(facing.getOpposite()), facing.getOpposite());
            int left = Math.max(adjacentLeft, channelSignal(SteerChannel.STEER_LEFT));
            int right = Math.max(adjacentRight, channelSignal(SteerChannel.STEER_RIGHT));
            newSteer = Mth.clamp(left - right, -15, 15);
            int adjacentBrake = level.getSignal(worldPosition.above(), Direction.UP);
            newBrake = Math.max(adjacentBrake, channelSignal(SteerChannel.BRAKE)) / 15.0;
        }
        if (Math.abs(newSteer - steerSignal) > 0.05) {
            steerSignal = newSteer;
            syncDirty = true;
        }
        brake01 = newBrake;

        lastChasingSteer = chasingSteer;
        chasingSteer = Mth.lerp(0.4, chasingSteer, steerSignal / 15.0 * Math.toRadians(Config.STEERING_MAX_DEGREES.getAsDouble()) * speedSteerLock());


        // torque stops when engine stops ticking
        if (level.getGameTime() - driveTorqueGameTime > 3) {
            driveTorquePerWheel = 0.0;
        }

        if (syncCooldown > 0) {
            syncCooldown--;
        }
        if ((syncDirty || wheelStateChangedEnough()) && syncCooldown <= 0) {
            syncDirty = false;
            syncCooldown = SYNC_INTERVAL_TICKS;
            for (WheelState w : new WheelState[]{leftWheel, rightWheel}) {
                w.syncedOmega = w.omega;
                w.syncedSpringLength = w.springLength;
            }
            sendData();
        }

        if (level instanceof ServerLevel serverLevel) {
            emitTireSmoke(serverLevel, leftWheel);
            emitTireSmoke(serverLevel, rightWheel);
            emitSkidmarks(serverLevel);
        }
    }

    private void emitSkidmarks(ServerLevel serverLevel) {
        if (!Config.SKIDMARKS.get()) {
            leftWheel.skidActive = false;
            rightWheel.skidActive = false;
            return;
        }
        java.util.List<float[]> quads = new java.util.ArrayList<>(2);
        appendSkidQuad(leftWheel, quads);
        appendSkidQuad(rightWheel, quads);
        if (!quads.isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    192.0, new com.createmotorsport.network.SkidmarkPacket(quads));
        }
    }

    private void appendSkidQuad(WheelState wheel, java.util.List<float[]> out) {
        boolean hardContact = wheel.skidFreshContact && isHardSkidSurface(wheel.smokeSurface);
        boolean slipping = hardContact && wheel.smokeSeverity > 0.0;

        double intensity;
        int trailTicks = Math.max(1, (int) Math.round(Config.SKIDMARK_TRAIL_TIME.getAsDouble() * 20.0));
        if (slipping) {
            wheel.skidHold = trailTicks;
            wheel.skidLastIntensity = wheel.smokeSeverity;
            intensity = wheel.smokeSeverity;
        } else if (hardContact && wheel.skidHold > 0) {
            wheel.skidHold--;
            intensity = wheel.skidLastIntensity * ((double) wheel.skidHold / trailTicks);
        } else {
            wheel.skidActive = false;
            wheel.skidFreshContact = false;
            return;
        }

        double halfWidth = Config.SKIDMARK_WIDTH.getAsDouble() * 0.5;
        double lx = wheel.smokeX - wheel.skidAxleX * halfWidth;
        double ly = wheel.smokeY - wheel.skidAxleY * halfWidth;
        double lz = wheel.smokeZ - wheel.skidAxleZ * halfWidth;
        double rx = wheel.smokeX + wheel.skidAxleX * halfWidth;
        double ry = wheel.smokeY + wheel.skidAxleY * halfWidth;
        double rz = wheel.smokeZ + wheel.skidAxleZ * halfWidth;

        if (wheel.skidActive && intensity > 0.0) {
            out.add(new float[]{
                    (float) wheel.skidPrevLX, (float) wheel.skidPrevLY, (float) wheel.skidPrevLZ,
                    (float) wheel.skidPrevRX, (float) wheel.skidPrevRY, (float) wheel.skidPrevRZ,
                    (float) rx, (float) ry, (float) rz,
                    (float) lx, (float) ly, (float) lz,
                    (float) Mth.clamp(intensity, 0.0, 1.0)});
        }
        wheel.skidPrevLX = lx;
        wheel.skidPrevLY = ly;
        wheel.skidPrevLZ = lz;
        wheel.skidPrevRX = rx;
        wheel.skidPrevRY = ry;
        wheel.skidPrevRZ = rz;
        wheel.skidActive = true;
        wheel.skidFreshContact = false;
    }

    // Skid marks only work on hard surfaces for now, so not dirt or sand or gravel
    private static boolean isHardSkidSurface(BlockState surface) {
        if (surface == null || surface.isAir()) {
            return false;
        }
        return !(surface.is(BlockTags.DIRT) || surface.is(BlockTags.SAND)
                || surface.is(BlockTags.SNOW) || surface.is(Blocks.GRAVEL));
    }

    private void emitTireSmoke(ServerLevel serverLevel, WheelState wheel) {
        if (wheel.smokeSeverity <= 0.0) {
            wheel.smokeAccum = 0.0;
            return;
        }
        wheel.smokeAccum += wheel.smokeSeverity * Config.TIRE_SMOKE_DENSITY.getAsDouble();
        int puffs = (int) wheel.smokeAccum;
        if (puffs <= 0) {
            return;
        }
        wheel.smokeAccum -= puffs;
        puffs = Math.min(puffs, SMOKE_MAX_PUFFS);

        ParticleOptions particle = smokeParticleFor(wheel.smokeSurface);
        double r = wheel.smokeRadius;
        double cx = wheel.smokeX + wheel.smokeVx * SMOKE_CLOUD_LEAN;
        double cy = wheel.smokeY + r * SMOKE_CENTER_HEIGHT;
        double cz = wheel.smokeZ + wheel.smokeVz * SMOKE_CLOUD_LEAN;
        double hSpread = r * SMOKE_H_SPREAD_RADIUS + SMOKE_H_SPREAD_BASE;
        double vSpread = r * SMOKE_V_SPREAD_RADIUS + 0.05;
        serverLevel.sendParticles(particle, cx, cy, cz, puffs, hSpread, vSpread, hSpread, SMOKE_DRIFT_MAX);
    }

    // Will improve this later to be more data driven but for now this detects some surfaces to kick up dirt instead of smoke the tire
    private ParticleOptions smokeParticleFor(BlockState surface) {
        if (surface != null && Config.TIRE_SMOKE_GROUND_DUST.get()
                && (surface.is(BlockTags.DIRT) || surface.is(BlockTags.SAND) || surface.is(BlockTags.SNOW)
                    || surface.is(Blocks.GRAVEL))) {
            return new BlockParticleOption(ParticleTypes.BLOCK, surface);
        }
        return ParticleTypes.CAMPFIRE_COSY_SMOKE;
    }

    private boolean wheelStateChangedEnough() {
        for (WheelState w : new WheelState[]{leftWheel, rightWheel}) {
            if (Math.abs(w.omega - w.syncedOmega) > 0.4 || Math.abs(w.springLength - w.syncedSpringLength) > 0.015) {
                return true;
            }
        }
        return false;
    }


    // 1/v^2 + 1/vRef^2
    // speed sensitive should work better
    private double speedSteerLock() {
        if (steerAssistOff) {
            return 1.0;
        }
        double fullLockKmh = Config.STEER_FULL_LOCK_SPEED.getAsDouble();
        if (fullLockKmh <= 0.0 || level == null) {
            return 1.0;
        }
        double speedKmh = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(worldPosition)).length() * 3.6;
        if (speedKmh <= fullLockKmh) {
            return 1.0;
        }
        double aeroKmh = Config.STEER_ASSIST_AERO_SPEED.getAsDouble();
        if (aeroKmh <= 0.0) {
            double ratio = fullLockKmh / speedKmh;
            return ratio * ratio;
        }
        double aeroTerm = 1.0 / (aeroKmh * aeroKmh);
        double atThisSpeed = 1.0 / (speedKmh * speedKmh) + aeroTerm;
        double atFullLock = 1.0 / (fullLockKmh * fullLockKmh) + aeroTerm;
        return Mth.clamp(atThisSpeed / atFullLock, 0.0, 1.0);
    }

    private void clientTick() {
        lastChasingSteer = chasingSteer;
        chasingSteer = Mth.lerp(0.4, chasingSteer, steerSignal / 15.0 * Math.toRadians(Config.STEERING_MAX_DEGREES.getAsDouble()) * speedSteerLock());
        for (WheelSide side : WheelSide.values()) {
            WheelState w = getWheel(side);
            w.lastAngle = w.angle;
            w.angle += w.omega / 20.0;
            w.lastClientSpringLength = w.clientSpringLength;
            w.clientSpringLength = Mth.lerp(0.5, w.clientSpringLength, w.springLength);
            // Ease the tire down when it is first installed (mirrors the wheel mount's drop-in).
            w.lastDeploy = w.deploy;
            w.deploy = Mth.lerp(0.25, w.deploy, hasTire(side) ? 1.0 : 0.0);
        }
    }

    // =======================
    // Physics substep
    // ================

    // gotta use the right mass here, was using InvNormalMass before, which is wrong
    private double sprungMassPerWheel(ServerSubLevel subLevel, MassData massData) {
        long now = level.getGameTime();
        if (now != wheelCountStamp) {
            wheelCountStamp = now;
            int count = 0;
            for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
                if (actor instanceof SuspensionBlockEntity s && !s.isRemoved()) {
                    if (s.hasTire(WheelSide.LEFT)) count++;
                    if (s.hasTire(WheelSide.RIGHT)) count++;
                }
            }
            cachedWheelCount = Math.max(1, count);
        }
        double unsprung = cachedWheelCount * Math.max(0.1, Config.WHEEL_MASS.getAsDouble());
        double sprung = Math.max(1.0, massData.getMass() - unsprung);
        return sprung / cachedWheelCount;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!hasAnyTire()) {
            return;
        }
        Pose3d pose = subLevel.logicalPose();
        MassData massData = subLevel.getMassTracker();
        if (massData.isInvalid()) {
            return;
        }
        this.sprungMassPerWheel = sprungMassPerWheel(subLevel, massData);
        Vector3d carVel = new Vector3d(subLevel.latestLinearVelocity);
        this.cachedCarSpeed = Math.sqrt(carVel.x * carVel.x + carVel.z * carVel.z);

        boolean queued = false;
        queued |= stepWheel(WheelSide.LEFT, subLevel, pose, massData, timeStep);
        queued |= stepWheel(WheelSide.RIGHT, subLevel, pose, massData, timeStep);
        applyDifferential(timeStep);
        if (queued) {
            QUEUED.add(this);
        }
    }


    // Limited-slip differential model from vdrift (driveline.h solveDiffClutch2)
    // the velocity constraint pulls the two wheels torward a shared speed, but the corrective torque is capped by
    // an anti-slip limit so it slips instead of locking. antiSlip=0 is open differentials, big number is a spool
    private void applyDifferential(double dt) {
        if (!hasTire(WheelSide.LEFT) || !hasTire(WheelSide.RIGHT)) {
            return;
        }
        double antiSlip = (driftDiffMode ? Config.DIFFERENTIAL_ANTISLIP_DRIFT : Config.DIFFERENTIAL_ANTISLIP_TORQUE)
                .getAsDouble();
        if (antiSlip <= 0.0) {
            return;
        }
        double inertiaL = wheelInertia(WheelSide.LEFT);
        double inertiaR = wheelInertia(WheelSide.RIGHT);

        // equal and opposite impulse driving two wheels to the same speed, clamped by anti-slip torque budget for the substep
        double velErr = leftWheel.omega - rightWheel.omega;
        double jointInertia = 1.0 / (1.0 / inertiaL + 1.0 / inertiaR);
        double limit = antiSlip * dt;
        double lambda = Mth.clamp(-velErr * jointInertia, -limit, limit);

        leftWheel.omega += lambda / inertiaL;
        rightWheel.omega -= lambda / inertiaR;
    }

    private double wheelInertia(WheelSide side) {
        TireLike tire = getTire(side).get(OffroadDataComponents.TIRE);
        double radius = tire != null ? tire.radius() : REST_LENGTH;
        return wheelInertia(radius);
    }

    // I = m*r^2
    private static double wheelInertia(double radius) {
        return Math.max(0.5, Config.WHEEL_MASS.getAsDouble() * radius * radius);
    }

    // Advances the wheel between the suspension spring and the tyre spring, returns the force the suspension puts into the body
    private double stepUnsprung(WheelState wheel, double rateMass, double rigidLength,
                                double hardpointVel, double responseMass, double dt) {
        double springK = TireModel.springRate(rateMass, setting.naturalFreqHz());
        boolean rebound = hardpointVel > 0.0;
        double springC = TireModel.springDamping(rateMass, setting.naturalFreqHz(),
                setting.dampingRatio(), rebound);
        double tireK = springK * Config.TIRE_STIFFNESS_RATIO.getAsDouble();
        double unsprungMass = Math.max(0.1, Config.WHEEL_MASS.getAsDouble());
        double tireC = 2.0 * Config.TIRE_VERTICAL_DAMPING.getAsDouble()
                * Math.sqrt(tireK * unsprungMass);

        // Re seed if the state is nonsense or the car has moved somewhere else
        if (!Double.isFinite(wheel.springLength) || !Double.isFinite(wheel.unsprungVel)
                || Math.abs(wheel.springLength - rigidLength) > MAX_TRAVEL + MAX_DROOP_RENDER) {
            wheel.springLength = Mth.clamp(rigidLength, restLength() - MAX_TRAVEL,
                    restLength() + MAX_DROOP_RENDER);
            wheel.unsprungVel = 0.0;
        }

        int steps = Math.max(1, (int) Math.ceil(dt / UNSPRUNG_MAX_STEP));
        double h = dt / steps;
        double groundLength = rigidLength;
        for (int i = 0; i < steps; i++) {
            double deflection = Math.max(0.0, wheel.springLength - groundLength);
            double compression = Math.max(0.0, restLength() - wheel.springLength);
            double strutC = TireModel.digressiveDamping(springC, hardpointVel - wheel.unsprungVel,
                    setting.damper());
            wheel.unsprungVel = TireModel.solveUnsprung(unsprungMass, h, wheel.unsprungVel, hardpointVel,
                    springK, strutC, compression, tireK, tireC, deflection);
            wheel.springLength = Mth.clamp(wheel.springLength + h * (hardpointVel - wheel.unsprungVel),
                    restLength() - MAX_TRAVEL, restLength() + MAX_DROOP_RENDER);
            groundLength += h * hardpointVel;
        }
        wheel.tireDeflection = Math.max(0.0, wheel.springLength - groundLength);
        wheel.telemRigidLength = groundLength;
        wheel.contactLoad = tireK * wheel.tireDeflection;

        // tried averaging before but reading the state at the end seemed to be better
        double newCompression = Math.max(0.0, restLength() - wheel.springLength);
        double strutVel = wheel.unsprungVel - hardpointVel;
        double bodyC = TireModel.effectiveDamping(springC, strutVel, setting.damper(), dt, responseMass);
        wheel.telemDampFraction = springC > 0.0 ? bodyC / springC : 1.0;
        return Math.max(0.0, springK * newCompression + bodyC * strutVel);
    }

    private boolean stepWheel(WheelSide side, ServerSubLevel subLevel, Pose3d pose, MassData massData,
                              double dt) {
        WheelState wheel = getWheel(side);
        TireLike tire = getTire(side).get(OffroadDataComponents.TIRE);
        if (tire == null) {
            wheel.grounded = false;
            wheel.springLength = Mth.lerp(0.5, wheel.springLength, restLength());
            return false;
        }

        double radius = tire.radius();
        Direction facing = getFacing();
        Direction sideDir = getSideDirection(side);
        Vec3 hardpoint = Vec3.atCenterOf(worldPosition)
                .add(sideDir.getStepX() * TRACK_HALF_WIDTH, 0.0, sideDir.getStepZ() * TRACK_HALF_WIDTH);
        Vector3d hardpointJoml = JOMLConversion.toJOML(hardpoint);

        // Ackermann geometry on the steering now, so the outer and inner wheels have to take their own angles (see wheelSteerAngle)
        double wheelSteer = wheelSteerAngle(chasingSteer, side);
        Vector3d forward = new Vector3d(facing.getStepX(), 0.0, facing.getStepZ()).rotateY(wheelSteer);
        Direction rightDir = facing.getClockWise();
        Vector3d axle = new Vector3d(rightDir.getStepX(), 0.0, rightDir.getStepZ()).rotateY(wheelSteer);

        TerrainCastResult cast = castToTerrain(hardpoint, forward, pose, radius, wheel, dt);
        wheel.telemCastLift = this.lastCastLift;
        wheel.telemAssistLift = this.lastAssistLift;
        double distance = cast.distance();
        boolean grounded = distance <= restLength() + radius + GROUND_MARGIN;
        wheel.grounded = grounded;
        wheel.surfaceMu = cast.hitBlock() != null
                ? fudgeFriction(PhysicsBlockPropertyHelper.getFriction(level.getBlockState(cast.hitBlock())))
                : 1.0;

        double wheelInertia = wheelInertia(radius);
        double brakeTorque = brake01 * Config.BRAKE_STRENGTH.getAsDouble() * radius;

        if (!grounded) {
            wheel.springLength = Mth.clamp(Mth.lerp(0.4, wheel.springLength, restLength() + MAX_DROOP_RENDER),
                    restLength() - MAX_TRAVEL, restLength() + MAX_DROOP_RENDER);
            wheel.omega = TireModel.integrateSpin(wheel.omega, radius, wheelInertia, driveTorquePerWheel,
                    brakeTorque, 0.0, wheel.omega * radius, dt) * 0.995;
            wheel.telemLoad = 0.0;
            wheel.telemLongForce = 0.0;
            wheel.telemLatForce = 0.0;
            wheel.telemSlipRatio = 0.0;
            wheel.telemSlipAngleRad = 0.0;
            wheel.telemGripUse = 0.0;
            wheel.latUse = 0.0;
            wheel.telemGripLon = 0.0;
            wheel.telemGripLat = 0.0;
            wheel.telemVLon = 0.0;
            wheel.telemVLat = 0.0;
            wheel.telemCompression = 0.0;
            wheel.telemBrakeTorque = brakeTorque;
            wheel.telemWheelSpeed = wheel.omega * radius;
            wheel.smokeSeverity = 0.0;
            wheel.prevRigidLength = Double.NaN;
            return false;
        }

        double rigidLength = distance - radius;
        wheel.telemRigidLength = rigidLength;

        // this is the exact jacobian denominator. I was using this to size the spring and scale the contact load cap before, but that needs to be
        // the corners static share
        double invNormalMass = massData.getInverseNormalMass(hardpointJoml, OrientedBoundingBox3d.UP);

        double responseMass = 1.0 / (Math.max(1, cachedWheelCount) * invNormalMass);
        wheel.telemEffMass = invNormalMass > 0.0 ? 1.0 / invNormalMass : Double.POSITIVE_INFINITY;
        if (invNormalMass <= 1.0e-9) {
            return false;
        }
        double rateMass = this.sprungMassPerWheel;

        // overload
        Vector3d velocity = Sable.HELPER.getVelocity(level, subLevel, hardpointJoml, new Vector3d());
        Vector3d localVelocity = pose.transformNormalInverse(velocity);

        double hardpointVel = velocity.y;
        double diffVel = 0.0;
        if (Double.isFinite(wheel.prevRigidLength) && dt > 0.0) {
            double delta = rigidLength - wheel.prevRigidLength;
            if (Math.abs(delta) <= MAX_TRAVEL) {
                diffVel = delta / dt;
            }
        }
        wheel.prevRigidLength = rigidLength;
        wheel.telemVelWorld = velocity.y;
        wheel.telemVelBody = localVelocity.y;
        wheel.telemVelDiff = diffVel;

        double compression;
        double springForce;
        if (Config.TIRE_COMPLIANCE.get()) {
            springForce = stepUnsprung(wheel, rateMass, rigidLength, hardpointVel, responseMass, dt);
            compression = Math.max(0.0, restLength() - wheel.springLength);
        } else {
            wheel.springLength = Mth.clamp(rigidLength, restLength() - MAX_TRAVEL, restLength() + MAX_DROOP_RENDER);
            wheel.tireDeflection = 0.0;
            wheel.unsprungVel = 0.0;
            compression = Math.max(0.0, restLength() - wheel.springLength);
            springForce = TireModel.suspensionForce(rateMass, setting.naturalFreqHz(),
                    setting.dampingRatio(), compression, hardpointVel, setting.damper(), responseMass, dt);
            double nominalC = TireModel.springDamping(rateMass, setting.naturalFreqHz(),
                    setting.dampingRatio(), hardpointVel > 0.0);
            wheel.telemDampFraction = nominalC > 0.0
                    ? TireModel.effectiveDamping(nominalC, hardpointVel, setting.damper(), dt, responseMass)
                            / nominalC
                    : 1.0;
        }

        wheel.telemHardpointVel = hardpointVel;
        wheel.telemSpringForce = springForce;

        // slope correction (got it from Bullet's clipped_inv_contact_dot_suspension)
        Vector3d hitNormal = new Vector3d(cast.normal().getStepX(), cast.normal().getStepY(), cast.normal().getStepZ());
        if (cast.hitSubLevel() != null) {
            cast.hitSubLevel().logicalPose().transformNormal(hitNormal);
        }
        pose.transformNormalInverse(hitNormal);
        if (hitNormal.lengthSquared() < 1.0e-6 || hitNormal.y < 0.05) {
            hitNormal.set(0.0, 1.0, 0.0);
        } else {
            hitNormal.normalize();
        }

        wheel.telemVelNormal = localVelocity.dot(hitNormal); // hitNormal is already in body space here, so the velocity has to be rotated the same way before projecting

        springForce *= Mth.clamp(1.0 / Math.max(0.5, hitNormal.y), 1.0, 2.0);
        springForce = Math.min(springForce, Config.MAX_CORNERING_G.getAsDouble() * rateMass * 9.81);

        Vector3d springImpulse = new Vector3d(hitNormal).mul(springForce * dt);
        forceTotal.applyImpulseAtPoint(massData, hardpointJoml, springImpulse);


        // DOWNFORCE -- so add onto this with the spoilers or wings. This is just user configured for now to apply 0.06 by default for test drive purposes
        double airspeed = localVelocity.length();
        double downforce = Config.AERO_DOWNFORCE.getAsDouble() * airspeed * airspeed;
        if (downforce > 0.0) {
            forceTotal.applyImpulseAtPoint(massData, hardpointJoml, new Vector3d(0.0, -downforce * dt, 0.0));
        }

        // ================================
        // Load sensitive tire friction
        // ===================================

        double normalForce = springForce;
        if (Config.TIRE_COMPLIANCE.get()) {
            normalForce = Math.min(wheel.contactLoad,
                    Config.MAX_CORNERING_G.getAsDouble() * rateMass * 9.81);
        }
        double vLon = localVelocity.dot(forward);
        double vLat = localVelocity.dot(axle);


        // ABS releases brake pressure on a wheel when the braking slip passes a threshold toward locking up,
        // Proportional release and a default floor of 5%, disengages below 1.5m/s
        if (brakeTorque > 0.0 && Config.ABS_ENABLED.get() && Math.abs(vLon) > Config.ABS_MIN_SPEED.getAsDouble()) {
            double lockSlip = -(wheel.omega * radius - vLon) * Math.signum(vLon) / Math.max(Math.abs(vLon), 2.0);
            double threshold = Config.ABS_SLIP_THRESHOLD.getAsDouble();
            if (lockSlip > threshold) {
                brakeTorque *= Mth.clamp(1.0 - (lockSlip - threshold) / threshold, 0.05, 1.0);
            }
        }

        // per-axle tire tier so front grips better than rear, should help the turning be more natural
        TireSpec tireSpec = TireSpec.fromConfig(isFrontAxle());

        float designLoad = getTire(side).getOrDefault(CreateMotorsport.TIRE_DESIGN_LOAD, 0.0f);
        double gripMult = TireModel.loadSensitivity(normalForce, designLoad, tireSpec);

        // Thermal grip parabola peaks at optimal temp. From Speed Dreams: mu *= 1 - k*(T-Topt)^2
        // k is set so grip = coldGripFactor at ambient, factor 1.0 would be off
        double tempFactor = 1.0;
        if (Config.TIRE_THERMAL_MODEL.get()) {
            double topt = Config.TIRE_OPT_TEMP.getAsDouble();
            double span = topt - Config.TIRE_AMBIENT_TEMP.getAsDouble();
            double k = span != 0.0 ? (1.0 - Config.COLD_MU_FACTOR.getAsDouble()) / (span * span) : 0.0;
            double dT = wheel.tireTemp - topt;
            tempFactor = Mth.clamp(1.0 - k * dT * dT, 0.1, 1.0);
        }
        double effectiveMu = wheel.surfaceMu * gripMult * tempFactor;
        wheel.telemGripMult = gripMult;
        wheel.telemPeakMu = effectiveMu * tireSpec.grip(); // peak grip the tire can deliver right now

        // non driving or braked wheels take from offroad wheel mount, just track ground so it cant start creating slip force and maybe wont roll as much
        boolean powered = Math.abs(driveTorquePerWheel) > 1.0e-3 || brakeTorque > 1.0e-3;
        double forwardImpulse;
        double sideImpulse;

        double gripForce = normalForce * effectiveMu * tireSpec.grip();

        int tireModel = Config.TIRE_MODEL.getAsInt();

        if (tireModel == 3) {

            // --------------------------------------------------------------------------
            // FIALA BRUSH TIRE MODEL 3
            // Ported from Project Chrono

            if (!powered) {
                wheel.omega = vLon / radius;
            }
            double wheelSpeed = wheel.omega * radius;
            double vRef = Math.max(Math.abs(vLon), Config.SIM_LOWSPEED_REF.getAsDouble());
            double kappa = (wheelSpeed - vLon) / vRef;
            double relaxLen = Math.max(Config.FIALA_RELAX_LENGTH.getAsDouble(), 1.0e-3);
            double decayRate = vRef / relaxLen;
            double decay = Math.exp(-decayRate * dt);
            double tauEff = decayRate > 1.0e-9 ? (1.0 - decay) / decayRate : dt;
            wheel.deflAlpha = wheel.deflAlpha * decay + vLat * tauEff;
            double alpha = Math.atan2(wheel.deflAlpha, relaxLen);

            double frictionScale = effectiveMu * tireSpec.grip();
            double[] fiala = new double[2];
            TireModel.fialaForces(fiala, kappa, alpha, normalForce,
                    Config.FIALA_CSLIP.getAsDouble(), Config.FIALA_CALPHA.getAsDouble(),
                    Config.FIALA_MU_MAX.getAsDouble(), Config.FIALA_MU_MIN.getAsDouble(), frictionScale);
            double forwardForce = fiala[0];
            double lateralForce = fiala[1];

            if (!powered) {
                forwardForce = -TireModel.rollingResistance(normalForce, vLon);
            }
            forwardForce = wheel.prevLongForce + (forwardForce - wheel.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
            wheel.prevLongForce = forwardForce;

            // low speed stabilizer because it is very jittery as of now
            double speed = Math.sqrt(vLon * vLon + vLat * vLat);
            double blendSpeed = Config.SIM_LOWSPEED_BLEND_MS.getAsDouble();
            double invSideMass = massData.getInverseNormalMass(hardpointJoml, axle);
            if (blendSpeed > 1.0e-6) {
                double slipBlend = Mth.clamp(speed / blendSpeed, 0.0, 1.0);
                double cancelForce = invSideMass > 1.0e-9 ? (-vLat / invSideMass * Config.LATERAL_GRIP_FRACTION.getAsDouble()) / dt : 0.0;
                lateralForce = Mth.lerp(slipBlend, cancelForce, lateralForce);
            }

            lateralForce += camberThrust(side, isFrontAxle(), gripForce, lateralForce, vLon);

            forwardImpulse = forwardForce * dt;
            sideImpulse = lateralForce * dt;

            if (powered) {
                wheel.omega = TireModel.integrateSpin(wheel.omega, radius, wheelInertia, driveTorquePerWheel,
                        brakeTorque, forwardForce, vLon, dt);
            }
        } else if (tireModel == 2) {

            //-----------------------------------------------------------------------------------
            // PACEJKA COMBINED-SLIP TIRE MODEL 2


            if (!powered) {
                wheel.omega = vLon / radius;
            }
            double wheelSpeed = wheel.omega * radius;
            double denom = Math.max(Math.abs(vLon), 2.0);
            double slipLon = (wheelSpeed - vLon) / denom;
            double slipLat = vLat / Math.max(Math.abs(vLon), 0.05);

            // friction ellipse instead of circle for configurable lateral slipperiness
            double latGrip = Config.SIM_LATERAL_GRIP.getAsDouble();
            double slipLatEff = slipLat / latGrip;
            double s = Math.sqrt(slipLon * slipLon + slipLatEff * slipLatEff);

            double forwardForce = 0.0;
            double lateralForce = 0.0;
            if (s > 1.0e-5) {
                double fMag = normalForce * effectiveMu * tireSpec.grip()
                        * TireModel.slipCurve(Math.min(s, Config.SIM_SLIP_LIMIT.getAsDouble()),
                                tireSpec.pacejkaB(), tireSpec.pacejkaC(), tireSpec.pacejkaE());
                forwardForce = fMag * slipLon / s;
                lateralForce = -fMag * slipLat / s;
            }
            if (!powered) {
                forwardForce = -TireModel.rollingResistance(normalForce, vLon);
            }
            forwardForce = wheel.prevLongForce + (forwardForce - wheel.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
            wheel.prevLongForce = forwardForce;

            // low speed blending to make it stabilize and not jitter
            double speed = Math.sqrt(vLon * vLon + vLat * vLat);
            double slipBlend = Mth.clamp(speed / Config.SIM_LOWSPEED_BLEND_MS.getAsDouble(), 0.0, 1.0);
            double invSideMass = massData.getInverseNormalMass(hardpointJoml, axle);
            double cancelForce = invSideMass > 1.0e-9 ? (-vLat / invSideMass * Config.LATERAL_GRIP_FRACTION.getAsDouble()) / dt : 0.0;
            lateralForce = Mth.lerp(slipBlend, cancelForce, lateralForce);

            lateralForce += camberThrust(side, isFrontAxle(), gripForce, lateralForce, vLon);

            forwardImpulse = forwardForce * dt;
            sideImpulse = lateralForce * dt;

            if (powered) {
                wheel.omega = TireModel.integrateSpin(wheel.omega, radius, wheelInertia, driveTorquePerWheel,
                        brakeTorque, forwardForce, vLon, dt);
            }
        } else if (tireModel == 4) {

            // --------------------------------------------------------------------------------
            // TMEASY TIRE MODEL 4 (Rill), ported from Project Chrono's ChTMeasyTire
            // See TireModel as well for more notes copied from source

            if (!powered) {
                wheel.omega = vLon / radius;
            }
            double wheelSpeed = wheel.omega * radius;

            // Slip inputs, this part is from Chrono's ChTMeasyTire 'Synchronize', covering the slip inputs and configurable
            // transport speed floor. Chrono used a tiny vnum of like 0.01 but due to our physics substeps this should be more like 1
            double vta = Math.max(Math.abs(wheelSpeed), Config.SIM_LOWSPEED_REF.getAsDouble());
            double sx = (wheelSpeed - vLon) / vta;
            double sy = -vLat / vta;

            // Chrono uses data driven tire inputs, instead we have estimatable values, inspired by a paper Dr. Rill wrote,
            // titled: "An Engineer's Guess on Tyre Model Parameter Made Possible with Tmeasy"
            double peakForce = normalForce * effectiveMu * tireSpec.grip();
            double slideFrac = Config.TMEASY_SLIDE_GRIP.getAsDouble();
            double fxm = peakForce, fym = peakForce;
            double fxs = slideFrac * peakForce, fys = slideFrac * peakForce;
            double sxm = Config.TMEASY_SLIP_PEAK_LONG.getAsDouble();
            double sym = Config.TMEASY_SLIP_PEAK_LAT.getAsDouble();
            double slideSlip = Config.TMEASY_SLIDE_SLIP_FACTOR.getAsDouble();
            double sxs = sxm * slideSlip, sys = sym * slideSlip;
            double stiffMult = Config.TMEASY_INITIAL_STIFFNESS.getAsDouble();
            double dfx0 = stiffMult * fxm / sxm, dfy0 = stiffMult * fym / sym;

            // This part also deviates a bit from Chrono because I was reading Dr. Rill's textbook, and wanted to try
            // the refinement he suggests, its a combined slip normalization found in equations 3.129 - 3.140
            // intended to rescale the two slip values (throttle/steer) so they are more comparable for a combined slip model
            double fOverDf0X = fxm / dfx0;
            double fOverDf0Y = fym / dfy0;
            double shatX = sxm / (sxm + sym) + fOverDf0X / (fOverDf0X + fOverDf0Y);
            double shatY = sym / (sxm + sym) + fOverDf0Y / (fOverDf0X + fOverDf0Y);
            double sNx = sx / shatX;
            double sNy = sy / shatY;
            double sc = Math.hypot(sNx, sNy);
            double cphi = sc > 1.0e-9 ? sNx / sc : Math.sqrt(0.5);
            double sphi = sc > 1.0e-9 ? sNy / sc : Math.sqrt(0.5);
            double df0 = Math.hypot(dfx0 * shatX * cphi, dfy0 * shatY * sphi);
            double fm = Math.hypot(fxm * cphi, fym * sphi);
            double sm = Math.hypot(sxm / shatX * cphi, sym / shatY * sphi);
            double fs = Math.hypot(fxs * cphi, fys * sphi);
            double ss = Math.hypot(sxs / shatX * cphi, sys / shatY * sphi);
            double[] tm = new double[2];

            // this part is from Chrono's ChTMeasyTire 'Advanced' & 'tmxy_combined'
            TireModel.tmeasyCombined(tm, sc, df0, sm, fm, ss, fs);
            double f = tm[0];
            double forwardForce = sc > 1.0e-9 ? f * cphi : 0.0;
            double lateralForce = sc > 1.0e-9 ? f * sphi : 0.0;

            if (!powered) {
                forwardForce = -TireModel.rollingResistance(normalForce, vLon);
            }
            forwardForce = wheel.prevLongForce + (forwardForce - wheel.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
            wheel.prevLongForce = forwardForce;

            // This part is Chrono's ChTMeasyTire  'CombinedCoulombForces', which is TMeasy's own Dahl bristle stand-still model
            double relaxLen = Math.max(Config.FIALA_RELAX_LENGTH.getAsDouble(), 1.0e-3);
            double fc = fym;
            double fyBristle;
            if (fc > 1.0e-6) {
                double sigma0 = fc / relaxLen;
                double invSideMass = massData.getInverseNormalMass(hardpointJoml, axle);
                double effMass = invSideMass > 1.0e-9 ? 1.0 / invSideMass : 1.0;
                double sigma1 = Config.TMEASY_BRISTLE_DAMPING.getAsDouble() * 2.0 * Math.sqrt(sigma0 * effMass);
                double vWash = Math.max(Math.abs(vLat), Config.TMEASY_STANDSTILL_LEAK.getAsDouble());
                double bry = wheel.latBristle;
                double bryDot = vLat - sigma0 * bry * vWash / fc;
                fyBristle = -(sigma0 * bry + sigma1 * bryDot);
                // implicit BDF1 update of the bristle deflection (unconditionally stable)
                wheel.latBristle = (fc * bry + fc * dt * vLat) / (fc + dt * sigma0 * vWash);
                fyBristle = Mth.clamp(fyBristle, -fc, fc);
            } else {
                wheel.latBristle = 0.0;
                fyBristle = 0.0;
            }
            // smooth blend from rest to steady curve at speed, from Chrono's ChTMeasyTire  'Advance'
            double frT = Mth.clamp(Math.abs(vLon) / Config.TMEASY_STANDSTILL_SPEED.getAsDouble(), 0.0, 1.0);
            double frBlend = frT * frT * (3.0 - 2.0 * frT);
            lateralForce = Mth.lerp(frBlend, fyBristle, lateralForce);

            lateralForce += camberThrust(side, isFrontAxle(), gripForce, lateralForce, vLon);

            forwardImpulse = forwardForce * dt;
            sideImpulse = lateralForce * dt;

            if (powered) {
                wheel.omega = TireModel.integrateSpin(wheel.omega, radius, wheelInertia, driveTorquePerWheel,
                        brakeTorque, forwardForce, vLon, dt);
            }
        } else {


            // --------------------------------------------------------------------------------
            // ARCADE TIRE MODEL 1

            double tireForce;
            if (powered) {
                tireForce = TireModel.longitudinalForce(normalForce, effectiveMu, wheel.omega * radius, vLon, tireSpec);
            } else {
                wheel.omega = vLon / radius; // roll with the ground, no slip
                tireForce = -TireModel.rollingResistance(normalForce, vLon);
            }
            // Speed-dreams runs FLOAT_RELAXATION2 on the tire force to relax longitudinal force towards its target, to damp oscillation
            tireForce = wheel.prevLongForce + (tireForce - wheel.prevLongForce) * Config.TIRE_FORCE_RELAXATION.getAsDouble();
            wheel.prevLongForce = tireForce;
            forwardImpulse = tireForce * dt;

            // cancel a fraction of the lateral velocity per substep same way Bullet does
            double invSideMass = massData.getInverseNormalMass(hardpointJoml, axle);
            sideImpulse = invSideMass > 1.0e-9 ? -vLat * (1.0 / invSideMass) * Config.LATERAL_GRIP_FRACTION.getAsDouble() : 0.0;

            sideImpulse += camberThrust(side, isFrontAxle(), gripForce, sideImpulse / dt, vLon) * dt;

            double maxImpulse = normalForce * effectiveMu * tireSpec.grip() * dt;
            double ellipseScale = TireModel.frictionEllipseScale(forwardImpulse, sideImpulse, maxImpulse);
            forwardImpulse *= ellipseScale;
            sideImpulse *= ellipseScale;

            if (powered) {
                wheel.omega = TireModel.integrateSpin(wheel.omega, radius, wheelInertia, driveTorquePerWheel,
                        brakeTorque, forwardImpulse / dt, vLon, dt);
            }
        }

        //-----------------------------------------------------------------------


        // This is for more detailed grip HUD and is unused right now
        double gripCircle = gripForce * dt;
        double demand = Math.hypot(forwardImpulse, sideImpulse);
        wheel.telemGripUse = gripCircle > 1.0e-9 ? demand / gripCircle : 0.0;
        wheel.telemGripLon = gripCircle > 1.0e-9 ? forwardImpulse / gripCircle : 0.0;
        wheel.telemGripLat = gripCircle > 1.0e-9 ? sideImpulse / gripCircle : 0.0;
        wheel.latUse = gripCircle > 1.0e-9 ? Math.abs(sideImpulse) / gripCircle : 0.0;

        // recording the resolved physics for csv logging
        wheel.telemLoad = normalForce;
        wheel.telemVLon = vLon;
        wheel.telemVLat = vLat;
        wheel.telemSlipRatio = (wheel.omega * radius - vLon) / Math.max(Math.abs(vLon), 2.0);
        wheel.telemSlipAngleRad = Math.atan2(vLat, Math.max(Math.abs(vLon), 0.05));
        wheel.telemLongForce = forwardImpulse / dt;
        wheel.telemLatForce = sideImpulse / dt;
        wheel.telemWheelSpeed = wheel.omega * radius;
        wheel.telemCompression = compression;
        wheel.telemBrakeTorque = brakeTorque;

        // Tire thermal update (Speed Dreams simuv4)
        if (Config.TIRE_THERMAL_MODEL.get()) {
            double slipVelLon = wheel.omega * radius - vLon;
            double frictionWork = (Math.abs((forwardImpulse / dt) * slipVelLon)
                    + Math.abs((sideImpulse / dt) * vLat)) * dt; // this would be joules this substep
            wheel.tireTemp += frictionWork * Config.TIRE_HEATING_RATE.getAsDouble();
            double ambient = Config.TIRE_AMBIENT_TEMP.getAsDouble();
            wheel.tireTemp -= Config.TIRE_COOLING_RATE.getAsDouble()
                    * (1.0 + TIRE_SPEED_COOL * Math.abs(vLon)) * (wheel.tireTemp - ambient) * dt;
        }


        // Forward forward at the wheel center, prevents barrel rolls. Thanks again bullet
        Vector3d contactPoint = new Vector3d(hardpointJoml).sub(0.0, wheel.springLength, 0.0);
        forceTotal.applyImpulseAtPoint(massData, contactPoint, new Vector3d(forward).mul(forwardImpulse));

        Vector3d sidePoint = new Vector3d(contactPoint);
        if (massData.getCenterOfMass() != null) {
            double heightAboveCom = contactPoint.y - massData.getCenterOfMass().y();
            double rollInfluence = Config.ROLL_INFLUENCE.getAsDouble();
            sidePoint.y -= heightAboveCom * (1.0 - rollInfluence);
        }
        forceTotal.applyImpulseAtPoint(massData, sidePoint, new Vector3d(axle).mul(sideImpulse));

        recordTireSmoke(wheel, pose, contactPoint, forward, axle, vLon, vLat, radius, cast);
        return true;
    }

    // Whether to smoke tires, for the server tick. Keeping it separate from force solver
    private void recordTireSmoke(WheelState wheel, Pose3d pose, Vector3d contactPoint, Vector3d forward,
                                 Vector3d axle, double vLon, double vLat, double radius, TerrainCastResult cast) {
        if (!Config.TIRE_SMOKE.get()) {
            wheel.smokeSeverity = 0.0;
            wheel.skidFreshContact = false;
            return;
        }

        // recorded every grounded tick for the skid ribbon
        Vector3d groundLocal = new Vector3d(contactPoint).sub(0.0, radius - 0.05, 0.0);
        Vec3 world = pose.transformPosition(JOMLConversion.toMojang(groundLocal));
        wheel.smokeX = world.x;
        wheel.smokeY = world.y;
        wheel.smokeZ = world.z;
        wheel.smokeRadius = radius;
        wheel.smokeSurface = cast.hitBlock() != null ? level.getBlockState(cast.hitBlock()) : null;

        Vector3d worldAxle = new Vector3d(axle);
        pose.transformNormal(worldAxle);
        double axleLen = worldAxle.length();
        if (axleLen > 1.0e-6) {
            worldAxle.div(axleLen);
        }
        wheel.skidAxleX = worldAxle.x;
        wheel.skidAxleY = worldAxle.y;
        wheel.skidAxleZ = worldAxle.z;
        wheel.skidFreshContact = true;

        double speed = Math.hypot(vLon, vLat);
        if (speed < Config.TIRE_SMOKE_MIN_SPEED.getAsDouble()) {
            wheel.smokeSeverity = 0.0;
            return;
        }
        // taking the worse of lateral or longitudinal slip to determine if slipping
        double slipLong = Math.abs(wheel.telemSlipRatio);
        double slipLat = Math.abs(Math.sin(wheel.telemSlipAngleRad));
        double slip = Math.max(slipLong, slipLat);
        double threshold = Config.TIRE_SMOKE_SLIP_THRESHOLD.getAsDouble();
        if (slip < threshold) {
            wheel.smokeSeverity = 0.0;
            return;
        }
        double severity = Mth.clamp((slip - threshold) / (1.0 - threshold), 0.0, 1.0);

        // temperature affects smoke levels
        double heatBoost = Config.TIRE_SMOKE_HEAT_BOOST.getAsDouble();
        double heat = Mth.clamp((wheel.tireTemp - SMOKE_HEAT_COLD_C) / (SMOKE_HEAT_HOT_C - SMOKE_HEAT_COLD_C), 0.0, 1.0);
        wheel.smokeSeverity = severity * ((1.0 - heatBoost) + heatBoost * heat);

        Vector3d drift = new Vector3d(forward).mul(vLon - wheel.omega * radius).add(new Vector3d(axle).mul(vLat));
        pose.transformNormal(drift);
        double len = drift.length();
        if (len > 1.0e-3) {
            drift.div(len).mul(SMOKE_DRIFT_SPEED);
        } else {
            drift.set(0.0, 0.0, 0.0);
        }
        wheel.smokeVx = drift.x;
        wheel.smokeVy = drift.y;
        wheel.smokeVz = drift.z;
    }

    // wheel mount gives slight friction even at 0
    private static double fudgeFriction(double realValue) {
        return realValue < 1.0 ? 0.1 + 0.9 * realValue : realValue;
    }

    // Camber thrust from Dr. Rill's Road Vehicle Dynamics 3.3.7.3
    private double camberThrust(WheelSide side, boolean frontAxle, double peakForce,
                                double currentLateralForce, double vLon) {
        double camberDeg = frontAxle ? Config.CAMBER_FRONT.getAsDouble() : Config.CAMBER_REAR.getAsDouble();
        double stiffness = Config.CAMBER_STIFFNESS.getAsDouble();
        if (camberDeg == 0.0 || stiffness <= 0.0 || peakForce <= 0.0) {
            return 0.0;
        }
        double sideFactor = side == WheelSide.LEFT ? 1.0 : -1.0;         // thrust toward the car centre for -camber
        double speedFade = Mth.clamp(Math.abs(vLon) / CAMBER_FADE_SPEED, 0.0, 1.0);
        double latUse = Mth.clamp(Math.abs(currentLateralForce) / peakForce, 0.0, 1.0);
        double slipFade = 1.0 - latUse * latUse;                        // Rill's fos: strong low-slip, gone at the limit
        return -Math.sin(Math.toRadians(camberDeg)) * stiffness * peakForce * sideFactor * speedFade * slipFade;
    }

    // =========================================================================
    // TERRAIN RAYCAST
    // =====================================================================

    private record TerrainCastResult(double distance, @NotNull Direction normal,
                                     @Nullable SubLevel hitSubLevel, @Nullable BlockPos hitBlock) {
    }

    // Tyre enveloping method of raycasting should work much better for offroading, but I still need to work on this more
    // Wheel with radius r, resting on a profile h(x) has center at:
    //     z = max over x of [ h(x) + sqrt(r^2 - x^2) ]
    private TerrainCastResult castToTerrain(Vec3 wheelCenter, Vector3d forward, Pose3dc pose, double radius,
                                            WheelState wheel, double dt) {
        double half = radius * FOOTPRINT_FRACTION;
        double[] offsets = new double[MAX_FOOTPRINT_SAMPLES];
        int sampleCount = footprintOffsets(wheelCenter, forward, pose, half, offsets);

        boolean hit = false;
        double minRigid = 0.0;
        double centerDist = 0.0;
        double centerX = Double.MAX_VALUE;
        Direction minNormal = Direction.UP;
        SubLevel minHitSubLevel = null;
        BlockPos minHitBlock = null;

        Vec3 forwardVec = JOMLConversion.toMojang(forward);
        for (int i = 0; i < sampleCount; i++) {
            double x = offsets[i];
            Vec3 origin = wheelCenter.add(forwardVec.scale(x));

            ClipContext clipContext = new ClipContext(origin, origin.subtract(0.0, 5.0, 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
            ((ClipContextExtension) clipContext).sable$setIgnoredSubLevel(Sable.HELPER.getContaining(this));
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
            pose.transformNormalInverse(hitNormal);
            if (hitNormal.dot(0.0, 1.0, 0.0) < 0.5) {
                continue;
            }

            double reach = Math.sqrt(Math.max(0.0, radius * radius - x * x));
            double rigid = dist - reach;

            if (Math.abs(x) < centerX) {
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
            this.lastCastLift = 0.0;
            return new TerrainCastResult(5.0, Direction.UP, null, null);
        }
        this.lastCastLift = centerDist - (minRigid + radius);

        double assist = offroadAssistLift(wheelCenter, forward, pose, radius, minRigid, wheel, dt);
        this.lastAssistLift = assist;
        return new TerrainCastResult(minRigid - assist + radius, minNormal, minHitSubLevel, minHitBlock);
    }


    private double offroadAssistLift(Vec3 wheelCenter, Vector3d forward, Pose3dc pose, double radius,
                                     double rigidHere, WheelState wheel, double dt) {
        TerrainAssistSpec spec = setting.terrainAssist();
        if (!spec.enabled() || !Config.OFFROADING_ASSIST.getAsBoolean()) {
            wheel.assistLift = 0.0;
            return 0.0;
        }
        double speed = this.cachedCarSpeed;
        double maxSlope = speed > 0.05 ? spec.maxVerticalRate() / speed : Double.MAX_VALUE;
        if (!Double.isFinite(maxSlope) || maxSlope >= 1.0) {
            // assist does nothing for more than 45 degree slope, so a 1 block step
            return releaseAssist(wheel, spec, dt);
        }
        double maxClimb = radius * spec.maxClimbFraction();
        double lookAhead = spec.lookAheadBlocks();
        double best = 0.0;
        Vec3 forwardVec = JOMLConversion.toMojang(forward);
        for (int i = 1; i <= ASSIST_PROBES; i++) {
            double d = lookAhead * i / ASSIST_PROBES;
            double rigidAhead = probeGroundRigid(wheelCenter.add(forwardVec.scale(d)), pose, radius);
            if (Double.isNaN(rigidAhead)) {
                continue;
            }
            double rise = rigidHere - rigidAhead;
            if (rise <= 0.0 || rise > maxClimb) {
                continue;
            }
            best = Math.max(best, rise - maxSlope * d);
        }
        return rateLimitAssist(wheel, spec, dt, Math.max(0.0, best));
    }

    // moves stored lift toward target no more than maxVerticalRate
    private static double rateLimitAssist(WheelState wheel, TerrainAssistSpec spec, double dt,
                                          double target) {
        double step = spec.maxVerticalRate() * dt;
        wheel.assistLift = target > wheel.assistLift
                ? Math.min(target, wheel.assistLift + step)
                : Math.max(target, wheel.assistLift - step * ASSIST_RELEASE_RATIO);
        return wheel.assistLift;
    }

    private static double releaseAssist(WheelState wheel, TerrainAssistSpec spec, double dt) {
        return rateLimitAssist(wheel, spec, dt, 0.0);
    }

    private double probeGroundRigid(Vec3 origin, Pose3dc pose, double radius) {
        ClipContext clipContext = new ClipContext(origin, origin.subtract(0.0, 5.0, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        ((ClipContextExtension) clipContext).sable$setIgnoredSubLevel(Sable.HELPER.getContaining(this));
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

    // ===============================================
    // Engine interface
    // ===================

    // called by an engine block on the same sublevel as suspension. Once per game tick. Torque is per wheel
    public void applyDriveTorque(double torquePerWheel, long gameTime) {
        if (gameTime == driveTorqueGameTime) {
            driveTorquePerWheel += torquePerWheel;
        } else {
            driveTorquePerWheel = torquePerWheel;
            driveTorqueGameTime = gameTime;
        }
    }

    public double peakDrivenOmega(int sign) {
        double peak = 0.0;
        for (WheelSide side : WheelSide.values()) {
            if (hasTire(side)) {
                peak = Math.max(peak, getWheel(side).omega * sign);
            }
        }
        return peak;
    }

    // Traction control caps torque with this directly to be faster
    public double remainingTractionForce() {
        double total = 0.0;
        for (WheelSide side : WheelSide.values()) {
            if (!hasTire(side)) {
                continue;
            }
            WheelState wheel = getWheel(side);
            if (!wheel.grounded) {
                continue;
            }
            double lat = Mth.clamp(wheel.latUse, 0.0, 1.0);
            total += wheel.contactLoad * getPeakMu(side) * Math.sqrt(Math.max(0.0, 1.0 - lat * lat));
        }
        return total;
    }

    public double peakLateralUse() {
        double peak = 0.0;
        for (WheelSide side : WheelSide.values()) {
            if (hasTire(side)) {
                peak = Math.max(peak, getWheel(side).latUse);
            }
        }
        return peak;
    }

    public double averageDrivenOmega(int sign) {
        double sum = 0.0;
        int count = 0;
        for (WheelSide side : WheelSide.values()) {
            if (hasTire(side)) {
                sum += getWheel(side).omega;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count * sign;
    }

    public boolean hasAnyTire() {
        return hasTire(WheelSide.LEFT) || hasTire(WheelSide.RIGHT);
    }

    public boolean hasTire(WheelSide side) {
        return getTire(side).get(OffroadDataComponents.TIRE) != null;
    }

    // ===============================================================
    // Tires
    // =============

    public ItemStack getTire(WheelSide side) {
        return tires.get(side.ordinal());
    }

    public boolean installTire(WheelSide side, ItemStack stack) {
        if (!getTire(side).isEmpty() || stack.get(OffroadDataComponents.TIRE) == null) {
            return false;
        }
        tires.set(side.ordinal(), stack.copyWithCount(1));
        notifyUpdate();
        invalidateRenderBoundingBox();
        return true;
    }

    public ItemStack removeTire(WheelSide side) {
        ItemStack removed = tires.get(side.ordinal());
        if (!removed.isEmpty()) {
            tires.set(side.ordinal(), ItemStack.EMPTY);
            getWheel(side).omega = 0.0;
            notifyUpdate();
            invalidateRenderBoundingBox();
        }
        return removed;
    }

    public NonNullList<ItemStack> getTires() {
        return tires;
    }

    // =======================================================
    // accessors
    // =====================================


    public SuspensionSetting getSetting() {
        return setting;
    }

    // server-side, current steer angle of this axle (rad)
    public double getSteerAngleRad() {
        return chasingSteer;
    }

    public double getTireRadius() {
        for (WheelSide side : WheelSide.values()) {
            TireLike tire = getTire(side).get(OffroadDataComponents.TIRE);
            if (tire != null) {
                return tire.radius();
            }
        }
        return 0.0;
    }

    public SuspensionSetting cycleSetting() {
        setting = setting.next();
        notifyUpdate();
        return setting;
    }

    public Direction getFacing() {
        return getBlockState().getValue(SuspensionBlock.FACING);
    }

    public Direction getSideDirection(WheelSide side) {
        Direction facing = getFacing();
        return side == WheelSide.LEFT ? facing.getCounterClockWise() : facing.getClockWise();
    }

    public WheelState getWheel(WheelSide side) {
        return side == WheelSide.LEFT ? leftWheel : rightWheel;
    }


    // Renderer accessors
    public float getLerpedSpringLength(WheelSide side, float partialTicks) {
        WheelState w = getWheel(side);
        return (float) Mth.lerp(partialTicks, w.lastClientSpringLength, w.clientSpringLength);
    }

    public static final double RENDER_REST_RAISE = 0.8;

    private double springToRaise(double springLength) {
        double animSpring = springLength - breakawayDrop();
        double droopSpan = MAX_DROOP_RENDER + animatedLiftHeight();
        if (animSpring >= REST_LENGTH) {
            double t = Mth.clamp((animSpring - REST_LENGTH) / droopSpan, 0.0, 1.0);
            return RENDER_REST_RAISE * (1.0 - t);
        }
        double t = Mth.clamp((REST_LENGTH - animSpring) / MAX_TRAVEL, 0.0, 1.0);
        return RENDER_REST_RAISE + (1.0 - RENDER_REST_RAISE) * t;
    }

    public float getLerpedRaise(WheelSide side, float partialTicks) {
        return (float) springToRaise(getLerpedSpringLength(side, partialTicks));
    }

    public float getLerpedAngle(WheelSide side, float partialTicks) {
        WheelState w = getWheel(side);
        return (float) Mth.lerp(partialTicks, w.lastAngle, w.angle);
    }

    public float getLerpedSteer(float partialTicks) {
        return (float) Mth.lerp(partialTicks, lastChasingSteer, chasingSteer);
    }


    // Ackermann geometry for the steering; >0 = anti-Ackermann (outer sharper), <0 = classic Ackermann (inner sharper), 0 = parallel
    public double wheelSteerAngle(double base, WheelSide side) {
        double k = Config.STEERING_ANTI_ACKERMANN.getAsDouble();
        if (k == 0.0 || base == 0.0) {
            return base;
        }
        boolean rightIsOuter = base > 0.0;
        boolean isOuter = (side == WheelSide.RIGHT) == rightIsOuter;
        boolean larger = (k > 0.0) == isOuter;
        double half = 0.5 * Math.abs(k);
        double t = Math.tan(Math.abs(base));
        double denom = Math.max(0.3, larger ? 1.0 - half * t : 1.0 + half * t);
        return Math.copySign(Math.atan(t / denom), base);
    }

    public float getLerpedSteer(WheelSide side, float partialTicks) {
        return (float) wheelSteerAngle(getLerpedSteer(partialTicks), side);
    }

    // Tire placement animation
    public float getLerpedDeploy(WheelSide side, float partialTicks) {
        WheelState w = getWheel(side);
        return (float) Mth.lerp(partialTicks, w.lastDeploy, w.deploy);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5, 2.0, 2.5);
    }

    // ==============================================================
    // NBT
    // ===============================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ContainerHelper.saveAllItems(tag, tires, registries);
        CompoundTag controlsTag = new CompoundTag();
        ContainerHelper.saveAllItems(controlsTag, controlItems, registries);
        tag.put("Controls", controlsTag);
        tag.putString("Setting", setting.name());
        tag.putBoolean("FrontAxle", frontAxle);
        tag.putInt("LiftSteps", liftSteps);
        if (clientPacket) {
            tag.putDouble("SteerSignal", steerSignal);
            tag.putBoolean("SteerAssistOff", steerAssistOff);
            tag.putFloat("LeftOmega", (float) leftWheel.omega);
            tag.putFloat("RightOmega", (float) rightWheel.omega);
            tag.putFloat("LeftSpring", (float) leftWheel.springLength);
            tag.putFloat("RightSpring", (float) rightWheel.springLength);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int slot = 0; slot < tires.size(); slot++) {
            tires.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, tires, registries);

        for (int slot = 0; slot < CONTROL_SLOT_COUNT; slot++) {
            controlItems.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag.getCompound("Controls"), controlItems, registries);
        for (int slot = 0; slot < CONTROL_SLOT_COUNT; slot++) {
            controls.setItem(slot, controlItems.get(slot));
        }
        refreshLinkNetwork();

        try {
            setting = SuspensionSetting.valueOf(tag.getString("Setting"));
        } catch (IllegalArgumentException ignored) {
            setting = SuspensionSetting.RACE;
        }
        frontAxle = tag.getBoolean("FrontAxle");
        liftSteps = Mth.clamp(tag.getInt("LiftSteps"), 0, MAX_LIFT_STEPS);
        if (clientPacket) {
            steerSignal = tag.getDouble("SteerSignal");
            steerAssistOff = tag.getBoolean("SteerAssistOff");
            leftWheel.omega = tag.getFloat("LeftOmega");
            rightWheel.omega = tag.getFloat("RightOmega");
            leftWheel.springLength = tag.getFloat("LeftSpring");
            rightWheel.springLength = tag.getFloat("RightSpring");
        }
    }
}
