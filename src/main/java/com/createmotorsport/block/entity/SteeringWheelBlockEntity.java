package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.network.TelemetryLinePacket;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class SteeringWheelBlockEntity extends SmartBlockEntity {
    // menu ordering of the various keybinds
    public enum SteeringControl {
        THROTTLE("Throttle"),
        BRAKE("Brake"),
        STEER_LEFT("Steer Left"),
        STEER_RIGHT("Steer Right"),
        CLUTCH("Clutch"),
        SHIFT_UP("Shift Up"),
        SHIFT_DOWN("Shift Down"),
        OVERTAKE("Overtake"),
        ENGINE_MODE_UP("Engine Mode+"),
        ENGINE_MODE_DOWN("Engine Mode-"),
        TRACTION_CONTROL("Traction Ctrl"),
        LIFT_UP("Lift Up"),
        LIFT_DOWN("Lift Down"),
        DIFF_MODE("Diff Mode"),
        HUD_TOGGLE("HUD Toggle"),
        PIT_LIMITER("Pit Limiter");

        private final String displayName;

        SteeringControl(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final SteeringControl[] CONTROLS = SteeringControl.values();
    public static final int SLOT_COUNT = CONTROLS.length * 2;

    public static int slotA(SteeringControl control) {
        return control.ordinal() * 2;
    }

    public static int slotB(SteeringControl control) {
        return control.ordinal() * 2 + 1;
    }

    // "ghost" item slots for the backup redstone links
    private final NonNullList<ItemStack> frequencyItems = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);


    // client-side registry
    private static final Set<SteeringWheelBlockEntity> CLIENT_LOADED =
            Collections.newSetFromMap(new WeakHashMap<>());
    // server-side registry
    private static final Set<SteeringWheelBlockEntity> SERVER_LOADED =
            Collections.newSetFromMap(new WeakHashMap<>());

    // for csv logs; ticks left, ticks between rows, when it started, who receives the lines
    private int logTicksRemaining;
    private int logSampleEveryTicks = 10;
    private long logStartGameTime;
    private UUID logRecipient;

    private final Transmitter[] transmitters = new Transmitter[CONTROLS.length];
    private final boolean[] registered = new boolean[CONTROLS.length];

    public enum DriveMode {
        FWD("FWD"),
        RWD("RWD"),
        AWD("AWD");

        private final String label;

        DriveMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public DriveMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // GLFW key codes which are bound to a given control by the player. -1 is unbound. Gets synced to client
    private final int[] keyCodes = new int[CONTROLS.length];
    private int inputMask;

    // analog driver inputs
    private float driverThrottle01;
    private float driverBrake01;
    private float driverSteer01;
    private DriveMode driveMode = DriveMode.RWD;
    private UUID user;

    // Dashboard telemetry, gathered on the server and synced to the client for the screen
    private int speedKmh;
    private int gearCode = 1; // 0 = R, 1 = N, 2 = 1st, 3 = 2nd gear, etc
    private int rpm;
    private boolean brake;
    private int steer; // animation now responds to analog input, so -1 left through +1 right
    private int powerMode = 8;
    private boolean tractionControlOn;
    private boolean boosting;
    private int telemetryCooldown;

    // for drivers' differential controls
    private int prevMomentaryMask;
    private boolean driftDiffMode;

    // More telemetry for HUD
    private int throttlePct;
    private int[] hudTireTempsC = new int[0];
    private int hudSlipMask;// 1 = slipping
    private int hudEffMuX100;//avg friction coef across tires

    // Client-side wheel-rotation interpolation.
    private double clientWheelAngle;
    private double lastClientWheelAngle;

    public static Iterable<SteeringWheelBlockEntity> clientLoaded() {
        return CLIENT_LOADED;
    }

    // returns which wheel a given player is currently driving or null
    public static SteeringWheelBlockEntity findDrivenBy(Player player) {
        for (SteeringWheelBlockEntity wheel : SERVER_LOADED) {
            if (!wheel.isRemoved() && wheel.isUser(player)) {
                return wheel;
            }
        }
        return null;
    }

    public SteeringWheelBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.STEERING_WHEEL_BLOCK_ENTITY.get(), pos, state);
        Arrays.fill(keyCodes, -1);
        for (SteeringControl control : CONTROLS) {
            transmitters[control.ordinal()] = new Transmitter(control);
        }
    }

    // return GLFW key code for the control
    public int getKeyCode(int controlIndex) {
        return controlIndex >= 0 && controlIndex < keyCodes.length ? keyCodes[controlIndex] : -1;
    }

    // Server: binds or clear with -1, a controls key from the menu
    public void setKeyCode(int controlIndex, int keyCode) {
        if (level == null || level.isClientSide || controlIndex < 0 || controlIndex >= keyCodes.length) {
            return;
        }
        keyCodes[controlIndex] = keyCode;
        setChanged();
        notifyUpdate();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // the 'ghost' item frequency in a link slot
    public ItemStack getFrequencyItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? frequencyItems.get(slot) : ItemStack.EMPTY;
    }

    // set ghost item frequency
    public void setFrequencyItem(int slot, ItemStack stack) {
        if (level == null || level.isClientSide || slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        if (ItemStack.matches(frequencyItems.get(slot), copy)) {
            return;
        }
        frequencyItems.set(slot, copy);
        refreshLinks();
        setChanged();
    }

    public boolean isUser(Player player) {
        return user != null && user.equals(player.getUUID());
    }

    public boolean hasUser() {
        return user != null;
    }

    public UUID getUser() {
        return user;
    }


    // Set a player as driving, server side. Client pushes this button on the menu itself, Sable already gates that block interaction by interaction range
    public void setDriving(Player player, boolean driving) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (driving) {
            user = player.getUUID();
            notifyUpdate();
        } else if (isUser(player)) {
            stopUsing();
        }
    }

    private void stopUsing() {
        user = null;
        inputMask = 0;
        driverThrottle01 = 0.0F;
        driverBrake01 = 0.0F;
        driverSteer01 = 0.0F;
        applyPowers();
        notifyUpdate();
    }

    public DriveMode getDriveMode() {
        return driveMode;
    }

    // menu button that cycles FWD -> RWD -> AWD, sendData() syncs it
    public void cycleDriveMode() {
        if (level == null || level.isClientSide) {
            return;
        }
        driveMode = driveMode.next();
        setChanged();
        sendData();
    }

    // server-side, receive the driver's controls: digital bitmask & analog throttle/brake/steer
    public void setInput(Player sender, int mask, int throttle, int brake, int steer) {
        if (level == null || level.isClientSide || !isUser(sender)) {
            return;
        }
        inputMask = mask;
        driverThrottle01 = Mth.clamp(throttle, 0, 100) / 100.0F;
        driverBrake01 = Mth.clamp(brake, 0, 100) / 100.0F;
        driverSteer01 = Mth.clamp(steer, -100, 100) / 100.0F;
        applyPowers();
    }

    private void applyPowers() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (SteeringControl control : CONTROLS) {
            boolean pressed = (inputMask & (1 << control.ordinal())) != 0;
            // converting redstone backup signals to their 0-100% value
            int power = switch (control) {
                case THROTTLE -> Math.round(driverThrottle01 * 15.0F);
                case BRAKE -> Math.round(driverBrake01 * 15.0F);
                case STEER_LEFT -> driverSteer01 > 0.0F ? Math.round(driverSteer01 * 15.0F) : 0;
                case STEER_RIGHT -> driverSteer01 < 0.0F ? Math.round(-driverSteer01 * 15.0F) : 0;
                default -> pressed ? 15 : 0;
            };
            transmitters[control.ordinal()].setPower(power);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            // configurable steering that responds to analog input
            double target = (steer / 100.0) * com.createmotorsport.Config.STEERING_WHEEL_MAX_ANGLE.getAsDouble();
            lastClientWheelAngle = clientWheelAngle;
            clientWheelAngle = Mth.lerp(0.3, clientWheelAngle, target);
            return;
        }

        gatherTelemetry();
        tickTelemetryLog();

        if (user == null) {
            return;
        }
        // drop the driver if theyve left the game
        Player player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(user) : null;
        if (player == null) {
            stopUsing();
            return;
        }
        pushDriverControls();
    }


    // bypass redstone with direct auto-recongition of our blocks.
    // Throttle/clutch/gearshift is sent to every engine
    // braking is sent to every suspension,
    //steering will go to the nearest axle only, so if the user puts the steering wheel in the rear it would currently turn the wrong axle

    private void pushDriverControls() {
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) {
            return;
        }
        boolean clutch = (inputMask & (1 << SteeringControl.CLUTCH.ordinal())) != 0;
        boolean shiftUp = (inputMask & (1 << SteeringControl.SHIFT_UP.ordinal())) != 0;
        boolean shiftDown = (inputMask & (1 << SteeringControl.SHIFT_DOWN.ordinal())) != 0;
        boolean overtake = (inputMask & (1 << SteeringControl.OVERTAKE.ordinal())) != 0;
        boolean modeUp = (inputMask & (1 << SteeringControl.ENGINE_MODE_UP.ordinal())) != 0;
        boolean modeDown = (inputMask & (1 << SteeringControl.ENGINE_MODE_DOWN.ordinal())) != 0;
        boolean tractionControl = (inputMask & (1 << SteeringControl.TRACTION_CONTROL.ordinal())) != 0;
        boolean PitLimiter = (inputMask & (1 << SteeringControl.PIT_LIMITER.ordinal())) != 0;

        int liftUpBit = 1 << SteeringControl.LIFT_UP.ordinal();
        int liftDownBit = 1 << SteeringControl.LIFT_DOWN.ordinal();
        int diffBit = 1 << SteeringControl.DIFF_MODE.ordinal();
        boolean liftUpEdge = (inputMask & liftUpBit) != 0 && (prevMomentaryMask & liftUpBit) == 0;
        boolean liftDownEdge = (inputMask & liftDownBit) != 0 && (prevMomentaryMask & liftDownBit) == 0;
        boolean diffEdge = (inputMask & diffBit) != 0 && (prevMomentaryMask & diffBit) == 0;
        prevMomentaryMask = inputMask;
        if (diffEdge) {
            driftDiffMode = !driftDiffMode;
        }

        float throttle01 = driverThrottle01;
        double steerSignal = driverSteer01 * 15.0;
        double brakeSignal = driverBrake01 * 15.0;

        EngineBlockEntity engine = null;
        List<SuspensionBlockEntity> suspensions = new ArrayList<>();
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof EngineBlockEntity e) {
                engine = e;
            } else if (actor instanceof SuspensionBlockEntity s && !s.isRemoved()) {
                suspensions.add(s);
            }
        }

        if (Config.ENABLE_DEBUG_LOGGING.getAsBoolean() && level.getGameTime() % 20 == 0) {
            CreateMotorsport.LOGGER.info(
                    "[Wheel {}] driving mask={} engine={} suspensions={} steer={} brake={} throttle={}",
                    worldPosition, Integer.toBinaryString(inputMask), engine != null, suspensions.size(),
                    String.format("%.2f", steerSignal), String.format("%.2f", brakeSignal),
                    String.format("%.2f", throttle01));
        }

        if (engine != null) {
            engine.setDriverControls(throttle01, clutch, shiftUp, shiftDown);
            engine.setDriverAids(overtake, modeUp, modeDown, tractionControl, PitLimiter);
            engine.setDriveMode(driveMode);
        }
        if (suspensions.isEmpty()) {
            return;
        }


        // now that front axle is set by user, steering axle is way easier to decide
        int liftDelta = Config.LIFT_STEPS_PER_PRESS.getAsInt();
        for (SuspensionBlockEntity s : suspensions) {
            if (liftUpEdge) {
                s.adjustLift(liftDelta);
            } else if (liftDownEdge) {
                s.adjustLift(-liftDelta);
            }
            s.setDriftDiffMode(driftDiffMode);
            s.setDriverSteering(s.isFrontAxle() ? steerSignal : 0.0, brakeSignal);
        }
    }

    // ==========================================================================================================
    // CSV LOGGING
    // =============================================================================================


    // engine + suspensions which are on the same sublevel as the steering wheel
    private record CarActors(EngineBlockEntity engine, List<SuspensionBlockEntity> suspensions, SubLevel subLevel) {
    }

    private CarActors gatherCar() {
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) {
            return null;
        }
        EngineBlockEntity engine = null;
        List<SuspensionBlockEntity> suspensions = new ArrayList<>();
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof EngineBlockEntity e) {
                engine = e;
            } else if (actor instanceof SuspensionBlockEntity s && !s.isRemoved()) {
                suspensions.add(s);
            }
        }
        suspensions.sort(java.util.Comparator.comparingLong(s -> s.getBlockPos().asLong()));
        return new CarActors(engine, suspensions, subLevel);
    }

    // start log for (seconds), give to (player) as csv. samplesPerSec 1-20; 20 samples per second is needed for better debugging
    public void startTelemetryLog(Player player, int seconds, int samplesPerSec) {
        if (level == null || level.isClientSide) {
            return;
        }
        CarActors car = gatherCar();
        if (car == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c[Motorsports] Cant find a Sable sub-level to log for this car"), false);
            return;
        }
        logRecipient = player.getUUID();
        logTicksRemaining = seconds * 20;
        logSampleEveryTicks = Math.max(1, 20 / Math.max(1, Math.min(20, samplesPerSec)));
        logStartGameTime = level.getGameTime();
        sendLine(TelemetryLinePacket.KIND_HEADER, buildHeader(car));
    }

    private void tickTelemetryLog() {
        if (logTicksRemaining <= 0) {
            return;
        }
        if (logTicksRemaining % logSampleEveryTicks == 0) {
            CarActors car = gatherCar();
            if (car != null) {
                sendLine(TelemetryLinePacket.KIND_ROW, buildRow(car));
            }
        }
        logTicksRemaining--;
        if (logTicksRemaining <= 0) {
            // appends the full config to every csv so its easier to identify issues with bug reports
            sendLine(TelemetryLinePacket.KIND_ROW, "");
            sendLine(TelemetryLinePacket.KIND_ROW, "config_option,value");
            for (String configLine : com.createmotorsport.Config.dumpForLog()) {
                sendLine(TelemetryLinePacket.KIND_ROW, configLine);
            }
            sendLine(TelemetryLinePacket.KIND_END, "");
            logRecipient = null;
        }
    }

    private void sendLine(int kind, String line) {
        if (logRecipient == null || level.getServer() == null) {
            return;
        }
        net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(logRecipient);
        if (player != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new TelemetryLinePacket(kind, line));
        }
    }

    private static final String[] WHEEL_COLS = {
            "grounded", "load_N", "slip_ratio", "slip_angle_deg", "vlon_ms", "vlat_ms", "Fx_N", "Fy_N",
            "omega", "wheelspeed_ms", "spring_m", "compress_m", "mu", "steer_deg", "brake_Nm",
            "grip_mult", "drive_Nm", "tire_temp_C"
    };

    private String buildHeader(CarActors car) {
        StringBuilder sb = new StringBuilder(
                "t_s,tick,speed_ms,speed_kmh,mass_kg,gear,rpm,throttle,clutch_locked,"
                        + "engine_torque_Nm,gear_ratio,wheel_torque_Nm,wheel_torque_applied,avg_wheel_omega,driven_wheels,"
                        + "power_mode,tc_on,boost_reserve,torque_factor,pos_x,pos_y,pos_z,vel_x,vel_y,vel_z,"
                        + "bodypos_x,bodypos_y,bodypos_z,quat_x,quat_y,quat_z,quat_w");
        for (int a = 0; a < car.suspensions().size(); a++) {
            for (String sideTag : new String[]{"L", "R"}) {
                for (String col : WHEEL_COLS) {
                    sb.append(",s").append(a).append(sideTag).append('_').append(col);
                }
            }
        }
        return sb.toString();
    }

    private String buildRow(CarActors car) {
        java.util.Locale l = java.util.Locale.ROOT;
        StringBuilder sb = new StringBuilder();

        double tS = (level.getGameTime() - logStartGameTime) / 20.0;
        Vec3 velocity = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(worldPosition));
        double speed = velocity.length();
        Vec3 worldPos = car.subLevel() != null
                ? car.subLevel().logicalPose().transformPosition(Vec3.atCenterOf(worldPosition))
                : Vec3.atCenterOf(worldPosition);
        org.joml.Vector3dc bodyPos = car.subLevel() != null
                ? car.subLevel().logicalPose().position() : new org.joml.Vector3d();
        org.joml.Quaterniondc quat = car.subLevel() != null
                ? car.subLevel().logicalPose().orientation() : new org.joml.Quaterniond();
        double mass = car.subLevel() instanceof ServerSubLevel ssl && ssl.getMassTracker() != null
                ? ssl.getMassTracker().getMass() : -1.0;

        EngineBlockEntity engine = car.engine();
        String gear = engine != null ? engine.getDrivetrain().gearDisplay() : "-";
        int rpm = engine != null ? engine.getDisplayRpm() : 0;
        double throttle = engine != null ? engine.getThrottle() : 0.0;
        boolean clutchLocked = engine != null && engine.getDrivetrain().lastClutchLocked();
        double engineTorque = engine != null ? engine.getDrivetrain().lastEngineTorque() : 0.0;
        double gearRatio = engine != null ? engine.getDrivetrain().lastRatio() : 0.0;
        double wheelTorque = engine != null ? engine.getDrivetrain().lastWheelTorque() : 0.0;
        double wheelTorqueApplied = engine != null ? engine.getWheelTorqueTotal() : 0.0;
        double avgOmega = engine != null ? engine.getAvgWheelOmega() : 0.0;
        int drivenWheels = engine != null ? engine.getDrivenWheelCount() : 0;
        int powerMode = engine != null ? engine.getPowerMode() : 0;
        boolean tcOn = engine != null && engine.isTractionControlOn();
        double boostReserve = engine != null ? engine.getBoostReserve() : 0.0;
        double torqueFactor = engine != null ? engine.getPowerFactor() : 0.0;

        sb.append(String.format(l, "%.2f,%d,%.3f,%.2f,%.1f,%s,%d,%.3f,%d,%.2f,%.3f,%.2f,%.2f,%.3f,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f",
                tS, level.getGameTime(), speed, speed * 3.6, mass, gear, rpm, throttle,
                clutchLocked ? 1 : 0, engineTorque, gearRatio, wheelTorque, wheelTorqueApplied,
                avgOmega, drivenWheels, powerMode, tcOn ? 1 : 0, boostReserve, torqueFactor,
                worldPos.x, worldPos.y, worldPos.z, velocity.x, velocity.y, velocity.z,
                bodyPos.x(), bodyPos.y(), bodyPos.z(), quat.x(), quat.y(), quat.z(), quat.w()));

        for (SuspensionBlockEntity s : car.suspensions()) {
            double steerDeg = Math.toDegrees(s.getSteerAngleRad());
            for (SuspensionBlockEntity.WheelSide side : SuspensionBlockEntity.WheelSide.values()) {
                SuspensionBlockEntity.WheelTelemetry t = s.getTelemetry(side);
                sb.append(String.format(l,
                        ",%d,%.1f,%.4f,%.3f,%.3f,%.3f,%.1f,%.1f,%.2f,%.3f,%.4f,%.4f,%.3f,%.2f,%.1f,%.3f,%.2f,%.1f",
                        t.grounded() ? 1 : 0, t.loadN(), t.slipRatio(), t.slipAngleDeg(),
                        t.vLonMs(), t.vLatMs(), t.longForceN(), t.latForceN(), t.omega(),
                        t.wheelSpeedMs(), t.springLenM(), t.compressionM(), t.surfaceMu(),
                        steerDeg, t.brakeTorqueNm(), t.gripMult(), t.driveTorqueNm(), t.tireTempC()));
            }
        }
        return sb.toString();
    }

    // ========================================================================================================

    // what counts as slipping for the HUD
    private static final double HUD_SLIP_RATIO = 0.20;
    private static final double HUD_SLIP_ANGLE = 10.0;

    // server: read speed/gear/RPM/brake/steering/etc off the car for the dashboard + HUD
    private void gatherTelemetry() {
        int gear = 1;
        int enginRpm = 0;
        int speed = 0;
        int mode = 8;
        boolean tc = false;
        boolean boost = false;
        List<SuspensionBlockEntity> susp = new ArrayList<>();

        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel != null) {
            for (var actor : subLevel.getPlot().getBlockEntityActors()) {
                if (actor instanceof EngineBlockEntity engine) {
                    gear = engine.getGearCode();
                    enginRpm = engine.getDisplayRpm();
                    mode = engine.getPowerMode();
                    tc = engine.isTractionControlOn();
                    boost = engine.isBoosting();
                } else if (actor instanceof SuspensionBlockEntity s && !s.isRemoved()) {
                    susp.add(s);
                }
            }
            Vec3 velocity = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(worldPosition));
            speed = (int) Math.round(velocity.length() * 3.6); // m/s -> km/h
        }

        // sorting so HUD can label FL/FR then RL/RR
        susp.sort(java.util.Comparator.comparing((SuspensionBlockEntity s) -> !s.isFrontAxle())
                .thenComparingLong(s -> s.getBlockPos().asLong()));
        boolean thermal = Config.TIRE_THERMAL_MODEL.get();
        int[] temps = new int[thermal ? susp.size() * 2 : 0];
        int slipMask = 0;
        double muSum = 0.0;
        int muCount = 0;
        int ti = 0;
        for (SuspensionBlockEntity s : susp) {
            for (SuspensionBlockEntity.WheelSide side : SuspensionBlockEntity.WheelSide.values()) {
                SuspensionBlockEntity.WheelTelemetry t = s.getTelemetry(side);
                if (thermal) {
                    temps[ti] = (int) Math.round(t.tireTempC());
                }
                if (t.grounded()) {
                    if (Math.abs(t.slipRatio()) > HUD_SLIP_RATIO || Math.abs(t.slipAngleDeg()) > HUD_SLIP_ANGLE) {
                        slipMask |= (1 << ti);
                    }
                    muSum += s.getPeakMu(side);
                    muCount++;
                }
                ti++;
            }
        }
        int effMu = muCount > 0 ? (int) Math.round(muSum / muCount * 100.0) : 0;
        int thr = Math.round(Mth.clamp(driverThrottle01, 0.0F, 1.0F) * 100.0F);

        boolean braking = (inputMask & (1 << SteeringControl.BRAKE.ordinal())) != 0;

        // analog steering wheel animation, synced to client. -100 fully left (user chooses how much that is), +100 fully right
        // driverSteer01 is +1 for left
        int steering = Math.round(Mth.clamp(-driverSteer01, -1.0F, 1.0F) * 100.0F);

        boolean changed = speed != speedKmh || gear != gearCode || Math.abs(enginRpm - rpm) > 50
                || braking != brake || Math.abs(steering - steer) > 2
                || mode != powerMode || tc != tractionControlOn || boost != boosting
                || thr != throttlePct || slipMask != hudSlipMask || effMu != hudEffMuX100
                || !java.util.Arrays.equals(temps, hudTireTempsC);
        speedKmh = speed;
        gearCode = gear;
        rpm = enginRpm;
        brake = braking;
        steer = steering;
        powerMode = mode;
        tractionControlOn = tc;
        boosting = boost;
        throttlePct = thr;
        hudTireTempsC = temps;
        hudSlipMask = slipMask;
        hudEffMuX100 = effMu;

        if (telemetryCooldown > 0) {
            telemetryCooldown--;
        }
        if (changed && telemetryCooldown <= 0) {
            telemetryCooldown = 2;
            notifyUpdate();
        }
    }

    public int getThrottlePct() {
        return throttlePct;
    }

    public int[] getTireTempsC() {
        return hudTireTempsC;
    }

    public int getSlipMask() {
        return hudSlipMask;
    }

    public double getEffectiveMu() {
        return hudEffMuX100 / 100.0;
    }

    public int getSpeedKmh() {
        return speedKmh;
    }

    public int getGearCode() {
        return gearCode;
    }

    public int getRpm() {
        return rpm;
    }

    public boolean isBraking() {
        return brake;
    }

    public int getPowerMode() {
        return powerMode;
    }

    public boolean isTractionControlOn() {
        return tractionControlOn;
    }

    public boolean isBoosting() {
        return boosting;
    }

    public float getLerpedWheelAngle(float partialTick) {
        return (float) Mth.lerp(partialTick, lastClientWheelAngle, clientWheelAngle);
    }


    // =======================================================================
    // Redstone link transmitting
    // ================================================================

    private boolean hasFrequency(SteeringControl control) {
        return !frequencyItems.get(slotA(control)).isEmpty()
                || !frequencyItems.get(slotB(control)).isEmpty();
    }

    private void refreshLinks() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (SteeringControl control : CONTROLS) {
            int i = control.ordinal();
            if (registered[i]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, transmitters[i]);
                registered[i] = false;
            }
            if (hasFrequency(control)) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, transmitters[i]);
                registered[i] = true;
            }
        }
        applyPowers();
    }

    private void removeLinks() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (SteeringControl control : CONTROLS) {
            int i = control.ordinal();
            if (registered[i]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, transmitters[i]);
                registered[i] = false;
            }
        }
    }

    private final class Transmitter implements IRedstoneLinkable {
        private final SteeringControl control;
        private int power;

        private Transmitter(SteeringControl control) {
            this.control = control;
        }

        private void setPower(int newPower) {
            int clamped = Math.max(0, Math.min(15, newPower));
            if (clamped == power) {
                return;
            }
            power = clamped;
            if (registered[control.ordinal()] && level != null && !level.isClientSide) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
            }
        }

        @Override
        public int getTransmittedStrength() {
            return power;
        }

        @Override
        public void setReceivedStrength(int received) {
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return level != null && !level.isClientSide && !isRemoved() && level.isLoaded(worldPosition);
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(frequencyItems.get(slotA(control))),
                    RedstoneLinkNetworkHandler.Frequency.of(frequencyItems.get(slotB(control)))
            );
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }

    // ================================================================
    // Lifecycle + NBT
    // ========================================

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && level.isClientSide) {
            CLIENT_LOADED.add(this);
        } else {
            SERVER_LOADED.add(this);
            refreshLinks();
        }
    }

    @Override
    public void remove() {
        CLIENT_LOADED.remove(this);
        SERVER_LOADED.remove(this);
        removeLinks();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        CLIENT_LOADED.remove(this);
        SERVER_LOADED.remove(this);
        removeLinks();
        super.onChunkUnloaded();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        ListTag freqs = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = frequencyItems.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) slot);
            entry.put("Item", stack.save(registries));
            freqs.add(entry);
        }
        tag.put("Frequencies", freqs);
        tag.putIntArray("KeyCodes", keyCodes.clone());
        tag.putString("DriveMode", driveMode.name());
        if (user != null) {
            tag.putUUID("User", user);
        }
        if (clientPacket) {
            tag.putInt("SpeedKmh", speedKmh);
            tag.putInt("GearCode", gearCode);
            tag.putInt("Rpm", rpm);
            tag.putBoolean("Brake", brake);
            tag.putInt("Steer", steer);
            tag.putInt("PowerMode", powerMode);
            tag.putBoolean("Tc", tractionControlOn);
            tag.putBoolean("Boost", boosting);
            tag.putInt("Throttle", throttlePct);
            tag.putIntArray("TireTemps", hudTireTempsC);
            tag.putInt("SlipMask", hudSlipMask);
            tag.putInt("EffMu", hudEffMuX100);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            frequencyItems.set(slot, ItemStack.EMPTY);
        }
        ListTag freqs = tag.getList("Frequencies", Tag.TAG_COMPOUND);
        for (int i = 0; i < freqs.size(); i++) {
            CompoundTag entry = freqs.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < SLOT_COUNT) {
                frequencyItems.set(slot, ItemStack.parseOptional(registries, entry.getCompound("Item")));
            }
        }
        Arrays.fill(keyCodes, -1);
        int[] saved = tag.getIntArray("KeyCodes");
        for (int i = 0; i < keyCodes.length && i < saved.length; i++) {
            keyCodes[i] = saved[i];
        }
        try {
            driveMode = DriveMode.valueOf(tag.getString("DriveMode"));
        } catch (IllegalArgumentException ignored) {
            driveMode = DriveMode.RWD;
        }
        user = tag.hasUUID("User") ? tag.getUUID("User") : null;
        if (clientPacket) {
            speedKmh = tag.getInt("SpeedKmh");
            gearCode = tag.getInt("GearCode");
            rpm = tag.getInt("Rpm");
            brake = tag.getBoolean("Brake");
            steer = tag.getInt("Steer");
            powerMode = tag.getInt("PowerMode");
            tractionControlOn = tag.getBoolean("Tc");
            boosting = tag.getBoolean("Boost");
            throttlePct = tag.getInt("Throttle");
            hudTireTempsC = tag.getIntArray("TireTemps");
            hudSlipMask = tag.getInt("SlipMask");
            hudEffMuX100 = tag.getInt("EffMu");
        }
        refreshLinks();
    }
}
