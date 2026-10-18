package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.DownFlapBlock;
import com.createmotorsport.physics.control.Cruise;
import com.createmotorsport.physics.terrain.TerrainAssist;
import com.createmotorsport.physics.vehicle.CarState;
import com.createmotorsport.redstone.LinkTransmitters;
import com.createmotorsport.telemetry.*;
import com.createmotorsport.vehicle.AssembledCar;
import com.createmotorsport.vehicle.CarAssembler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The Steering Wheel covers the cars driver controls and certain driver aids
 *
 * User input goes to CarState.Controls
 * Turns the client's input into CarState.Controls and CarState.Aids,
 * also transmits signal onto redstone links if applicable
 *
 * HAS TO BE A SABLE ACTOR SO THAT AssembledCar.rebuild FINDS IT
 * see vehicle/AssembledCar for a checklist when adding new kinds of Block Entities for a car
 */
public class SteeringWheelBlockEntity extends SmartBlockEntity implements dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor,
        com.createmotorsport.menu.PartPanel {

    // ==========================================
    // CONSTANTS / TYPES
    // ==========================================

    // kept when broken, not the driver
    public static final java.util.Set<String> CONFIGURATION_KEYS = java.util.Set.of(
            "Frequencies", "KeyCodes", "ControlLayout", "TerrainAssist", "AssistOff", "DiffMode",
            "TractionControl");

    private static final int CONTROL_LAYOUT = 1;
    private static final double CRUISE_DT = 0.05;

    private static final boolean RACE_EXPORT_EXTRA = false;

    public static final SteeringControl[] CONTROLS = SteeringControl.values();
    public static final int SLOT_COUNT = CONTROLS.length * 2;

    public static int slotA(SteeringControl control) {
        return control.ordinal() * 2;
    }

    public static int slotB(SteeringControl control) {
        return control.ordinal() * 2 + 1;
    }

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
        DIFF_MODE("Diff Mode"),
        HUD_TOGGLE("HUD Toggle"),
        STEER_ASSIST("Steer Assist"),
        CRUISE("Cruise"),
        IGNITION("Ignition"),
        DOWN_FLAP("Down Flap");

        private final String displayName;

        SteeringControl(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Aid { TERRAIN_ASSIST, STEER_ASSIST, DIFF_MODE, TRACTION_CONTROL }

    // ==========================================
    // LOADED WHEELS
    // ==========================================

    private static final Set<SteeringWheelBlockEntity> SERVER_LOADED =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static Iterable<SteeringWheelBlockEntity> serverLoaded() { return SERVER_LOADED; }

    public static SteeringWheelBlockEntity findLoadedAt(BlockPos pos) {
        for (SteeringWheelBlockEntity wheel : SERVER_LOADED) {
            if (!wheel.isRemoved() && wheel.getBlockPos().equals(pos)) {
                return wheel;
            }
        }
        return null;
    }

    public static SteeringWheelBlockEntity findDrivenBy(Player player) {
        for (SteeringWheelBlockEntity wheel : SERVER_LOADED) {
            if (!wheel.isRemoved() && wheel.isUser(player)) {
                return wheel;
            }
        }
        return null;
    }

    // ==========================================
    // STATE
    // ==========================================


    // Driver and inputs
    private UUID user;
    private int inputMask;
    private float driverThrottle01;
    private float driverBrake01;
    private float driverSteer01;
    private float appliedThrottle01;
    private int prevMomentaryMask;

    // GLFW key codes bound to a control by user. -1 is unbound. Synced to client
    private final int[] keyCodes = new int[CONTROLS.length];

    // "ghost" item slots for the backup redstone links
    private final NonNullList<ItemStack> frequencyItems = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final LinkTransmitters links = new LinkTransmitters(this, slot -> frequencyItems.get(slot),
            CONTROLS.length, i -> slotA(CONTROLS[i]), i -> slotB(CONTROLS[i]));

    // driver aids
    private TerrainAssist.Level terrainAssist = TerrainAssist.Level.NORMAL;
    private boolean driftDiffMode;
    private boolean steerAssistOff;
    private boolean tractionControl;

    // Cruise control
    private boolean cruiseOn;
    private double cruiseTargetMs;
    private int cruiseTargetKmh;

    // Dashboard and HUD
    private Dashboard dashboard = Dashboard.EMPTY;
    private Dashboard dashboardSynced = Dashboard.EMPTY;
    private int telemetryCooldown;
    private boolean assistOffSynced;
    private boolean diffModeSynced;
    private int cruiseSyncedKmh;

    // /motorsports log, while running
    @Nullable
    private TelemetryLog log;
    private long logStartGameTime;

    // client
    private double clientWheelAngle;
    private double lastClientWheelAngle;

    // DownFlap
    private boolean downFlapToggle = false;

    // ==========================================
    // CONSTRUCTION / LIFECYCLE
    // ==========================================
    // If on -> drag activated



    public SteeringWheelBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.STEERING_WHEEL_BLOCK_ENTITY.get(), pos, state);
        Arrays.fill(keyCodes, -1);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || !level.isClientSide) {
            SERVER_LOADED.add(this);
            refreshLinks();
        }
        CarAssembler.memberChanged(level);
    }

    @Override
    public void remove() {
        SERVER_LOADED.remove(this);
        removeLinks();
        CarAssembler.memberChanged(level);
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        SERVER_LOADED.remove(this);
        removeLinks();
        CarAssembler.memberChanged(level);
        super.onChunkUnloaded();
    }

    // ==========================================
    // PART SCREEN / AIDS
    // ==========================================

    @Override
    public com.createmotorsport.menu.PartKind partKind() {
        return com.createmotorsport.menu.PartKind.STEERING_WHEEL;
    }

    @Override
    public List<net.minecraft.network.chat.Component> statusLines() {
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        Player driver = user != null && level != null && level.getServer() != null
                ? level.getServer().getPlayerList().getPlayer(user) : null;
        lines.add(net.minecraft.network.chat.Component.literal(driver != null
                ? "Driver: " + driver.getGameProfile().getName() : "No driver"));
        AssembledCar car = CarAssembler.of(this);
        lines.add(net.minecraft.network.chat.Component.literal(car == null ? "Not part of a car"
                : "Car: " + car.engines().size() + " engine(s), " + car.suspensions().size() + " axle(s)"));
        if (log != null) {
            lines.add(net.minecraft.network.chat.Component.literal("Logging"));
        }
        return lines;
    }

    public TerrainAssist.Level getTerrainAssist() {
        return terrainAssist;
    }

    public CarState.Aids aids() {
        return new CarState.Aids(terrainAssist, steerAssistOff, driftDiffMode, tractionControl);
    }

    public void toggle(Aid aid) {
        if (level == null || level.isClientSide) {
            return;
        }
        switch (aid) {
            case TERRAIN_ASSIST -> terrainAssist = terrainAssist.next();
            case STEER_ASSIST -> steerAssistOff = !steerAssistOff;
            case DIFF_MODE -> driftDiffMode = !driftDiffMode;
            case TRACTION_CONTROL -> tractionControl = !tractionControl;
        }
        setChanged();
        sendData();
    }

    public boolean isTractionControlOn() {
        return tractionControl;
    }

    public boolean isSteerAssistOff() {
        return steerAssistOff;
    }

    public boolean isDiffModeOn() {
        return driftDiffMode;
    }

    // ==========================================
    // DRIVING
    // ==========================================

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
            if (user != null && !user.equals(player.getUUID())) {
                return;
            }
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
        appliedThrottle01 = 0.0F;
        AssembledCar car = CarAssembler.of(this);
        if (car != null) {
            car.state().cruise().off();
        }
        mirrorCruise(null);
        applyPowers();
        notifyUpdate();
    }

    public Vec3 carWorldPosition() {
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) {
            return null;
        }
        return subLevel.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition));
    }

    // from SteeringInputPacket
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
            // configurable steering that responds to analog input
            double target = dashboard.steer() / 100.0
                    * com.createmotorsport.Config.STEERING_WHEEL_MAX_ANGLE.getAsDouble();
            lastClientWheelAngle = clientWheelAngle;
            clientWheelAngle = Mth.lerp(0.3, clientWheelAngle, target);
            return;
        }

        AssembledCar car = CarAssembler.of(level, Sable.HELPER.getContaining(this));
        if (car != null && car.steps(this)) {
            car.gameTick(level);
        } else {
            driverStep();
        }
        gatherTelemetry();
        tickTelemetryLog();
    }

    public void driverStep() {
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

    // push driver controls to ControlState which distributes them from there
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
        boolean ignition = (inputMask & (1 << SteeringControl.IGNITION.ordinal())) != 0;

        int diffBit = 1 << SteeringControl.DIFF_MODE.ordinal();
        int downFlapBit = 1 << SteeringControl.DOWN_FLAP.ordinal();
        int assistBit = 1 << SteeringControl.STEER_ASSIST.ordinal();
        int cruiseBit = 1 << SteeringControl.CRUISE.ordinal();
        int tcBit = 1 << SteeringControl.TRACTION_CONTROL.ordinal();
        boolean diffEdge = (inputMask & diffBit) != 0 && (prevMomentaryMask & diffBit) == 0;
        boolean downFlapEdge = (inputMask & downFlapBit) != 0 && (prevMomentaryMask & downFlapBit) == 0;
        boolean assistEdge = (inputMask & assistBit) != 0 && (prevMomentaryMask & assistBit) == 0;
        boolean cruiseEdge = (inputMask & cruiseBit) != 0 && (prevMomentaryMask & cruiseBit) == 0;
        boolean tcEdge = (inputMask & tcBit) != 0 && (prevMomentaryMask & tcBit) == 0;
        prevMomentaryMask = inputMask;
        if (diffEdge) {
            toggle(Aid.DIFF_MODE);
        }
        if (assistEdge) {
            toggle(Aid.STEER_ASSIST);
        }
        if (tcEdge) {
            toggle(Aid.TRACTION_CONTROL);
        }

        AssembledCar car = CarAssembler.of(level, subLevel);
        if (car == null) {
            appliedThrottle01 = driverThrottle01;
            mirrorCruise(null);
            return;
        }
        Vec3 v = Sable.HELPER.getVelocity(level, Vec3.atCenterOf(worldPosition));
        float throttle01 = car.state().cruise().throttle(cruiseEdge, Math.sqrt(v.x * v.x + v.z * v.z),
                driverThrottle01, driverBrake01, car.throttleAuthority(), CRUISE_DT);
        mirrorCruise(car.state().cruise());
        appliedThrottle01 = throttle01;
        double steerSignal = driverSteer01 * 15.0;
        double brakeSignal = driverBrake01 * 15.0;

        // send to CarState to handle and distribute the controls where needed
        car.state().setControls(new CarState.Controls(throttle01, clutch, shiftUp, shiftDown,
                overtake, modeUp, modeDown, steerSignal, brakeSignal, ignition), level.getGameTime());

        // Get all pillar downflaps
        List<DownFlapBlockEntity> downflaps = new ArrayList<>();
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof DownFlapBlockEntity d && d.getBlockState().getValue(DownFlapBlock.PILLAR)) {
                downflaps.add(d);
            }
        }

        if (Config.ENABLE_DEBUG_LOGGING.getAsBoolean() && level.getGameTime() % 20 == 0) {
            CreateMotorsport.LOGGER.info(
                    "[Wheel {}] driving mask={} engines={} suspensions={} steer={} brake={} throttle={}",
                    worldPosition, Integer.toBinaryString(inputMask), car.engines().size(), car.suspensions().size(),
                    String.format("%.2f", steerSignal), String.format("%.2f", brakeSignal),
                    String.format("%.2f", throttle01));
        }

        if (!downflaps.isEmpty()) {
            if (downFlapEdge) {
                downFlapToggle = !downFlapToggle;
                for (DownFlapBlockEntity d : downflaps) {
                    DownFlapBlock block = (DownFlapBlock) d.getBlockState().getBlock();
                    block.steeringBack(d.getBlockState(), d.getLevel(), d.getBlockPos(), downFlapToggle);
                }
            }
        }
    }

