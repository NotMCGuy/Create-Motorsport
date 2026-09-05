package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.physics.DrivetrainSim;
import com.createmotorsport.physics.EngineSpec;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import org.joml.Vector3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;


public class EngineBlockEntity extends SmartBlockEntity implements dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {

    //control channels for steering wheel
    public enum ControlChannel {
        THROTTLE("Throttle"),
        CLUTCH("Clutch"),
        SHIFT_UP("Shift Up"),
        SHIFT_DOWN("Shift Down"),
        PIT_LIMITER("Pit Limiter");

        private final String displayName;

        ControlChannel(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final ControlChannel[] CHANNELS = ControlChannel.values();
    public static final int SLOT_EXHAUST = 0;
    public static final int SLOT_INTAKE = 1;
    public static final int SLOT_CHANNELS_START = 2;
    public static final int SLOT_COUNT = SLOT_CHANNELS_START + CHANNELS.length * 2;

    public static int channelSlotA(ControlChannel channel) {
        return SLOT_CHANNELS_START + channel.ordinal() * 2;
    }

    public static int channelSlotB(ControlChannel channel) {
        return SLOT_CHANNELS_START + channel.ordinal() * 2 + 1;
    }

    private static final int LAVA_PER_BURN = 100;
    private static final int BURN_TICKS = 100;
    private static final int SOUND_INTERVAL = 8;

    private final DrivetrainSim drivetrain = new DrivetrainSim(EngineSpec.RACING_V8_HYBRID);

    private int burnTicks;
    private int soundCooldown;
    private float throttle;
    private int lastShiftUpSignal;
    private int lastShiftDownSignal;

    private final int[] receivedSignals = new int[CHANNELS.length];
    private final boolean[] registeredLinks = new boolean[CHANNELS.length];
    private final IRedstoneLinkable[] channelLinks = new IRedstoneLinkable[CHANNELS.length];


    // signal from our steering wheel that takes precedence over redstone
    private final int[] driverSignals = new int[CHANNELS.length];
    private float driverThrottle01;
    private SteeringWheelBlockEntity.DriveMode driveMode = SteeringWheelBlockEntity.DriveMode.RWD;
    private long driverSignalTime = Long.MIN_VALUE;

    // values for csv logging
    private double telemAvgWheelOmega;
    private double telemWheelTorqueTotal;
    private int telemDrivenWheels;

    // User selected engine crank rotation direction (+1 or -1)
    private int rotationDirection = 1;

    // Center-differential coupling gain
    // Nm of torque transfer per rad/s of front vs rear speed difference
    private static final double CENTER_DIFF_COUPLING = 50.0;

    // Driver controlled settings: engine mode, overtake and traction control affecting torque
    public static final int MAX_POWER_MODE = 8;
    private static final double BOOST_FACTOR = 1.25;   // overtake torque multiplier
    private static final double BOOST_DRAIN = 1.0 / (20 * 8);    // ~8 s of full boost
    private static final double BOOST_RECHARGE = 1.0 / (20 * 20); // ~20 s to refill

    // Traction Control
    private static final double TC_SPEED_FLOOR = 2.0;    // divide by zero otherwise
    private static final double TC_INTEGRAL_CLAMP = 0.5;
    private static final double TICK_SECONDS = 0.05;
    private static final double TCL_SIDE_SLIP = 4.0;     // m/s of sideways slide before the anti-oversteer cut
    private static final double TCL_SIDE_RANGE = 8.0;    // m/s past that over which the cut ramps to the floor
    private static final double TCL_SIDE_FLOOR = 0.5;    // most the lateral cut can trim throttle to

    private int powerMode = MAX_POWER_MODE; // 1 to 8;  torque caps at mode / MAX
    private boolean tractionControl;
    private double telemTcSlipTarget;
    private double telemTcFactor = 1.0;
    private double tcIntegral;
    private double tcFactor = 1.0; // last throttle multiplier
    private double boostReserve = 1.0;      // 0 to 1
    private boolean boosting;

    private boolean auxOvertake;
    private boolean auxModeUp;
    private boolean auxModeDown;
    private boolean auxTc;
    private boolean lastAuxModeUp;
    private boolean lastAuxModeDown;
    private boolean lastAuxTc;
    private boolean pitLimiter;
    private boolean auxPitLimiter;
    private boolean lastAuxPitLimiter;
    private double telemPowerFactor = 1.0;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            syncAttachmentState();
            EngineBlockEntity.this.setChanged();
        }
    };

