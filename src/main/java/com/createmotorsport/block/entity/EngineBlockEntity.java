package com.createmotorsport.block.entity;

import com.createmotorsport.physics.control.IgnitionSwitch;
import com.createmotorsport.Config;
import com.createmotorsport.physics.spec.TorqueSplitSpec;
import com.createmotorsport.redstone.LinkReceivers;
import com.createmotorsport.tune.SpecSlot;
import com.createmotorsport.tune.Tunable;
import com.createmotorsport.vehicle.AssembledCar;
import com.createmotorsport.vehicle.CarAssembler;
import com.createmotorsport.physics.spec.TractionControlSpec;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.physics.drivetrain.DrivetrainSim;
import com.createmotorsport.physics.spec.GearboxSpec;
import com.createmotorsport.registry.SpecCodecs;
import com.createmotorsport.physics.control.PowerMode;
import com.createmotorsport.registry.SpecOverrides;
import com.createmotorsport.registry.SpecRegistries;
import com.createmotorsport.registry.EngineKind;
import com.createmotorsport.fuel.FuelDraw;
import com.createmotorsport.physics.spec.EngineSpec;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class EngineBlockEntity extends SmartBlockEntity implements com.createmotorsport.menu.PartPanel, dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor,
        Tunable {


    public static final java.util.Set<String> CONFIGURATION_KEYS = java.util.Set.of(
            "Engine", "Gearbox", "EngineOverrides", "GearboxOverrides", "RotationDirection",
            "PowerMode", "Items", "SlotLayout");

    private static final int FUEL_PER_BURN = 100;
    private static final int BURN_TICKS = 100;

    public static final ControlChannel[] CHANNELS = ControlChannel.values();
    public static final int SLOT_CHANNELS_START = 0;
    public static final int SLOT_EXHAUST = CHANNELS.length * 2;
    public static final int SLOT_INTAKE = SLOT_EXHAUST + 1;
    public static final int SLOT_COUNT = SLOT_INTAKE + 1;

    private static final int LEGACY_CHANNELS_START = 2;

    public static int channelSlotA(ControlChannel channel) {
        return SLOT_CHANNELS_START + channel.ordinal() * 2;
    }

    public static int channelSlotB(ControlChannel channel) {
        return SLOT_CHANNELS_START + channel.ordinal() * 2 + 1;
    }

    public enum ControlChannel {
        THROTTLE("Throttle"),
        CLUTCH("Clutch"),
        SHIFT_UP("Shift Up"),
        SHIFT_DOWN("Shift Down"),
        IGNITION("Ignition");

        private final String displayName;

        ControlChannel(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }


    private final EngineKind kind;
    private final SpecSlot<EngineSpec> engineSlot;
    private final SpecSlot<GearboxSpec> gearboxSlot;
    private EngineSpec baseSpec;

    private final DrivetrainSim drivetrain;
    private double torqueScale;
    private int rotationDirection = 1;
    private float throttle;
    private int burnTicks;

    private final IgnitionSwitch ignition = new IgnitionSwitch();
    private final PowerMode power = new PowerMode();

    private final int[] driverSignals = new int[CHANNELS.length];
    private float driverThrottle01;
    private long driverSignalTime = Long.MIN_VALUE;
    private boolean driverIgnition;
    private long keyedTime = Long.MIN_VALUE;
    private boolean auxOvertake;
    private boolean auxModeUp;
    private boolean auxModeDown;
    private boolean lastAuxModeUp;
    private boolean lastAuxModeDown;
    private int lastShiftUpSignal;
    private int lastShiftDownSignal;

    private boolean loadingInventory;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!loadingInventory) {
                syncAttachmentState();
                EngineBlockEntity.this.setChanged();
            }
        }
    };
    private final LinkReceivers links = new LinkReceivers(this, inventory, CHANNELS.length,
            i -> channelSlotA(CHANNELS[i]), i -> channelSlotB(CHANNELS[i]));

    private double telemAvgWheelOmega;
    private int telemDrivenWheels;
    private boolean clientSoundRunning;


    public EngineBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.ENGINE_BLOCK_ENTITY.get(), pos, state);
        this.kind = EngineKind.of(state);
        this.engineSlot = new SpecSlot<>("engine", "Engine", SpecRegistries.ENGINE, SpecCodecs.ENGINE_SPEC,
                SpecRegistries.BUILT_IN_ENGINES,
                (access, key) -> SpecRegistries.fittedEngine(access, key, EngineKind.engineFallback(key, this.kind)),
                "Engine", this.kind.engine());
        this.gearboxSlot = new SpecSlot<>("gearbox", "Gearbox", SpecRegistries.GEARBOX, SpecCodecs.GEARBOX_SPEC,
                SpecRegistries.BUILT_IN_GEARBOXES,
                (access, key) -> SpecRegistries.fittedGearbox(access, key, EngineKind.gearboxFallback(key, this.kind)),
                "Gearbox", this.kind.gearbox());
        this.baseSpec = EngineKind.engineFallback(this.engineSlot.id(), this.kind);
        this.drivetrain = new DrivetrainSim(baseSpec, EngineKind.gearboxFallback(this.gearboxSlot.id(), this.kind));
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, items.get(slot));
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        syncAttachmentState();
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


    @Override
    public com.createmotorsport.menu.PartKind partKind() {
        return com.createmotorsport.menu.PartKind.ENGINE;
    }

    @Override
    public List<Component> statusLines() {
        String state = drivetrain.firing() ? "Running" : ignition.on() ? "Stopped, ignition on" : "Switched off";
        return List.of(
                Component.literal("Engine: " + engineSlot.id().location().getPath()),
                Component.literal("Gearbox: " + gearboxSlot.id().location().getPath()),
                Component.literal(state + ", " + getDisplayRpm() + " rpm, gear " + drivetrain.gearDisplay()),
                Component.literal("Fuel in reach: " + FuelDraw.inReach(level, worldPosition) + " mB"),
                Component.literal("Power mode " + power.mode()));
    }

    public int getRotationDirection() {
        return rotationDirection;
    }

    public void toggleRotationDirection() {
        if (level == null || level.isClientSide) {
            return;
        }
        rotationDirection = -rotationDirection;
        setChanged();
        sendData();
    }

    public ResourceKey<GearboxSpec> getGearboxId() {
        return gearboxSlot.id();
    }

    public ResourceKey<EngineSpec> getEngineId() {
        return engineSlot.id();
    }

    public ResourceKey<EngineSpec> cycleEngine() {
        return cycleEngine(1);
    }

    public ResourceKey<EngineSpec> cycleEngine(int step) {
        engineSlot.cycle(levelRegistryAccess(), step);
        notifyUpdate();
        return engineSlot.id();
    }

    public ResourceKey<GearboxSpec> cycleGearbox() {
        return cycleGearbox(1);
    }

    public ResourceKey<GearboxSpec> cycleGearbox(int step) {
        gearboxSlot.cycle(levelRegistryAccess(), step);
        notifyUpdate();
        return gearboxSlot.id();
    }

    public EngineSpec packEngine() {
        return engineSlot.pack(levelRegistryAccess());
    }

    public GearboxSpec packGearbox() {
        return gearboxSlot.pack(levelRegistryAccess());
    }

    public CompoundTag getEngineOverrides() {
        return engineSlot.overrides();
    }

    public CompoundTag getGearboxOverrides() {
        return gearboxSlot.overrides();
    }

    @org.jetbrains.annotations.Nullable
    public String setEngineOverride(String path, String value) {
        return tuneSection("engine").set(path, value);
    }

    public void clearEngineOverride(String path) {
        tuneSection("engine").clear(path);
    }

    @org.jetbrains.annotations.Nullable
    public String setGearboxOverride(String path, String value) {
        return tuneSection("gearbox").set(path, value);
    }

    public void clearGearboxOverride(String path) {
        tuneSection("gearbox").clear(path);
    }

    private void overridesChanged() {
        setChanged();
        sendData();
    }

    @Override
    public List<Tunable.Section<?>> tuneSections() {
        return List.of(
                engineSlot.section(levelRegistryAccess(), this::overridesChanged, this::cycleEngine),
                gearboxSlot.section(levelRegistryAccess(), this::overridesChanged, this::cycleGearbox));
    }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.core.RegistryAccess levelRegistryAccess() {
        return level == null ? null : level.registryAccess();
    }


    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            com.createmotorsport.client.audio.EngineAudio.tick(this);
            return;
        }
        AssembledCar car = CarAssembler.of(this);
        if (car != null && car.steps(this)) {
            car.gameTick(level);
            return;
        }
        beginStep();
        gameStep(0.0, 0);
        coupling(0.0, 1.0 / 20.0, 0.0);
        settle(0.0);
    }

    public void beginStep() {
        if (com.createmotorsport.motorcycle.MotorcycleGearboxBlock.hasConnectedGearbox(this)) {
            boolean changed = false;
            if (engineSlot.id().equals(kind.engine()) && engineSlot.overrides().isEmpty()) {
                engineSlot.select(SpecRegistries.MOTORCYCLE_I4);
                changed = true;
            }
            if (gearboxSlot.id().equals(kind.gearbox()) && gearboxSlot.overrides().isEmpty()) {
                gearboxSlot.select(SpecRegistries.MOTORCYCLE_QUICKSHIFT_6);
                changed = true;
            }
            if (changed) overridesChanged();
        }
        GearboxSpec fittedGearbox = gearboxSlot.fitted(levelRegistryAccess());
        if (fittedGearbox != drivetrain.gearbox()) {
            drivetrain.setGearbox(fittedGearbox);
        }
        EngineSpec fittedEngine = engineSlot.fitted(levelRegistryAccess());
        if (fittedEngine != baseSpec) {
            baseSpec = fittedEngine;
            drivetrain.setSpec(baseSpec);
        }

        updateFuel();
        updateThrottle();
    }

    public void gameStep(double wheelOmega, int wheels) {
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
        if (modeUp && !lastAuxModeUp) {
            power.up();
        }
        if (modeDown && !lastAuxModeDown) {
            power.down();
        }
        lastAuxModeUp = modeUp;
        lastAuxModeDown = modeDown;

        boolean starter = ignition.step(driving && driverIgnition, keyed(), hasLink(ControlChannel.IGNITION),
                links.signal(ControlChannel.IGNITION.ordinal()) > 0, drivetrain.firing());
        drivetrain.settle(wheelOmega);
        drivetrain.tick(ignition.on(), isFueled(), starter, throttle, clutchHeld, shiftUpEdge, shiftDownEdge,
                wheelOmega, 1.0 / 20.0);
        this.torqueScale = drivetrain.torqueScale(power.step(driving && auxOvertake));
        this.telemDrivenWheels = wheels;

        if (Config.ENABLE_DEBUG_LOGGING.getAsBoolean() && level.getGameTime() % 20 == 0) {
            CreateMotorsport.LOGGER.info(
                    "[Engine {}] driver={} gear={} rpm={} throttle={} clutch={} up={} down={} wheels={} torque={}",
                    worldPosition, driverActive(), drivetrain.gearDisplay(),
                    getDisplayRpm(), String.format("%.2f", throttle),
                    signalFor(ControlChannel.CLUTCH), signalFor(ControlChannel.SHIFT_UP),
                    signalFor(ControlChannel.SHIFT_DOWN), wheels,
                    String.format("%.1f", getWheelTorqueTotal()));
        }
        if ((drivetrain.firing() && level.getGameTime() % 4 == 0) || clientSoundRunning != drivetrain.firing()) {
            clientSoundRunning = drivetrain.firing();
            sendData();
        }
    }

    public DrivetrainSim.Coupling coupling(double wheelOmega, double dt, double dragThrottle) {
        DrivetrainSim.Coupling c = drivetrain.couple(wheelOmega, dt, dragThrottle);
        this.telemAvgWheelOmega = wheelOmega;
        return new DrivetrainSim.Coupling(c.torque() * torqueScale, c.stiffness() * torqueScale,
                c.limit() * torqueScale);
    }

    public void settle(double wheelOmega) {
        drivetrain.settle(wheelOmega);
    }


    private boolean isFueled() {
        return burnTicks > 0;
    }

    private void updateFuel() {
        if (burnTicks <= 1 && ignition.on() && FuelDraw.drain(level, worldPosition, FUEL_PER_BURN)) {
            burnTicks = BURN_TICKS;
            return;
        }
        if (burnTicks > 0 && drivetrain.firing()) {
            burnTicks--;
        }
    }

    private void updateThrottle() {
        float target = drivetrain.firing() ? getTargetThrottle() : 0.0F;
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


    public void setDriverControls(float throttle01, boolean clutch, boolean shiftUp, boolean shiftDown) {
        if (level == null || level.isClientSide) {
            return;
        }
        driverThrottle01 = Mth.clamp(throttle01, 0.0F, 1.0F);
        driverSignals[ControlChannel.THROTTLE.ordinal()] = Math.round(driverThrottle01 * 15);
        driverSignals[ControlChannel.CLUTCH.ordinal()] = clutch ? 15 : 0;
        driverSignals[ControlChannel.SHIFT_UP.ordinal()] = shiftUp ? 15 : 0;
        driverSignals[ControlChannel.SHIFT_DOWN.ordinal()] = shiftDown ? 15 : 0;
        driverSignalTime = level.getGameTime();
    }

    public void setDriverIgnition(boolean held) {
        if (level == null || level.isClientSide) {
            return;
        }
        driverIgnition = held;
        driverSignalTime = level.getGameTime();
    }

    public void markKeyed() {
        if (level != null) {
            keyedTime = level.getGameTime();
        }
    }

    private boolean keyed() {
        return level != null && keyedTime >= level.getGameTime() - 2;
    }

    public boolean ignitionOn() {
        return ignition.on();
    }

    public void setDriverAids(boolean overtake, boolean modeUp, boolean modeDown) {
        if (level == null || level.isClientSide) {
            return;
        }
        auxOvertake = overtake;
        auxModeUp = modeUp;
        auxModeDown = modeDown;
        driverSignalTime = level.getGameTime();
    }

    private boolean driverActive() {
        return level != null && driverSignalTime >= level.getGameTime() - 2;
    }

    private int signalFor(ControlChannel channel) {
        if (driverActive()) {
            return driverSignals[channel.ordinal()];
        }
        return links.signal(channel.ordinal());
    }

    private boolean hasLink(ControlChannel channel) {
        return links.hasLink(channel.ordinal());
    }

    private void refreshLinkNetwork() {
        links.refresh();
    }

    private void removeLinks() {
        links.remove();
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    private void syncAttachmentState() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            items.set(slot, inventory.getItem(slot));
        }
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        BlockState updated = state.setValue(EngineBlock.HAS_EXHAUST, hasExhaust())
                .setValue(EngineBlock.HAS_INTAKE, hasIntake());
        if (updated != state) level.setBlock(worldPosition, updated, 3);
        refreshLinkNetwork();
    }

    public boolean hasExhaust() { return inventory.getItem(SLOT_EXHAUST).is(CreateMotorsport.EXHAUST_MANIFOLD.get()); }
    public boolean hasIntake() { return inventory.getItem(SLOT_INTAKE).is(CreateMotorsport.AIR_INTAKE.get()); }


    public int driveSign(Direction suspensionFacing) {
        return rotationDirection * facingSign(getBlockState().getValue(EngineBlock.FACING), suspensionFacing);
    }

    private static int facingSign(Direction engineFacing, Direction suspensionFacing) {
        int dot = engineFacing.getStepX() * suspensionFacing.getStepX()
                + engineFacing.getStepZ() * suspensionFacing.getStepZ();
        return dot < 0 ? -1 : 1;
    }

    public TorqueSplitSpec centerDiff() {
        return drivetrain.gearbox().centerDiff();
    }

    public TorqueSplitSpec interAxleDiff() {
        return drivetrain.gearbox().interAxleDiff();
    }

    public TractionControlSpec tractionControlSpec() {
        return drivetrain.spec().tractionControl();
    }

    public double wheelTorquePerThrottle() {
        return drivetrain.wheelTorquePerThrottle() * torqueScale;
    }

    public int getPowerMode() {
        return power.mode();
    }

    public double getBoostReserve() {
        return power.reserve();
    }

    public boolean isBoosting() {
        return power.boosting();
    }

    public double getPowerFactor() {
        return power.factor();
    }

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
        return drivetrain.lastWheelTorque() * torqueScale;
    }

    public double getAvgWheelOmega() {
        return telemAvgWheelOmega;
    }

    public int getDrivenWheelCount() {
        return telemDrivenWheels;
    }

    public int getDisplayRpm() {
        return (int) (drivetrain.getRpmFraction() * drivetrain.spec().redlineRpm());
    }

    public int getGearCode() {
        return drivetrain.gearCode();
    }


    private double clientSoundRpmFraction;

    public boolean soundRunning() { return clientSoundRunning; }
    public double soundRpmFraction() { return clientSoundRpmFraction; }


    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putBoolean("SoundRunning", drivetrain.firing());
            tag.putDouble("SoundRpmFraction", drivetrain.getRpmFraction());
        }
        tag.putInt("BurnTicks", burnTicks);
        tag.putBoolean("Ignition", ignition.on());
        tag.putFloat("Throttle", throttle);
        tag.putInt("PowerMode", power.mode());
        tag.putInt("RotationDirection", rotationDirection);
        gearboxSlot.write(tag);
        engineSlot.write(tag);
        tag.putDouble("Rpm", drivetrain.getRpm());
        tag.putInt("Gear", drivetrain.getGearIndex());
        tag.putInt("SlotLayout", 2);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            clientSoundRunning = tag.getBoolean("SoundRunning");
            clientSoundRpmFraction = tag.getDouble("SoundRpmFraction");
        }
        burnTicks = tag.getInt("BurnTicks");
        ignition.setOn(!tag.contains("Ignition") || tag.getBoolean("Ignition"));
        throttle = tag.getFloat("Throttle");
        power.setMode(tag.contains("PowerMode") ? tag.getInt("PowerMode") : PowerMode.MAX);
        rotationDirection = tag.getInt("RotationDirection") < 0 ? -1 : 1;
        gearboxSlot.read(tag, kind::gearbox);
        engineSlot.read(tag, kind::engine);
        if (!clientPacket) {
            migrateDesignMass(tag);
        }
        drivetrain.restore(tag.getDouble("Rpm"), tag.getInt("Gear"));
        items.clear();
        if (tag.getInt("SlotLayout") >= 1) {
            ContainerHelper.loadAllItems(tag, items, registries);
        } else {
            NonNullList<ItemStack> legacy = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag, legacy, registries);
            for (int slot = 0; slot < CHANNELS.length * 2; slot++)
                items.set(slot, legacy.get(LEGACY_CHANNELS_START + slot));
            items.set(SLOT_EXHAUST, legacy.get(0));
            items.set(SLOT_INTAKE, legacy.get(1));
        }
        loadingInventory = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) inventory.setItem(slot, items.get(slot));
        } finally {
            loadingInventory = false;
        }
        syncAttachmentState();
    }


    private void migrateDesignMass(CompoundTag tag) {
        double old = tag.getDouble("DesignMassOverride");
        if (old > 0.0 && !SpecOverrides.isSet(engineSlot.overrides(), "design_vehicle_mass_blocks")) {
            CompoundTag migrated = engineSlot.overrides().copy();
            migrated.putDouble("design_vehicle_mass_blocks", Mth.clamp(old, 1.0, 100000.0));
            engineSlot.setOverrides(migrated);
        }
    }
}
