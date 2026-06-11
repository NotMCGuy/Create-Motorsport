package com.createmotorsport.block.entity;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.createmotorsport.CreateMotorsport;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ClutchBlockEntity extends SplitShaftBlockEntity {
    public static final int SLOT_CLUTCH = 0;
    public static final int SLOT_NEUTRAL = 1;
    public static final int SLOT_REVERSE = 2;
    public static final int SLOT_GEAR_1 = 3;
    public static final int SLOT_GEAR_2 = 4;
    public static final int SLOT_GEAR_3 = 5;
    public static final int SLOT_GEAR_4 = 6;
    public static final int SLOT_GEAR_5 = 7;
    public static final int SLOT_GEAR_6 = 8;
    public static final int SLOT_COUNT = 9;

    private static final float[] RATIOS = {0.0F, 0.0F, -0.50F, 0.50F, 0.75F, 1.00F, 1.35F, 1.70F, 2.10F};

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                items.set(slot, getItem(slot));
            }
            refreshLinkNetwork();
            ClutchBlockEntity.this.setChanged();
        }
    };
    private int selectedRatioSlot = SLOT_NEUTRAL;
    private final int[] receivedSignals = new int[SLOT_COUNT];
    private final boolean[] registeredLinks = new boolean[SLOT_COUNT];
    private final IRedstoneLinkable[] ratioLinks = new IRedstoneLinkable[SLOT_COUNT];

    public ClutchBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.CLUTCH_BLOCK_ENTITY.get(), pos, state);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ratioLinks[slot] = new RatioLink(slot);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ClutchBlockEntity clutch) {
        clutch.tick();
    }

    @Override
    public float getRotationSpeedModifier(Direction direction) {
        if (!hasSource() || direction == getSourceFacing()) {
            return 1.0F;
        }
        return RATIOS[selectedRatioSlot];
    }

    public SimpleContainer getInventory() {
        return inventory;
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("SelectedRatioSlot", selectedRatioSlot);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        selectedRatioSlot = Math.clamp(tag.getInt("SelectedRatioSlot"), 0, RATIOS.length - 1);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setItem(slot, items.get(slot));
        }
        refreshLinkNetwork();
    }

    private void setReceivedSignal(int slot, int signal) {
        receivedSignals[slot] = signal;
        int newRatioSlot = findSelectedRatioSlot();
        if (selectedRatioSlot == newRatioSlot) {
            return;
        }

        selectedRatioSlot = newRatioSlot;
        detachKinetics();
        attachKinetics();
        setChanged();
        notifyUpdate();
    }

    private int findSelectedRatioSlot() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (receivedSignals[slot] > 0 && !inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return SLOT_NEUTRAL;
    }

    private void refreshLinkNetwork() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (registeredLinks[slot]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, ratioLinks[slot]);
                registeredLinks[slot] = false;
            }

            if (!inventory.getItem(slot).isEmpty()) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, ratioLinks[slot]);
                registeredLinks[slot] = true;
            } else {
                receivedSignals[slot] = 0;
            }
        }

        selectedRatioSlot = findSelectedRatioSlot();
    }

    private void removeLinks() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (registeredLinks[slot]) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, ratioLinks[slot]);
                registeredLinks[slot] = false;
            }
        }
    }

    private class RatioLink implements IRedstoneLinkable {
        private final int slot;

        private RatioLink(int slot) {
            this.slot = slot;
        }

        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int power) {
            setReceivedSignal(slot, power);
        }

        @Override
        public boolean isListening() {
            return !inventory.getItem(slot).isEmpty();
        }

        @Override
        public boolean isAlive() {
            return level != null && !level.isClientSide && !isRemoved() && level.isLoaded(worldPosition);
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(inventory.getItem(slot)),
                    RedstoneLinkNetworkHandler.Frequency.EMPTY
            );
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }
}
