package com.createmotorsport.block.entity;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.createmotorsport.CreateMotorsport;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.NonNullList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class EngineBlockEntity extends GeneratingKineticBlockEntity {
    public static final int SLOT_EXHAUST = 0;
    public static final int SLOT_INTAKE = 1;
    public static final int SLOT_RED_LINK = 2;
    public static final int SLOT_BLUE_LINK = 3;
    public static final int SLOT_COUNT = 4;

    private static final int LAVA_PER_BURN = 100;
    private static final int BURN_TICKS = 100;
    private static final float IDLE_SPEED = 16.0F;
    private static final float MAX_SPEED = 96.0F;
    private static final float THROTTLE_UP_RATE = 0.01F;
    private static final float THROTTLE_DOWN_RATE = 0.025F;
    private static final float STRESS_CAPACITY = 96.0F;
    private static final int SOUND_INTERVAL = 28;

    private int burnTicks;
    private int soundCooldown;
    private float throttle;
    private float lastGeneratedSpeed;
    private boolean wasPowered;
    private int linkedThrottleSignal = 15;
    private boolean linkRegistered;
    private final IRedstoneLinkable throttleLink = new IRedstoneLinkable() {
        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int power) {
            linkedThrottleSignal = power;
        }

        @Override
        public boolean isListening() {
            return hasThrottleLink();
        }

        @Override
        public boolean isAlive() {
            return level != null && !level.isClientSide && !isRemoved() && level.isLoaded(worldPosition);
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(inventory.getItem(SLOT_RED_LINK)),
                    RedstoneLinkNetworkHandler.Frequency.of(inventory.getItem(SLOT_BLUE_LINK))
            );
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    };
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
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EngineBlockEntity engine) {
        engine.tick();

        if (level.isClientSide) {
            return;
        }

        boolean poweredBeforeTick = engine.isFueled();
        engine.updateFuel(level, pos);

        engine.updateThrottle();
        engine.updateKineticSpeedIfNeeded();
        engine.playEngineSound(level, pos);

        boolean poweredAfterTick = engine.isFueled();
        if (poweredBeforeTick != poweredAfterTick || engine.wasPowered != poweredAfterTick) {
            engine.wasPowered = poweredAfterTick;
            engine.updateGeneratedRotation();
            engine.notifyUpdate();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return throttle <= 0.0F ? 0.0F : IDLE_SPEED + ((MAX_SPEED - IDLE_SPEED) * throttle);
    }

    @Override
    public float calculateAddedStressCapacity() {
        return throttle > 0.0F ? STRESS_CAPACITY : 0.0F;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("BurnTicks", burnTicks);
        tag.putFloat("Throttle", throttle);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        burnTicks = tag.getInt("BurnTicks");
        throttle = tag.getFloat("Throttle");
        lastGeneratedSpeed = getGeneratedSpeed();
        wasPowered = isFueled();
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, items.get(slot));
        }
        syncAttachmentState();
    }

    @Override
    public void initialize() {
        super.initialize();
        refreshLinkNetwork();
    }

    @Override
    public void remove() {
        removeThrottleLink();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        removeThrottleLink();
        super.onChunkUnloaded();
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
        if (!state.hasProperty(com.createmotorsport.block.EngineBlock.HAS_EXHAUST)
                || !state.hasProperty(com.createmotorsport.block.EngineBlock.HAS_INTAKE)) {
            return;
        }

        BlockState updated = state
                .setValue(com.createmotorsport.block.EngineBlock.HAS_EXHAUST, hasExhaust())
                .setValue(com.createmotorsport.block.EngineBlock.HAS_INTAKE, hasIntake());
        if (updated != state) {
            level.setBlock(worldPosition, updated, 3);
        }
    }

    private boolean isFueled() {
        return burnTicks > 0;
    }

    private void updateFuel(Level level, BlockPos pos) {
        if (!shouldConsumeFuel()) {
            if (burnTicks > 0) {
                burnTicks--;
            }
            return;
        }

        if (burnTicks <= 1 && tryDrainLava(level, pos)) {
            burnTicks = BURN_TICKS;
            return;
        }

        if (burnTicks > 0) {
            burnTicks--;
        }
    }

    private boolean shouldConsumeFuel() {
        return !hasThrottleLink() || linkedThrottleSignal > 0;
    }

    private void updateThrottle() {
        float targetThrottle = isFueled() ? getTargetThrottle() : 0.0F;
        float rate = targetThrottle > throttle ? THROTTLE_UP_RATE : THROTTLE_DOWN_RATE;

        if (throttle < targetThrottle) {
            throttle = Math.min(targetThrottle, throttle + rate);
        } else if (throttle > targetThrottle) {
            throttle = Math.max(targetThrottle, throttle - rate);
        }
    }

    private float getTargetThrottle() {
        if (!hasThrottleLink()) {
            return 1.0F;
        }

        return Math.clamp(linkedThrottleSignal / 15.0F, 0.0F, 1.0F);
    }

    private boolean hasThrottleLink() {
        return !inventory.getItem(SLOT_RED_LINK).isEmpty() || !inventory.getItem(SLOT_BLUE_LINK).isEmpty();
    }

    private void refreshLinkNetwork() {
        if (level == null || level.isClientSide) {
            return;
        }

        if (linkRegistered) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, throttleLink);
            linkRegistered = false;
        }

        if (hasThrottleLink()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, throttleLink);
            linkRegistered = true;
        } else {
            linkedThrottleSignal = 15;
        }
    }

    private void removeThrottleLink() {
        if (level != null && !level.isClientSide && linkRegistered) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, throttleLink);
            linkRegistered = false;
        }
    }

    private void updateKineticSpeedIfNeeded() {
        float generatedSpeed = getGeneratedSpeed();
        if (Math.abs(generatedSpeed - lastGeneratedSpeed) < 1.0F) {
            return;
        }

        lastGeneratedSpeed = generatedSpeed;
        updateGeneratedRotation();
        notifyUpdate();
    }

    private void playEngineSound(Level level, BlockPos pos) {
        if (throttle <= 0.0F) {
            soundCooldown = 0;
            return;
        }

        if (soundCooldown > 0) {
            soundCooldown--;
            return;
        }

        soundCooldown = SOUND_INTERVAL;
        level.playSound(null, pos, getSoundForThrottle(), SoundSource.BLOCKS, 0.85F, getPitchForThrottle());
    }

    private SoundEvent getSoundForThrottle() {
        if (throttle < 0.25F) {
            return CreateMotorsport.ENGINE_IDLE.get();
        }
        if (throttle < 0.55F) {
            return CreateMotorsport.ENGINE_LOW.get();
        }
        if (throttle < 0.82F) {
            return CreateMotorsport.ENGINE_MID.get();
        }
        return CreateMotorsport.ENGINE_FAST.get();
    }

    private float getPitchForThrottle() {
        return 0.85F + (throttle * 0.35F);
    }

    private boolean tryDrainLava(Level level, BlockPos pos) {
        FluidStack lava = new FluidStack(Fluids.LAVA, LAVA_PER_BURN);

        for (Direction direction : Direction.values()) {
            BlockPos tankPos = pos.relative(direction);
            IFluidHandler handler = FluidUtil.getFluidHandler(level, tankPos, direction.getOpposite()).orElse(null);
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
}
