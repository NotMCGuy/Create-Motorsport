package com.createmotorsport.menu;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.EngineBlockEntity.ControlChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EngineMenu extends AbstractContainerMenu {
    private static final int ENGINE_SLOT_COUNT = EngineBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = ENGINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    public static final int COMPONENT_Y = 24;
    public static final int EXHAUST_X = 120;
    public static final int INTAKE_X = 142;
    public static final int CHANNEL_Y0 = 50;
    public static final int CHANNEL_ROW_H = 22;
    public static final int CHANNEL_SLOT_A_X = 120;
    public static final int CHANNEL_SLOT_B_X = 142;
    public static final int INV_X = 7;

    public static final int INV_Y = CHANNEL_Y0 + EngineBlockEntity.CHANNELS.length * CHANNEL_ROW_H + 12;
    public static final int HOTBAR_Y = INV_Y + 58;

    private final Container engineInventory;
    private final BlockPos enginePos;
    private boolean canTune;

    public EngineMenu(int containerId, Inventory playerInventory, BlockPos pos, boolean canTune) {
        this(containerId, playerInventory, new SimpleContainer(ENGINE_SLOT_COUNT), null, pos);
        this.canTune = canTune;
    }

    public EngineMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, false);
    }

    public EngineMenu(int containerId, Inventory playerInventory, EngineBlockEntity engine) {
        this(containerId, playerInventory, engine.getInventory(), engine, engine.getBlockPos());
    }

    private EngineMenu(int containerId, Inventory playerInventory, Container engineInventory, EngineBlockEntity engine,
                       BlockPos pos) {
        super(CreateMotorsport.ENGINE_MENU.get(), containerId);
        this.enginePos = pos;
        checkContainerSize(engineInventory, ENGINE_SLOT_COUNT);
        this.engineInventory = engineInventory;
        engineInventory.startOpen(playerInventory.player);

        for (ControlChannel channel : EngineBlockEntity.CHANNELS) {
            int y = CHANNEL_Y0 + channel.ordinal() * CHANNEL_ROW_H;
            addSlot(new FrequencySlot(engineInventory, EngineBlockEntity.channelSlotA(channel), CHANNEL_SLOT_A_X, y));
            addSlot(new FrequencySlot(engineInventory, EngineBlockEntity.channelSlotB(channel), CHANNEL_SLOT_B_X, y));
        }

        addSlot(new Slot(engineInventory, EngineBlockEntity.SLOT_EXHAUST, EXHAUST_X, COMPONENT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(CreateMotorsport.EXHAUST_MANIFOLD.get()); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(engineInventory, EngineBlockEntity.SLOT_INTAKE, INTAKE_X, COMPONENT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(CreateMotorsport.AIR_INTAKE.get()); }
            @Override public int getMaxStackSize() { return 1; }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, INV_X + column * 18, INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INV_X + column * 18, HOTBAR_Y));
        }

    }

    public boolean canTune() {
        return canTune;
    }

    public BlockPos getEnginePos() {
        return enginePos;
    }



    private static class FrequencySlot extends Slot {
        FrequencySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return engineInventory.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < ENGINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(CreateMotorsport.EXHAUST_MANIFOLD.get()) || stack.is(CreateMotorsport.AIR_INTAKE.get())) {
            if (!moveItemStackTo(stack, EngineBlockEntity.SLOT_EXHAUST, ENGINE_SLOT_COUNT, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, EngineBlockEntity.SLOT_EXHAUST, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        engineInventory.stopOpen(player);
    }
}
