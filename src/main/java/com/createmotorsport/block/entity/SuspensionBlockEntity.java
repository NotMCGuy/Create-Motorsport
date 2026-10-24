package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.SuspensionBlock;
import com.createmotorsport.effects.WheelEffects;
import com.createmotorsport.physics.MassScale;
import com.createmotorsport.physics.control.SteerAssist;
import com.createmotorsport.physics.differential.AxleDiff;
import com.createmotorsport.physics.spec.DifferentialSpec;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.spec.SuspensionSpec;
import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.physics.suspension.UnsprungSolver;
import com.createmotorsport.physics.terrain.TerrainAssist;
import com.createmotorsport.physics.vehicle.*;
import com.createmotorsport.physics.world.Gravity;
import com.createmotorsport.physics.world.Surfaces;
import com.createmotorsport.physics.world.TerrainCast;
import com.createmotorsport.redstone.LinkReceivers;
import com.createmotorsport.registry.*;
import com.createmotorsport.telemetry.WheelTelemetry;
import com.createmotorsport.tune.SpecSlot;
import com.createmotorsport.tune.Tunable;
import com.createmotorsport.vehicle.AssembledCar;
import com.createmotorsport.vehicle.CarAssembler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * One axle and its two wheel corners
 * Sable calls sable$physicsTick each substep, it gathers each CornerInput, runs Corner.step and queues the impulses
 * for AssembledCar to flush. AssembledCar hands it the drive through setDrive
 *
 * HAS TO BE A SABLE ACTOR SO THAT AssembledCar.rebuild FINDS IT
 * see vehicle/AssembledCar for a checklist when adding new kinds of Block Entities for a car
 */