// ==========================================
// KEY BINDINGS / REDSTONE LINKS
// ==========================================

public int getKeyCode(int controlIndex) {
    return controlIndex >= 0 && controlIndex < keyCodes.length ? keyCodes[controlIndex] : -1;
}

// from SetSteeringKeyPacket, -1 clears
public void setKeyCode(int controlIndex, int keyCode) {
    if (level == null || level.isClientSide || controlIndex < 0 || controlIndex >= keyCodes.length) {
        return;
    }
    keyCodes[controlIndex] = keyCode;
    setChanged();
    notifyUpdate();
    }

    // the 'ghost' item frequency in a link slot
    public ItemStack getFrequencyItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? frequencyItems.get(slot) : ItemStack.EMPTY;
    }

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

    private void applyPowers() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (SteeringControl control : CONTROLS) {
            boolean pressed = (inputMask & (1 << control.ordinal())) != 0;
            // redstone to %
            int power = switch (control) {
                case THROTTLE -> Math.round(driverThrottle01 * 15.0F);
                case BRAKE -> Math.round(driverBrake01 * 15.0F);
                case STEER_LEFT -> driverSteer01 > 0.0F ? Math.round(driverSteer01 * 15.0F) : 0;
                case STEER_RIGHT -> driverSteer01 < 0.0F ? Math.round(-driverSteer01 * 15.0F) : 0;
                default -> pressed ? 15 : 0;
            };
            links.setPower(control.ordinal(), power);
        }
    }

    private void refreshLinks() {
        if (level == null || level.isClientSide) {
            return;
        }
        links.refresh();
        applyPowers();
    }

    private void removeLinks() {
        links.remove();
    }

    // ==========================================
    // CRUISE / DASHBOARD
    // ==========================================

    private void mirrorCruise(@Nullable Cruise.Controller cruise) {
        cruiseOn = cruise != null && cruise.on();
        cruiseTargetMs = cruise == null ? 0.0 : cruise.targetMs();
        cruiseTargetKmh = cruise == null ? 0 : cruise.targetKmh();
    }

    // 0 when cruise is off
    public int getCruiseTargetKmh() {
        return cruiseTargetKmh;
    }

    private void gatherTelemetry() {
        Dashboard next = Dashboard.gather(level, worldPosition, Sable.HELPER.getContaining(this), tractionControl,
                appliedThrottle01, (inputMask & (1 << SteeringControl.BRAKE.ordinal())) != 0, driverSteer01);
        boolean changed = dashboardSynced.differs(next) || steerAssistOff != assistOffSynced
                || driftDiffMode != diffModeSynced || cruiseTargetKmh != cruiseSyncedKmh;
        dashboard = next;

        if (telemetryCooldown > 0) {
            telemetryCooldown--;
        }
        if (changed && telemetryCooldown <= 0) {
            telemetryCooldown = 2;
            dashboardSynced = next;
            assistOffSynced = steerAssistOff;
            diffModeSynced = driftDiffMode;
            cruiseSyncedKmh = cruiseTargetKmh;
            notifyUpdate();
        }
    }

    public Dashboard dashboard() {
        return dashboard;
    }

    // ==========================================
    // LOGGING
    // ==========================================

    @Nullable
    private CarActors gatherCar() {
        return CarActors.of(level, Sable.HELPER.getContaining(this));
    }

    public void startTelemetryLog(Player player, int seconds, int samplesPerSec, boolean extra) {
        if (level == null || level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return;
        }
        CarActors car = gatherCar();
        if (car == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c[Motorsports] Cant find a Sable sub-level to log for this car"), false);
            return;
        }
        log = TelemetryLog.start(level, sp, car, seconds, samplesPerSec, extra);
        logStartGameTime = log.startGameTime();
    }

    public void stopTelemetryLog(Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (log == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§e[Motorsports] No log is running"), false);
            return;
        }
        log.stop();
    }

    private void tickTelemetryLog() {
        if (log != null && !log.tick(level, worldPosition, this::gatherCar, cruiseOn, cruiseTargetMs,
                terrainAssist.name(), driverInputs())) {
            log = null;
        }
    }

    public String raceTelemetryHeader() {
        CarActors car = gatherCar();
        return car == null ? null : TelemetryFormat.header(car, RACE_EXPORT_EXTRA);
    }

    public String raceTelemetryRow() {
        CarActors car = gatherCar();
        return car == null ? null : TelemetryFormat.row(level, worldPosition, car, logStartGameTime,
                cruiseOn, cruiseTargetMs, RACE_EXPORT_EXTRA, driverInputs());
    }

    private DriverInputs driverInputs() {
        return new DriverInputs(driverThrottle01, driverBrake01, driverSteer01, keys(inputMask));
    }

    // telemetry's (in_keys) column
    public static String keys(int mask) {
        StringBuilder sb = new StringBuilder();
        for (SteeringControl control : CONTROLS) {
            if ((mask & (1 << control.ordinal())) != 0) {
                if (sb.length() > 0) {
                    sb.append('+');
                }
                sb.append(control.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    // ==========================================
    // CLIENT RENDER
    // ==========================================

    public float getLerpedWheelAngle(float partialTick) {
        return (float) Mth.lerp(partialTick, lastClientWheelAngle, clientWheelAngle);
    }

    // ==========================================
    // NBT
    // ==========================================

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
        tag.putInt("ControlLayout", CONTROL_LAYOUT);
        tag.putString("TerrainAssist", terrainAssist.name());
        tag.putBoolean("AssistOff", steerAssistOff);
        tag.putBoolean("DiffMode", driftDiffMode);
        tag.putBoolean("TractionControl", tractionControl);
        if (user != null) {
            tag.putUUID("User", user);
        }
        if (clientPacket) {
            dashboard.write(tag);
            tag.putInt("CruiseKmh", cruiseTargetKmh);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            frequencyItems.set(slot, ItemStack.EMPTY);
        }
        boolean layout0 = tag.getInt("ControlLayout") < CONTROL_LAYOUT;
        ListTag freqs = tag.getList("Frequencies", Tag.TAG_COMPOUND);
        for (int i = 0; i < freqs.size(); i++) {
            CompoundTag entry = freqs.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (layout0) {
                int control = fromLayout0(slot / 2);
                slot = control < 0 ? -1 : control * 2 + slot % 2;
            }
            if (slot >= 0 && slot < SLOT_COUNT) {
                frequencyItems.set(slot, ItemStack.parseOptional(registries, entry.getCompound("Item")));
            }
        }
        Arrays.fill(keyCodes, -1);
        int[] saved = tag.getIntArray("KeyCodes");
        for (int i = 0; i < saved.length; i++) {
            int control = layout0 ? fromLayout0(i) : i;
            if (control >= 0 && control < keyCodes.length) {
                keyCodes[control] = saved[i];
            }
        }
        try {
            terrainAssist = TerrainAssist.Level.valueOf(tag.getString("TerrainAssist"));
        } catch (IllegalArgumentException ignored) {
            terrainAssist = TerrainAssist.Level.NORMAL;
        }
        steerAssistOff = tag.getBoolean("AssistOff");
        driftDiffMode = tag.getBoolean("DiffMode");
        tractionControl = tag.getBoolean("TractionControl");
        user = tag.hasUUID("User") ? tag.getUUID("User") : null;
        if (clientPacket) {
            dashboard = Dashboard.read(tag);
            cruiseTargetKmh = tag.getInt("CruiseKmh");
        }
        refreshLinks();
    }

    private static int fromLayout0(int control) {
        return control < 11 ? control : control < 13 ? -1 : control - 2;
    }
}