    public EngineBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.ENGINE_BLOCK_ENTITY.get(), pos, state);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, items.get(slot));
        }
        for (ControlChannel channel : CHANNELS) {
            channelLinks[channel.ordinal()] = new ChannelLink(channel);
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        updateFuel();
        updateThrottle();

        boolean running = isFueled();
        boolean clutchHeld = signalFor(ControlChannel.CLUTCH) > 0;
        int shiftUpSignal = signalFor(ControlChannel.SHIFT_UP);
        int shiftDownSignal = signalFor(ControlChannel.SHIFT_DOWN);
        boolean shiftUpEdge = shiftUpSignal > 0 && lastShiftUpSignal == 0;
        boolean shiftDownEdge = shiftDownSignal > 0 && lastShiftDownSignal == 0;
        lastShiftUpSignal = shiftUpSignal;
        lastShiftDownSignal = shiftDownSignal;

        boolean driving = driverActive();
        boolean modeUp = driving && auxModeUp;
        boolean modeDown = driving && auxModeDown;
        boolean tcToggle = driving && auxTc;
        boolean pitToggle = driving && auxPitLimiter;
        if (pitToggle && !lastAuxPitLimiter) {
            pitLimiter = !pitLimiter;
        }
        lastAuxPitLimiter = pitToggle;
        
        // Also check redstone link signal if driver is not active
        if (!driving && hasLink(ControlChannel.PIT_LIMITER)) {
            pitLimiter = signalFor(ControlChannel.PIT_LIMITER) > 0;
        }
        if (modeUp && !lastAuxModeUp) {
            powerMode = Math.min(MAX_POWER_MODE, powerMode + 1);
        }
        if (modeDown && !lastAuxModeDown) {
            powerMode = Math.max(1, powerMode - 1);
        }
        if (tcToggle && !lastAuxTc) {
            tractionControl = !tractionControl;
        }
        lastAuxModeUp = modeUp;
        lastAuxModeDown = modeDown;
        lastAuxTc = tcToggle;

        // uses sables per-plot actor registry to gather every suspension with ties on it, to make up the drivetrain
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        List<SuspensionBlockEntity> axles = new ArrayList<>();
        int wheelCount = 0;
        int actorCount = 0;
        double omegaSum = 0.0;
        double peakOmega = 0.0;
        double peakLatUse = 0.0;
        double tractionForce = 0.0;
        double repRadius = 0.0;
        Direction engineFacing = getBlockState().getValue(EngineBlock.FACING);

        if (subLevel != null) {
            for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
                actorCount++;
                if (!(actor instanceof SuspensionBlockEntity suspension)
                        || suspension.isRemoved() || !suspension.hasAnyTire()) {
                    continue;
                }
                if (!isDrivenAxle(suspension)) {
                    continue;
                }
                axles.add(suspension);
                if (repRadius <= 0.0) {
                    repRadius = suspension.getTireRadius();
                }
                int sign = rotationDirection * facingSign(engineFacing, suspension.getFacing());
                int tireCount = (suspension.hasTire(SuspensionBlockEntity.WheelSide.LEFT) ? 1 : 0)
                        + (suspension.hasTire(SuspensionBlockEntity.WheelSide.RIGHT) ? 1 : 0);
                omegaSum += suspension.averageDrivenOmega(sign) * tireCount;
                peakOmega = Math.max(peakOmega, suspension.peakDrivenOmega(sign));
                peakLatUse = Math.max(peakLatUse, suspension.peakLateralUse());
                tractionForce += suspension.remainingTractionForce();
                wheelCount += tireCount;
            }
        }

        double avgOmega = wheelCount == 0 ? 0.0 : omegaSum / wheelCount;
        boolean semiAuto = Config.SEMI_AUTO_SHIFT.get();
        double totalTorque = drivetrain.update(running, throttle, clutchHeld, semiAuto, shiftUpEdge, shiftDownEdge,
                avgOmega, 1.0 / 20.0, pitLimiter);
        totalTorque *= powerFactor(driving && auxOvertake, peakOmega, peakLatUse, avgOmega,repRadius, running,
                tractionForce, Math.abs(totalTorque));

        this.telemAvgWheelOmega = avgOmega;
        this.telemWheelTorqueTotal = totalTorque;
        this.telemDrivenWheels = wheelCount;

        if (wheelCount > 0) {
            long gameTime = level.getGameTime();

            double frontOmegaSum = 0.0;
            double rearOmegaSum = 0.0;
            int frontWheels = 0;
            int rearWheels = 0;
            for (SuspensionBlockEntity suspension : axles) {
                int sign = rotationDirection * facingSign(engineFacing, suspension.getFacing());
                int tc = (suspension.hasTire(SuspensionBlockEntity.WheelSide.LEFT) ? 1 : 0)
                        + (suspension.hasTire(SuspensionBlockEntity.WheelSide.RIGHT) ? 1 : 0);
                if (suspension.isFrontAxle()) {
                    frontOmegaSum += suspension.averageDrivenOmega(sign) * tc;
                    frontWheels += tc;
                } else {
                    rearOmegaSum += suspension.averageDrivenOmega(sign) * tc;
                    rearWheels += tc;
                }
            }

            double perWheelFront;
            double perWheelRear;
            if (frontWheels > 0 && rearWheels > 0) {
                double bias = Config.CENTER_DIFF_FRONT_BIAS.getAsDouble();
                double frontAvg = frontOmegaSum / frontWheels;
                double rearAvg = rearOmegaSum / rearWheels;
                double cap = Config.CENTER_DIFF_LOCK.getAsDouble() * Math.abs(totalTorque);
                double transfer = Mth.clamp((frontAvg - rearAvg) * CENTER_DIFF_COUPLING, -cap, cap);
                double frontTorque = totalTorque * bias - transfer;
                double rearTorque = totalTorque * (1.0 - bias) + transfer;
                perWheelFront = frontTorque / frontWheels;
                perWheelRear = rearTorque / rearWheels;
            } else {
                double perWheel = totalTorque / wheelCount;
                perWheelFront = perWheel;
                perWheelRear = perWheel;
            }

            for (SuspensionBlockEntity suspension : axles) {
                int sign = rotationDirection * facingSign(engineFacing, suspension.getFacing());
                double perWheel = suspension.isFrontAxle() ? perWheelFront : perWheelRear;
                suspension.applyDriveTorque(perWheel * sign, gameTime);
            }
        }

        if (Config.ENABLE_DEBUG_LOGGING.getAsBoolean() && level.getGameTime() % 20 == 0) {
            CreateMotorsport.LOGGER.info(
                    "[Engine {}] driver={} gear={} rpm={} throttle={} clutch={} up={} down={} wheels={} torque={}",
                    worldPosition, driverActive(), drivetrain.gearDisplay(),
                    getDisplayRpm(), String.format("%.2f", throttle),
                    signalFor(ControlChannel.CLUTCH), signalFor(ControlChannel.SHIFT_UP),
                    signalFor(ControlChannel.SHIFT_DOWN), wheelCount,
                    String.format("%.1f", totalTorque));
        }
        // muting engine for now
        //playEngineSound(running);
    }

    public int getRotationDirection() {
        return rotationDirection;
    }

    // +1 or -1 from user selection
    public void toggleRotationDirection() {
        if (level == null || level.isClientSide) {
            return;
        }
        rotationDirection = -rotationDirection;
        setChanged();
        sendData();
    }

    // trying to auto-align the car, so +1 is the suspension facing same way as engine, -1 if opposite
    private static int facingSign(Direction engineFacing, Direction suspensionFacing) {
        int dot = engineFacing.getStepX() * suspensionFacing.getStepX()
                + engineFacing.getStepZ() * suspensionFacing.getStepZ();
        return dot < 0 ? -1 : 1;
    }

    // ===================================================
    // Fuel & throttle
    // =================

    private boolean isFueled() {
        return burnTicks > 0;
    }

    private void updateFuel() {
        if (burnTicks <= 1 && tryDrainLava()) {
            burnTicks = BURN_TICKS;
            return;
        }
        if (burnTicks > 0) {
            burnTicks--;
        }
    }

    private void updateThrottle() {
        float target = isFueled() ? getTargetThrottle() : 0.0F;
        throttle = Mth.lerp(0.35F, throttle, target);
        if (Math.abs(throttle - target) < 0.01F) {
            throttle = target;
        }
    }

    private float getTargetThrottle() {
        if (driverActive()) {
            return Mth.clamp(driverThrottle01, 0.0F, 1.0F);
        }
        if (!hasLink(ControlChannel.THROTTLE)) {
            return 0.0F;
        }
        return Mth.clamp(signalFor(ControlChannel.THROTTLE) / 15.0F, 0.0F, 1.0F);
    }

    private boolean tryDrainLava() {
        FluidStack lava = new FluidStack(Fluids.LAVA, LAVA_PER_BURN);
        for (Direction direction : Direction.values()) {
            IFluidHandler handler = FluidUtil.getFluidHandler(level, worldPosition.relative(direction),
                    direction.getOpposite()).orElse(null);
            if (handler == null) {
                continue;
            }
            FluidStack simulated = handler.drain(lava, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.getAmount() < LAVA_PER_BURN || simulated.getFluid() != Fluids.LAVA) {
                continue;
            }
            handler.drain(lava, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    // ======================================
    // Controls and redstone
    // ==========================================


    public void setDriverControls(float throttle01, boolean clutch, boolean shiftUp, boolean shiftDown) {
        if (level == null || level.isClientSide) {
            return;
        }
        driverThrottle01 = Mth.clamp(throttle01, 0.0F, 1.0F);
        driverSignals[ControlChannel.THROTTLE.ordinal()] = Math.round(driverThrottle01 * 15); // for the debug readout
        driverSignals[ControlChannel.CLUTCH.ordinal()] = clutch ? 15 : 0;
        driverSignals[ControlChannel.SHIFT_UP.ordinal()] = shiftUp ? 15 : 0;
        driverSignals[ControlChannel.SHIFT_DOWN.ordinal()] = shiftDown ? 15 : 0;
        driverSignalTime = level.getGameTime();
    }

    // steering wheel pushes chosen drive layout (RWD/FWD/AWD) here
    public void setDriveMode(SteeringWheelBlockEntity.DriveMode mode) {
        if (mode != null) {
            this.driveMode = mode;
        }
    }

    // Does this axle get engine torque under the current drive mode
    private boolean isDrivenAxle(SuspensionBlockEntity suspension) {
        return switch (driveMode) {
            case AWD -> true;
            case RWD -> !suspension.isFrontAxle();
            case FWD -> suspension.isFrontAxle();
        };
    }

    // driving aids that affect torque: engine mode, overtake boost, traction control. Pushed here from steering wheel
    public void setDriverAids(boolean overtake, boolean modeUp, boolean modeDown, boolean tcToggle, boolean pitLimiterToggle) {
        if (level == null || level.isClientSide) {
            return;
        }
        auxOvertake = overtake;
        auxModeUp = modeUp;
        auxModeDown = modeDown;
        auxTc = tcToggle;
        auxPitLimiter = pitLimiterToggle; // Added
        driverSignalTime = level.getGameTime();
    }


    // torque multiplier from driver aids (engine mode, overtake boost, traction control)
    private double powerFactor(boolean wantBoost, double peakOmega, double peakLatUse, double avgOmega, double repRadius,
                               boolean running, double tractionForce, double demandTorque) {
        double factor = powerMode / (double) MAX_POWER_MODE;
    
        // Pit limiter override: force drop power factor
        if (pitLimiter) {
            factor *= 0.25; // idk
        } else {
            boosting = wantBoost && boostReserve > 0.0;
            if (boosting) {
                factor *= BOOST_FACTOR;
                boostReserve = Math.max(0.0, boostReserve - BOOST_DRAIN);
            } else {
                boostReserve = Math.min(1.0, boostReserve + BOOST_RECHARGE);
            }
        }
    
        factor *= tractionControlCap(peakOmega, peakLatUse, repRadius, running, tractionForce,
                demandTorque * factor);

        telemPowerFactor = factor;
        return factor;
    }


    private double tractionControlCap(double peakOmega, double peakLatUse, double repRadius,
                                      boolean running, double tractionForce, double demandTorque) {
        if (!tractionControl || !running || repRadius <= 0.0 || throttle <= 0.1f) {
            tcIntegral = 0.0;
            tcFactor = 1.0;
            telemTcSlipTarget = Config.TC_TARGET_SLIP.getAsDouble();
            telemTcFactor = 1.0;
            return 1.0;
        }
        Vec3 vel = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(worldPosition));
        Vec3 fwd = headingForward();

        double ground = Math.abs(vel.x * fwd.x + vel.z * fwd.z);
        double wheelSurface = Math.abs(peakOmega) * repRadius;
        double slipRatio = (wheelSurface - ground) / Math.max(ground, TC_SPEED_FLOOR);
        double latUse = Mth.clamp(peakLatUse, 0.0, 1.0);
        double slipTarget = Config.TC_TARGET_SLIP.getAsDouble() * Math.sqrt(Math.max(0.0, 1.0 - latUse * latUse));
        telemTcSlipTarget = slipTarget;
        double error = slipRatio - slipTarget;

        if (error > 0.0) {
            tcIntegral = Math.min(tcIntegral + error * TICK_SECONDS, TC_INTEGRAL_CLAMP);
        } else {
            tcIntegral = Math.max(0.0, tcIntegral + error * TICK_SECONDS);
        }

        double cut = Config.TC_PROPORTIONAL.getAsDouble() * Math.max(0.0, error)
                + Config.TC_INTEGRAL.getAsDouble() * tcIntegral;
        double target = Mth.clamp(1.0 - cut, Config.TC_MIN_THROTTLE.getAsDouble(), 1.0);

        if (target < tcFactor) {
            tcFactor = target;
        } else {
            tcFactor = Math.min(target, tcFactor + Config.TC_RECOVER_RATE.getAsDouble());
        }

        if (tractionForce > 0.0 && demandTorque > 1.0e-6) {
            double capacityTorque = tractionForce * repRadius;
            tcFactor = Math.min(tcFactor, Mth.clamp(capacityTorque / demandTorque, 0.0, 1.0));
        }
        telemTcFactor = tcFactor;

        double factor = tcFactor;
        double sideSlip = Math.abs(vel.x * fwd.z - vel.z * fwd.x);
        if (sideSlip > TCL_SIDE_SLIP) {
            factor *= Mth.clamp(1.0 - (sideSlip - TCL_SIDE_SLIP) / TCL_SIDE_RANGE, TCL_SIDE_FLOOR, 1.0);
        }
        return factor;
    }

    private Vec3 headingForward() {
        Direction facing = getFacing();
        SubLevel sub = level == null ? null : Sable.HELPER.getContaining(level, worldPosition);
        if (sub != null) {
            Vector3d world = new Vector3d(facing.getStepX(), 0.0, facing.getStepZ());
            sub.logicalPose().transformNormal(world);
            double lenSq = world.x * world.x + world.z * world.z;
            if (lenSq > 1.0e-6) {
                double inv = 1.0 / Math.sqrt(lenSq);
                return new Vec3(world.x * inv, 0.0, world.z * inv);
            }
        }
        return new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
    }

    public int getPowerMode() {
        return powerMode;
    }

    public boolean isTractionControlOn() {
        return tractionControl;
    }

    public double getBoostReserve() {
        return boostReserve;
    }

    public boolean isBoosting() {
        return boosting;
    }

    public double getPowerFactor() {
        return telemPowerFactor;
    }

    private boolean driverActive() {
        return level != null && driverSignalTime >= level.getGameTime() - 2;
    }

    private int signalFor(ControlChannel channel) {
        if (driverActive()) {
            return driverSignals[channel.ordinal()];
        }
        return receivedSignals[channel.ordinal()];
    }

    private boolean hasLink(ControlChannel channel) {
        return !inventory.getItem(channelSlotA(channel)).isEmpty()
                || !inventory.getItem(channelSlotB(channel)).isEmpty();
    }

    private void refreshLinkNetwork() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ControlChannel channel : CHANNELS) {
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
        for (ControlChannel channel : CHANNELS) {
            int i = channel.ordinal();
            if (registeredLinks[i]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, channelLinks[i]);
                registeredLinks[i] = false;
            }
        }
    }

    private class ChannelLink implements IRedstoneLinkable {
        private final ControlChannel channel;

        private ChannelLink(ControlChannel channel) {
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
                    RedstoneLinkNetworkHandler.Frequency.of(inventory.getItem(channelSlotA(channel))),
                    RedstoneLinkNetworkHandler.Frequency.of(inventory.getItem(channelSlotB(channel)))
            );
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }

    // ============================================
    // Sound
    // =============

    // muted for now until we work on this
    private void playEngineSound(boolean running) {
        if (!running) {
            soundCooldown = 0;
            return;
        }
        if (soundCooldown > 0) {
            soundCooldown--;
            return;
        }
        soundCooldown = SOUND_INTERVAL;

        double frac = drivetrain.getRpmFraction();
        SoundEvent sound;
        if (frac < 0.32) {
            sound = CreateMotorsport.ENGINE_IDLE.get();
        } else if (frac < 0.55) {
            sound = CreateMotorsport.ENGINE_LOW.get();
        } else if (frac < 0.80) {
            sound = CreateMotorsport.ENGINE_MID.get();
        } else {
            sound = CreateMotorsport.ENGINE_FAST.get();
        }
        float pitch = 0.7F + (float) frac * 0.7F;
        float volume = 0.35F + throttle * 0.5F;
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ======================================
    // Sync + inventory plumbing
    // ================================

    public DrivetrainSim getDrivetrain() {
        return drivetrain;
    }

    public Direction getFacing() {
        return getBlockState().getValue(EngineBlock.FACING);
    }

    public boolean isDriverActive() {
        return driverActive();
    }

    public float getThrottle() {
        return throttle;
    }

    public double getWheelTorqueTotal() {
        return telemWheelTorqueTotal;
    }

    public double getAvgWheelOmega() {
        return telemAvgWheelOmega;
    }

    public int getDrivenWheelCount() {
        return telemDrivenWheels;
    }

    // server-side RPM
    public int getDisplayRpm() {
        return (int) (drivetrain.getRpmFraction() * drivetrain.spec().redlineRpm());
    }

    // 0 is reverse, 1 is neutral, 2 is 1st, etc
    public int getGearCode() {
        return drivetrain.gearCode();
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public boolean hasExhaust() {
        return !inventory.getItem(SLOT_EXHAUST).isEmpty();
    }

    public boolean hasIntake() {
        return !inventory.getItem(SLOT_INTAKE).isEmpty();
    }

    private void syncAttachmentState() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            items.set(slot, inventory.getItem(slot));
        }
        if (level == null || level.isClientSide) {
            return;
        }

        refreshLinkNetwork();

        BlockState state = getBlockState();
        if (!state.hasProperty(EngineBlock.HAS_EXHAUST) || !state.hasProperty(EngineBlock.HAS_INTAKE)) {
            return;
        }
        BlockState updated = state
                .setValue(EngineBlock.HAS_EXHAUST, hasExhaust())
                .setValue(EngineBlock.HAS_INTAKE, hasIntake());
        if (updated != state) {
            level.setBlock(worldPosition, updated, 3);
            notifyUpdate();
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        refreshLinkNetwork();
    }

    @Override
    public void remove() {
        removeLinks();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        removeLinks();
        super.onChunkUnloaded();
    }

    // =============================
    // NBT
    // ==================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("BurnTicks", burnTicks);
        tag.putFloat("Throttle", throttle);
        tag.putInt("PowerMode", powerMode);
        tag.putBoolean("TractionControl", tractionControl);
        tag.putInt("RotationDirection", rotationDirection);
        drivetrain.save(tag);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        burnTicks = tag.getInt("BurnTicks");
        throttle = tag.getFloat("Throttle");
        powerMode = tag.contains("PowerMode") ? Math.max(1, Math.min(MAX_POWER_MODE, tag.getInt("PowerMode"))) : MAX_POWER_MODE;
        tractionControl = tag.getBoolean("TractionControl");
        rotationDirection = tag.getInt("RotationDirection") < 0 ? -1 : 1;
        drivetrain.load(tag);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, items.get(slot));
        }
        syncAttachmentState();
    }
}