public class SuspensionBlockEntity extends SmartBlockEntity implements com.createmotorsport.menu.PartPanel, BlockEntitySubLevelActor, GeoBlockEntity,
        Tunable {

    // ==========================================
    // CONSTANTS / TYPES
    // ==========================================

    // see ConfiguredDrops
    public static final java.util.Set<String> CONFIGURATION_KEYS = java.util.Set.of(
            "Setup", "Differential", "SetupOverrides", "DifferentialOverrides", "FrontAxle", "Steered", "Controls");

    public static final double REST_LENGTH = 3.5 / 16.0;
    private static final double BUMP_STOP_GAP = 0.04;
    public static final double MAX_TRAVEL = REST_LENGTH - BUMP_STOP_GAP;
    public static final double MAX_DROOP_RENDER = 0.15;
    private static final double GROUND_MARGIN = 0.15;

    public static final int TRACK_MIN_PX = 16;
    public static final int TRACK_MAX_PX = 192;
    public static final int TRACK_DEFAULT_PX = 50;
    public static final double MAX_LIFT = 0.25 + MAX_DROOP_RENDER;
    public static final int LIFT_STEPS = 8;
    public static final double LIFT_STEP_HEIGHT = MAX_LIFT / LIFT_STEPS;

    private static final int LIFT_STEPS_SANITY = 1000;

    // physics and sync
    private static final double GRAVITY_READ_DIVISOR = 2.1; // this is a magic number from Sable's ServerSubLevel.prePhysicsTick that I need to fix some phantom force in the solver
    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final CompoundTag NO_TIRE_OVERRIDES = new CompoundTag();

    // redstone link channels
    public static final SteerChannel[] CHANNELS = SteerChannel.values();
    public static final int CONTROL_SLOT_COUNT = CHANNELS.length * 2;

    public static int channelSlotA(SteerChannel channel) {
        return channel.ordinal() * 2;
    }

    public static int channelSlotB(SteerChannel channel) {
        return channel.ordinal() * 2 + 1;
    }

    public enum WheelSide {
        LEFT, RIGHT
    }

    public static class WheelState extends CornerState {
        WheelState() {
            super(REST_LENGTH);
        }

        // interpolation (client)
        double clientSpringLength = REST_LENGTH;
        double lastClientSpringLength = REST_LENGTH;
        double angle;
        double lastAngle;

        // tire placing animation (client), 0 = none, 1 = deployed
        double deploy;
        double lastDeploy;

        // sync
        double syncedOmega;
        double syncedSpringLength = REST_LENGTH;

        final WheelEffects.State fx = new WheelEffects.State();
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

    // ==========================================
    // STATE
    // ==========================================

    private final NonNullList<ItemStack> tires = NonNullList.withSize(2, ItemStack.EMPTY);
    private final WheelState leftWheel = new WheelState();
    private final WheelState rightWheel = new WheelState();

    private boolean frontAxle; //defaults rear for now
    @Nullable
    private Boolean steered;

    private final SpecSlot<SuspensionSpec> setupSlot;
    private final SpecSlot<DifferentialSpec> differentialSlot = new SpecSlot<>("differential", "Differential",
            SpecRegistries.DIFFERENTIAL, SpecCodecs.DIFFERENTIAL_SPEC, SpecRegistries.BUILT_IN_DIFFERENTIALS,
            SpecRegistries::fittedDifferential, "Differential", SpecRegistries.DIFF_LSD);
    private SuspensionSpec setup = SuspensionSpec.RACE;
    private DifferentialSpec differential = DifferentialSpec.DEFAULT;
    private final SpecOverrides.Memo<TireSpec> leftCompoundMemo = new SpecOverrides.Memo<>();
    private final SpecOverrides.Memo<TireSpec> rightCompoundMemo = new SpecOverrides.Memo<>();

    // packSetup cache
    private SuspensionSpec resolvedFrom;
    private boolean resolvedFront;
    private SuspensionSpec resolvedPack;

    // Ride height and track as steps
    private int liftSteps;
    private int trackWidthPx = TRACK_DEFAULT_PX;


    private double sprungMassPerWheel = 1.0;
    private double cachedCarMass = MassScale.REFERENCE_CAR_MASS;
    private int cachedWheelCount = 4;
    private double cachedCarSpeed;
    private double cachedGravity = Gravity.DEFAULT;
    private double cachedWheelbase;                          //wheelbase synced to client for Ackermann
    private TerrainAssist.Level cachedTerrainAssist = TerrainAssist.Level.NORMAL;  // server only drivers choice for terrain assist
    private boolean driftDiffMode;
    private final Corner.Modes modes = new Corner.Modes();

    // drive, brake and steering
    private final AxleDiff drive = new AxleDiff();
    private long driveTorqueGameTime = Long.MIN_VALUE;
    private double brake01;
    private final AxleSteering steering = new AxleSteering();
    private final ForceTotal forceTotal = new ForceTotal();

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
    private final LinkReceivers links = new LinkReceivers(this, controls, CHANNELS.length,
            i -> channelSlotA(CHANNELS[i]), i -> channelSlotB(CHANNELS[i]));
    private double driverSteer; // -15 to 15, + is left
    private double driverBrake; // 0 to 15
    private long driverControlTime = Long.MIN_VALUE;

    // sync and client
    private int syncCooldown;
    private boolean syncDirty;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    // ==========================================
    // CONSTRUCTION / LIFECYCLE
    // ==========================================

    public SuspensionBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), pos, state);
        this.setupSlot = new SpecSlot<>("setup", "Setup", SpecRegistries.SUSPENSION_SETUP, SpecCodecs.SUSPENSION_SPEC,
                SpecRegistries.BUILT_IN_SETUPS, this::resolveSetup, "Setup", SuspensionSetting.defaultFor(state));
        for (int slot = 0; slot < CONTROL_SLOT_COUNT; slot++) {
            controls.setItem(slot, controlItems.get(slot));
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            refreshLinkNetwork();
        }
        CarAssembler.memberChanged(level);
    }

    @Override
    public void remove() {
        removeLinks();
        CarAssembler.memberChanged(level);
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        removeLinks();
        CarAssembler.memberChanged(level);
        super.onChunkUnloaded();
    }

    // ==========================================
    // PART SCREEN / SETUP
    // ==========================================

    @Override
    public com.createmotorsport.menu.PartKind partKind() {
        return com.createmotorsport.menu.PartKind.SUSPENSION;
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> statusLines() {
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(net.minecraft.network.chat.Component.literal("Setup: " + getSetupId().location().getPath()));
        lines.add(net.minecraft.network.chat.Component.literal((isFrontAxle() ? "Front axle" : "Rear axle") + ", "
                + (isSteered() ? "steered" : "fixed") + ", " + (isDriven() ? "driven" : "free")));
        if (isDriven()) {
            lines.add(net.minecraft.network.chat.Component.literal("Differential: "
                    + getDifferential().type().id()));
        }
        for (WheelSide side : WheelSide.values()) {
            ItemStack tire = getTire(side);
            lines.add(net.minecraft.network.chat.Component.literal((side == WheelSide.LEFT ? "Left: " : "Right: "))
                    .append(tire.isEmpty() ? net.minecraft.network.chat.Component.literal("no tyre")
                            : tire.getHoverName()));
        }
        return lines;
    }

    public boolean isFrontAxle() {
        return frontAxle;
    }

    // which end of the car this axle is on, for the wheelbase
    public void toggleAxleEnd() {
        if (level == null || level.isClientSide) {
            return;
        }
        frontAxle = !frontAxle;
        refreshSetup();
        setChanged();
        sendData();
    }

    public boolean isSteered() {
        return steered != null ? steered : frontAxle;
    }

    public void toggleSteering() {
        if (level == null || level.isClientSide) {
            return;
        }
        steered = !isSteered();
        setChanged();
        sendData();
    }

    // Driven as in receives torque from the engine, not as in if a player is driving
    public void toggleDriven() {
        if (level == null || level.isClientSide) {
            return;
        }
        setSetupOverride("driven", isDriven() ? "false" : "true");
    }

    public net.minecraft.resources.ResourceKey<SuspensionSpec> getSetupId() {
        return setupSlot.id();
    }

    public String getSetupName() {
        return SpecRegistries.displayName(setupSlot.id().location());
    }

    public SuspensionSpec getSetup() {
        return setup;
    }

    public String cycleSetting() {
        return cycleSetting(1);
    }

    public String cycleSetting(int step) {
        setupSlot.cycle(registryAccess(), step);
        refreshSetup();
        notifyUpdate();
        return getSetupName();
    }

    public String cycleDifferential() {
        return cycleDifferential(1);
    }

    public String cycleDifferential(int step) {
        differentialSlot.cycle(registryAccess(), step);
        refreshSetup();
        notifyUpdate();
        return SpecRegistries.displayName(differentialSlot.id().location());
    }

    public net.minecraft.resources.ResourceKey<DifferentialSpec> getDifferentialId() {
        return differentialSlot.id();
    }

    public DifferentialSpec getDifferential() {
        return differential;
    }

    public boolean isDriven() {
        SuspensionSpec spec = this.setup != null ? this.setup : packSetup();
        return spec.drivenOr(!frontAxle);
    }

    public SuspensionSpec packSetup() {
        return setupSlot.pack(registryAccess());
    }

    private SuspensionSpec resolveSetup(@Nullable net.minecraft.core.RegistryAccess access,
                                        net.minecraft.resources.ResourceKey<SuspensionSpec> key) {
        SuspensionSpec pack = SpecRegistries.fittedSetup(access, key, SuspensionSetting.fallbackFor(key));
        if (pack.driven().isPresent()) {
            return pack;
        }
        if (pack != resolvedFrom || frontAxle != resolvedFront) {
            resolvedFrom = pack;
            resolvedFront = frontAxle;
            resolvedPack = pack.withDriven(!frontAxle);
        }
        return resolvedPack;
    }

    public DifferentialSpec packDifferential() {
        return differentialSlot.pack(registryAccess());
    }

    public CompoundTag getSetupOverrides() {
        return setupSlot.overrides();
    }

    public CompoundTag getDifferentialOverrides() {
        return differentialSlot.overrides();
    }

    @Nullable
    public String setSetupOverride(String path, String value) {
        return tuneSection("setup").set(path, value);
    }

    public void clearSetupOverride(String path) {
        tuneSection("setup").clear(path);
    }

    @Nullable
    public String setDifferentialOverride(String path, String value) {
        Tunable.Section<?> section = tuneSection("differential");
        return section == null ? "this axle isnt driven, so it has no differential" : section.set(path, value);
    }

    public void clearDifferentialOverride(String path) {
        Tunable.Section<?> section = tuneSection("differential");
        if (section != null) {
            section.clear(path);
        }
    }

    @Override
    public List<Tunable.Section<?>> tuneSections() {
        Tunable.Section<?> setupSection = setupSlot.section(registryAccess(), this::overridesChanged,
                this::cycleSetting);
        Tunable.Section<?> differentialSection = differentialSlot.section(registryAccess(), this::overridesChanged,
                this::cycleDifferential);
        return isDriven() ? List.of(setupSection, differentialSection) : List.of(setupSection);
    }

    private void overridesChanged() {
        refreshSetup();
        setChanged();
        sendData();
    }

    private void refreshSetup() {
        this.setup = setupSlot.fitted(registryAccess());
        this.differential = differentialSlot.fitted(registryAccess());
        int maxLift = maxLiftSteps();
        int specLift = Mth.clamp((int) Math.round(setup.rideHeight() / LIFT_STEP_HEIGHT), 0, maxLift);
        int specTrack = Mth.clamp((int) Math.round(setup.trackWidth() * 16.0), TRACK_MIN_PX, TRACK_MAX_PX);
        if (specLift != this.liftSteps || specTrack != this.trackWidthPx) {
            this.liftSteps = specLift;
            this.trackWidthPx = specTrack;
            syncDirty = true;
        }
    }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.core.RegistryAccess registryAccess() {
        return this.level == null ? null : this.level.registryAccess();
    }

    // ==========================================
    // RIDE HEIGHT / TRACK / WHEEL OFFSET
    // ==========================================

    public boolean isLiftOverridden() {
        return SpecOverrides.isSet(setupSlot.overrides(), "ride_height");
    }

    public boolean isTrackOverridden() {
        return SpecOverrides.isSet(setupSlot.overrides(), "track_width");
    }

    public boolean isWheelOffsetOverridden() {
        return SpecOverrides.isSet(setupSlot.overrides(), "wheel_offset");
    }

    public double trackWidth() {
        return this.trackWidthPx / 16.0;
    }

    public double trackHalfWidth() {
        return this.trackWidthPx / 32.0;
    }

    // fore/aft adjustment requested by someone for building purposes. Wont line up with the axle unless you replace the art with something offset the same way
    public double wheelOffset() {
        return Mth.clamp(setup.wheelOffset(), -SuspensionSpec.MAX_WHEEL_OFFSET, SuspensionSpec.MAX_WHEEL_OFFSET);
    }

    public Vec3 axleCentre() {
        Direction facing = getFacing();
        double offset = wheelOffset();
        return Vec3.atCenterOf(worldPosition).add(facing.getStepX() * offset, 0.0, facing.getStepZ() * offset);
    }

    public double liftHeight() {
        return liftSteps * LIFT_STEP_HEIGHT;
    }

    private static double liftHeight(int steps) {
        return Math.round(steps * LIFT_STEP_HEIGHT * 1.0e6) / 1.0e6;
    }

    // in steps, 24 for every shipped setup
    private int maxLiftSteps() {
        return Math.max(0, (int) Math.round(setup.maxRideHeight() / LIFT_STEP_HEIGHT));
    }

    private double restLength() {
        return REST_LENGTH + liftHeight();
    }

    // ==========================================
    // GAME TICK
    // ==========================================

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
        AssembledCar car = CarAssembler.of(this);
        if (car != null && car.steps(this)) {
            // after the driver, so steer and brake are this tick's
            car.gameTick(level);
            return;
        }
        gameStep();
    }

    // the car's step calls it, or the tick does for a loose axle
    public void gameStep() {
        refreshSetup();
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
        if (steering.setSignal(newSteer)) {
            syncDirty = true;
        }
        brake01 = newBrake;

        steering.setLock(frontSlipLock());
        steering.chase(maxSteerDegrees());


        // torque stops when engine stops ticking
        if (level.getGameTime() - driveTorqueGameTime > 3) {
            drive.clearDrive();
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
            WheelEffects.emit(serverLevel, worldPosition, leftWheel.fx, rightWheel.fx);
        }
    }

    private boolean wheelStateChangedEnough() {
        for (WheelState w : new WheelState[]{leftWheel, rightWheel}) {
            if (Math.abs(w.omega - w.syncedOmega) > 0.4 || Math.abs(w.springLength - w.syncedSpringLength) > 0.015) {
                return true;
            }
        }
        return false;
    }

    private void clientTick() {
        refreshSetup();
        steering.chase(maxSteerDegrees());
        for (WheelSide side : WheelSide.values()) {
            WheelState w = getWheel(side);
            w.lastAngle = w.angle;
            w.angle += w.omega / 20.0;
            w.lastClientSpringLength = w.clientSpringLength;
            w.clientSpringLength = Mth.lerp(0.5, w.clientSpringLength, w.springLength);
            w.lastDeploy = w.deploy;
            w.deploy = Mth.lerp(0.25, w.deploy, hasTire(side) ? 1.0 : 0.0);
        }
    }


    // ==========================================
    // DRIVER CONTROLS / REDSTONE
    // ==========================================

    public void setDriverSteering(double steer, double brake) {
        if (level == null || level.isClientSide) {
            return;
        }
        driverSteer = Mth.clamp(steer, -15.0, 15.0);
        driverBrake = Mth.clamp(brake, 0.0, 15.0);
        driverControlTime = level.getGameTime();
    }

    private boolean driverActive() {
        return level != null && driverControlTime >= level.getGameTime() - 2;
    }

    private int channelSignal(SteerChannel channel) {
        return links.signal(channel.ordinal());
    }

    public SimpleContainer getControls() {
        return controls;
    }

    private void refreshLinkNetwork() {
        links.refresh();
    }

    private void removeLinks() {
        links.remove();
    }

    // ==========================================
    // STEERING
    // ==========================================

    // at the centerline, the wheels sit either side of it (see ackermannK)
    public double maxSteerDegrees() {
        return setup.maxSteerDegrees();
    }

    // k in cot(inner) - cot(outer) = k, from the setup's percentage and the car's proportions. 0 wheelbase is parallel
    public double ackermannK() {
        double wb = cachedWheelbase;
        return setup.wheelSteerK(wb > 1.0e-6 ? trackWidth() / wb : 0.0);
    }

    // zero until this strut is part of a car
    public double getWheelbase() {
        return cachedWheelbase;
    }

    // see SteerAssist
    private double frontSlipLock() {
        // the assist reads front tyres, so a steered rear axle gets full lock for now
        if (steering.assistOff() || level == null || !frontAxle) {
            return 1.0;
        }
        Vec3 vel = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(worldPosition));
        if (Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 3.6 <= setup.steerFullLockSpeed()) {
            return 1.0;
        }
        return SteerAssist.frontLock(steering.angle(), fittedCorners(), maxSteerDegrees());
    }

    // server-side, current steer angle of this axle (rad)
    public double getSteerAngleRad() {
        return steering.angle();
    }

    // see Ackermann. A rear axle steers the other way for the same turn, so it's mirrored there and back, or its
    // inner and outer wheels swap
    public double wheelSteerAngle(double base, WheelSide side) {
        return frontAxle ? Ackermann.wheelAngle(base, ackermannK(), side == WheelSide.RIGHT)
                : -Ackermann.wheelAngle(-base, ackermannK(), side == WheelSide.RIGHT);
    }

    // ==========================================
    // PHYSICS SUBSTEP / DRIVE
    // ==========================================

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

        AssembledCar car = CarAssembler.refreshed(level, subLevel, timeStep);
        CarState.Body body = car == null ? null : car.state().body(subLevel.getUniqueId());
        if (body != null) {
            this.cachedCarMass = body.mass();
            this.cachedCarSpeed = body.speed();
            this.cachedWheelCount = Math.max(1, body.wheels());
            this.sprungMassPerWheel = Math.max(1.0, body.mass()) / this.cachedWheelCount;
        }
        this.cachedGravity = Gravity.of(level);
        refreshSetup();
        if (car != null) {
            this.cachedWheelbase = car.state().wheelbase();
            mirrorAids(car.state().aids());
        }

        double traction = car == null ? 1.0 : car.state().traction().applied();
        Corner.Axle axle = new Corner.Axle(setup, restLength(), MAX_TRAVEL, MAX_DROOP_RENDER, GROUND_MARGIN,
                brake01, drive.torque() * traction, drive.stiffness() * traction, drive.min() * traction,
                drive.max() * traction, cachedCarMass, cachedWheelCount, sprungMassPerWheel, cachedGravity,
                cachedTerrainAssist, cachedCarSpeed);
        boolean split = car != null && axle.driveStiffness() > 0.0;
        boolean leftFitted = hasTire(WheelSide.LEFT);
        boolean rightFitted = hasTire(WheelSide.RIGHT);
        double diffTorque = drive.carried(split, leftFitted && rightFitted);
        Corner.Axle left = split ? drive.warmStart(axle, true, diffTorque) : axle;
        Corner.Axle right = split ? drive.warmStart(axle, false, -diffTorque) : axle;
        boolean queued = false;
        queued |= stepWheel(WheelSide.LEFT, subLevel, pose, massData, left, timeStep);
        queued |= stepWheel(WheelSide.RIGHT, subLevel, pose, massData, right, timeStep);
        drive.stepped(traction, diffTorque);
        if (split) {
            // the car splits the drive across every driven wheel once they have all stepped, then calls finishDrive
            car.queueSplit(this, drive.wheels(axle, diffTorque,
                    leftFitted ? leftWheel : null, leftFitted ? wheelInertia(WheelSide.LEFT) : 0.0,
                    rightFitted ? rightWheel : null, rightFitted ? wheelInertia(WheelSide.RIGHT) : 0.0));
        } else {
            finishDrive(timeStep);
        }
        if (queued && car != null) {
            car.queueForces(this);
        }
    }

    // steer assist is synced because the client chases the steer angle too
    private void mirrorAids(CarState.Aids aids) {
        cachedTerrainAssist = aids.terrainAssist();
        driftDiffMode = aids.driftDiff();
        if (steering.setAssistOff(aids.steerAssistOff())) {
            syncDirty = true;
        }
    }

    private boolean stepWheel(WheelSide side, ServerSubLevel subLevel, Pose3d pose, MassData massData,
                              Corner.Axle axle, double dt) {
        WheelState wheel = getWheel(side);
        Gathered gathered = gatherCorner(side, wheel, subLevel, pose, massData, dt);
        if (gathered == null) {
            wheel.grounded = false;
            wheel.springLength = Mth.lerp(0.5, wheel.springLength, restLength());
            return false;
        }
        CornerOutput out = Corner.step(axle, side == WheelSide.LEFT ? 1.0 : -1.0, gathered.in(), wheel,
                modes, dt);
        if (out == null) {
            if (!wheel.grounded) {
                wheel.fx.smokeSeverity = 0.0;
            }
            return false;
        }
        applyCorner(wheel, gathered, out, pose, massData, dt);
        return true;
    }

    private record Gathered(CornerInput in, Vector3d hardpoint, Vector3d forward, Vector3d axle,
                            Vector3d normal, TerrainCast.Result cast) {
    }

    @Nullable
    private Gathered gatherCorner(WheelSide side, WheelState wheel, ServerSubLevel subLevel, Pose3d pose,
                                  MassData massData, double dt) {
        ItemStack stack = getTire(side);
        TireLike tire = stack.get(OffroadDataComponents.TIRE);
        if (tire == null) {
            return null;
        }

        double radius = tire.radius();
        Direction facing = getFacing();
        Direction sideDir = getSideDirection(side);
        Vec3 hardpoint = axleCentre()
                .add(sideDir.getStepX() * trackHalfWidth(), 0.0, sideDir.getStepZ() * trackHalfWidth());
        Vector3d hardpointJoml = JOMLConversion.toJOML(hardpoint);

        // Ackermann geometry on the steering now, so the outer and inner wheels have to take their own angles (see wheelSteerAngle)
        double wheelSteer = wheelSteerAngle(steering.angle(), side);
        Vector3d forward = new Vector3d(facing.getStepX(), 0.0, facing.getStepZ()).rotateY(wheelSteer);
        Direction rightDir = facing.getClockWise();
        Vector3d axle = new Vector3d(rightDir.getStepX(), 0.0, rightDir.getStepZ()).rotateY(wheelSteer);

        SubLevel self = Sable.HELPER.getContaining(this);
        TerrainCast.Result cast = TerrainCast.cast(level, self, hardpoint, forward, axle, pose, radius,
                getTire(side).getOrDefault(CreateMotorsport.TIRE_WIDTH, 0.0f));
        if (cast.hit()) {
            wheel.telem.rigidRaw = cast.rigid();
            wheel.telem.hardpointY = hardpoint.y;
        }
        Vec3 forwardVec = JOMLConversion.toMojang(forward);
        TerrainAssist.Probe probe = d -> TerrainCast.probe(level, self, hardpoint.add(forwardVec.scale(d)), pose, radius);

        SurfaceSpec surface = SurfaceSpec.DRY_DEFAULT;
        double surfaceMu = 1.0;
        if (cast.hitBlock() != null) {
            BlockState ground = level.getBlockState(cast.hitBlock());
            surface = Surfaces.of(level, cast.hitBlock(), ground);
            surfaceMu = surface == SurfaceSpec.DRY_DEFAULT
                    ? TerrainCast.fudgeFriction(PhysicsBlockPropertyHelper.getFriction(ground))
                    : surface.peakMu();
        }

        Vector3d velocity = Sable.HELPER.getVelocity(level, subLevel, hardpointJoml, new Vector3d());
        double airborneVel = velocity.y;
        velocity.y += cachedGravity * dt / GRAVITY_READ_DIVISOR; // Sable does this same subtraction in ServerSubLevel.prePhysicsTick but doesnt expose it
        Vector3d localVelocity = pose.transformNormalInverse(velocity);
        double hardpointVel = velocity.y;

        // this is the exact jacobian denominator. I was using this to size the spring and scale the contact load cap before, but that needs to be
        // the corners static share
        double invNormalMass = massData.getInverseNormalMass(hardpointJoml, OrientedBoundingBox3d.UP);
        double rollMass = Config.TIRE_COMPLIANCE.get()
                ? rollResponseMass(massData, hardpointJoml, invNormalMass) : Double.NaN;
        double invAxleMass = massData.getInverseNormalMass(hardpointJoml, axle);

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

        TireSpec tireSpec = getTireSpec(side);
        float designLoad = FittedTire.designLoad(stack, this.sprungMassPerWheel, cachedGravity);

        CornerInput in = new CornerInput(radius,
                cast.hit(), cast.rigid(), cast.castLift(), probe,
                surface, surfaceMu,
                airborneVel, hardpointVel,
                localVelocity.dot(forward), localVelocity.dot(axle),
                // hitNormal is already in body space here, so the velocity has to be rotated the same way before projecting
                localVelocity.dot(hitNormal), localVelocity.length(),
                hitNormal.y,
                invNormalMass, rollMass, invAxleMass,
                tireSpec, designLoad, tireRate(stack, radius),
                stack.getOrDefault(CreateMotorsport.TIRE_VERTICAL_DAMPING, TireEntry.DEFAULT_VERTICAL_DAMPING),
                FittedTire.mass(stack, cachedCarMass), level.getGameTime());
        return new Gathered(in, hardpointJoml, forward, axle, hitNormal, cast);
    }

    private void applyCorner(WheelState wheel, Gathered gathered, CornerOutput out, Pose3d pose,
                             MassData massData, double dt) {
        Vector3d hardpointJoml = gathered.hardpoint();
        Vector3d springImpulse = new Vector3d(gathered.normal()).mul(out.springForce() * dt);
        forceTotal.applyImpulseAtPoint(massData, hardpointJoml, springImpulse);

        if (out.downforce() > 0.0) {
            forceTotal.applyImpulseAtPoint(massData, hardpointJoml, new Vector3d(0.0, -out.downforce() * dt, 0.0));
        }

        Vector3d contactPoint = new Vector3d(hardpointJoml).sub(0.0, wheel.springLength, 0.0);
        forceTotal.applyImpulseAtPoint(massData, contactPoint, new Vector3d(gathered.forward()).mul(out.forwardImpulse()));

        Vector3d sidePoint = new Vector3d(contactPoint);
        if (massData.getCenterOfMass() != null) {
            double heightAboveCom = contactPoint.y - massData.getCenterOfMass().y();
            double rollInfluence = setup.rollInfluence();
            sidePoint.y -= heightAboveCom * (1.0 - rollInfluence);
        }
        forceTotal.applyImpulseAtPoint(massData, sidePoint, new Vector3d(gathered.axle()).mul(out.sideImpulse()));

        CornerInput in = gathered.in();
        WheelEffects.record(level, wheel.fx, wheel, pose, contactPoint, gathered.forward(), gathered.axle(),
                in.vLon(), in.vLat(), in.radius(), gathered.cast());
    }

    // The roll mode's mass at one corner, in kg
    // Not responseMass. The issue is pencil shaped cars diverging real badly in roll mode
    private double rollResponseMass(MassData massData, Vector3d hardpointJoml, double invCorner) {
        Vector3d axleCentre = JOMLConversion.toJOML(axleCentre());
        double invMid = massData.getInverseNormalMass(axleCentre, OrientedBoundingBox3d.UP);
        return Corner.rollResponseMass(invCorner, invMid, cachedWheelCount);
    }

    public ForceTotal forceTotal() {
        return forceTotal;
    }

    // written by AssembledCar's driveline before this axle steps
    public void setDrive(double torquePerWheel, double stiffnessPerWheel, double min, double max) {
        drive.setDrive(torquePerWheel, stiffnessPerWheel, min, max);
        if (level != null) {
            driveTorqueGameTime = level.getGameTime();
        }
    }

    // see AxleDiff.passed
    public double getDriveTorquePerWheel() {
        return drive.passed();
    }

    // each wheel's weight in the mean, and last substep's mean
    public void setDriveSplit(double weight, double mean) {
        drive.setSplit(weight, mean);
    }

    // after the car's split, or at once on an axle it doesn't split
    public void finishDrive(double dt) {
        boolean leftFitted = hasTire(WheelSide.LEFT);
        boolean rightFitted = hasTire(WheelSide.RIGHT);
        boolean pair = leftFitted && rightFitted;
        drive.finish(leftWheel, leftFitted, pair ? wheelInertia(WheelSide.LEFT) : 0.0,
                rightWheel, rightFitted, pair ? wheelInertia(WheelSide.RIGHT) : 0.0,
                isDriven(), differential, MassScale.measured(cachedCarMass), driftDiffMode, dt);
    }

    private double wheelInertia(WheelSide side) {
        return FittedTire.inertia(getTire(side), cachedCarMass, REST_LENGTH);
    }

    // ==========================================
    // TIRES
    // ==========================================

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

    public boolean hasAnyTire() {
        return hasTire(WheelSide.LEFT) || hasTire(WheelSide.RIGHT);
    }

    public boolean hasTire(WheelSide side) {
        return getTire(side).get(OffroadDataComponents.TIRE) != null;
    }

    // what the driveline and traction control read
    public List<CornerState> fittedCorners() {
        List<CornerState> out = new ArrayList<>(2);
        for (WheelSide side : WheelSide.values()) {
            if (hasTire(side)) {
                out.add(getWheel(side));
            }
        }
        return out;
    }

    public double meanTireRadius() {
        double sum = 0.0;
        int n = 0;
        for (WheelSide side : WheelSide.values()) {
            TireLike tire = getTire(side).get(OffroadDataComponents.TIRE);
            if (tire != null) {
                sum += tire.radius();
                n++;
            }
        }
        return n == 0 ? 0.0 : sum / n;
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

    // see SpecOverrides.Memo
    public TireSpec getTireSpec(WheelSide side) {
        ItemStack stack = getTire(side);
        return (side == WheelSide.LEFT ? leftCompoundMemo : rightCompoundMemo).get(SpecCodecs.TIRE_SPEC,
                SpecRegistries.fittedCompound(registryAccess(), stack, isFrontAxle()),
                stack.getOrDefault(CreateMotorsport.TIRE_COMPOUND_OVERRIDES, NO_TIRE_OVERRIDES));
    }

    public float foreignDesignLoad() {
        return FittedTire.foreignDesignLoad(this.sprungMassPerWheel, cachedGravity);
    }

    // for SpecDump
    public double tireRate(ItemStack stack, double radius) {
        return FittedTire.verticalRate(stack, radius, this.sprungMassPerWheel, cachedGravity);
    }

    // ==========================================
    // READOUTS FOR THE CAR / TELEMETRY / HUD
    // ==========================================

    public WheelTelemetry getTelemetry(WheelSide side) {
        return WheelTelemetry.of(getWheel(side));
    }

    // peak Mu for the HUD grip readout
    public double getPeakMu(WheelSide side) {
        return getWheel(side).peakMu;
    }

    public double getCachedCarMass() {
        return cachedCarMass;
    }

    public double getScaledWheelMass() {
        return Corner.wheelMass(cachedCarMass);
    }

    public double getSprungMassPerWheel() {
        return sprungMassPerWheel;
    }

    // omega*dt of the corner's body mode. Over 2 the integrator cant hold it,
    // and if over Corner.MAX_BODY_MODE_OMEGA_DT this axle substeps to compensate
    public double getBodyModeOmegaDt() {
        return modes.bodyOmegaDt;
    }

    public double getRollModeOmegaDt() {
        return modes.rollOmegaDt;
    }

    // the frequency the solver ran, after UnsprungSolver's detune
    public double effectiveSpringFreqHz() {
        return UnsprungSolver.detunedFreq(setup.naturalFreqHz(), modes.bodyOmegaDt, modes.rollOmegaDt,
                Corner.MAX_BODY_MODE_OMEGA_DT);
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

    // ==========================================
    // RENDERING (CLIENT)
    // ==========================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public float getLerpedSpringLength(WheelSide side, float partialTicks) {
        WheelState w = getWheel(side);
        return (float) Mth.lerp(partialTicks, w.lastClientSpringLength, w.clientSpringLength);
    }

    public float getLerpedAngle(WheelSide side, float partialTicks) {
        WheelState w = getWheel(side);
        return (float) Mth.lerp(partialTicks, w.lastAngle, w.angle);
    }

    public float getLerpedSteer(float partialTicks) {
        return (float) steering.lerpedAngle(partialTicks);
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

    // ==========================================
    // NBT
    // ==========================================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ContainerHelper.saveAllItems(tag, tires, registries);
        CompoundTag controlsTag = new CompoundTag();
        ContainerHelper.saveAllItems(controlsTag, controlItems, registries);
        tag.put("Controls", controlsTag);
        setupSlot.write(tag);
        differentialSlot.write(tag);
        tag.putBoolean("FrontAxle", frontAxle);
        if (steered != null) {
            tag.putBoolean("Steered", steered);
        }
        if (clientPacket) {
            tag.putInt("LiftSteps", liftSteps);
            tag.putInt("TrackWidthPx", trackWidthPx);
            tag.putDouble("SteerSignal", steering.signal());
            tag.putFloat("SteerLock", (float) steering.lock());
            tag.putBoolean("SteerAssistOff", steering.assistOff());
            tag.putFloat("LeftOmega", (float) leftWheel.omega);
            tag.putFloat("RightOmega", (float) rightWheel.omega);
            tag.putFloat("LeftSpring", (float) leftWheel.springLength);
            tag.putFloat("RightSpring", (float) rightWheel.springLength);
            tag.putFloat("Wheelbase", (float) cachedWheelbase);
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

        setupSlot.read(tag, () -> {
            try {
                return SuspensionSetting.valueOf(tag.getString("Setting")).key();
            } catch (IllegalArgumentException ignored) {
                return SuspensionSetting.defaultFor(getBlockState());
            }
        });
        differentialSlot.read(tag, () -> SpecRegistries.DIFF_LSD);
        frontAxle = tag.getBoolean("FrontAxle");
        steered = tag.contains("Steered") ? tag.getBoolean("Steered") : null;
        if (!clientPacket) {
            migrateGeometry(tag);
        }
        if (clientPacket) {
            liftSteps = Mth.clamp(tag.getInt("LiftSteps"), 0, LIFT_STEPS_SANITY);
            trackWidthPx = Mth.clamp(tag.getInt("TrackWidthPx"), TRACK_MIN_PX, TRACK_MAX_PX);
        }
        if (clientPacket) {
            steering.readSynced(tag.getDouble("SteerSignal"), tag.contains("SteerLock") ? tag.getFloat("SteerLock") : 1.0,
                    tag.getBoolean("SteerAssistOff"));
            leftWheel.omega = tag.getFloat("LeftOmega");
            rightWheel.omega = tag.getFloat("RightOmega");
            leftWheel.springLength = tag.getFloat("LeftSpring");
            rightWheel.springLength = tag.getFloat("RightSpring");
            cachedWheelbase = tag.getFloat("Wheelbase");
        }
    }

    private void migrateGeometry(CompoundTag tag) {
        int lift = tag.contains("LiftStepsOverride") ? tag.getInt("LiftStepsOverride")
                : tag.contains("LiftSteps") ? Mth.clamp(tag.getInt("LiftSteps"), 0, LIFT_STEPS_SANITY) : -1;
        int track = tag.contains("TrackWidthOverridePx") ? tag.getInt("TrackWidthOverridePx")
                : tag.contains("TrackWidthPx") ? tag.getInt("TrackWidthPx") : -1;
        CompoundTag migrated = setupSlot.overrides().copy();
        if (lift >= 0 && !SpecOverrides.isSet(migrated, "ride_height")) {
            migrated.putDouble("ride_height", liftHeight(lift));
        }
        if (track >= 0 && !SpecOverrides.isSet(migrated, "track_width")) {
            migrated.putDouble("track_width", Mth.clamp(track, TRACK_MIN_PX, TRACK_MAX_PX) / 16.0);
        }
        setupSlot.setOverrides(migrated);
    }
}
