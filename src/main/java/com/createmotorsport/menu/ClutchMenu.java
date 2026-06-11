package com.createmotorsport.menu;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.ClutchBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ClutchMenu extends AbstractContainerMenu {
    private static final int CLUTCH_SLOT_COUNT = ClutchBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = CLUTCH_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private final Container clutchInventory;

    public ClutchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CLUTCH_SLOT_COUNT));
    }

    public ClutchMenu(int containerId, Inventory playerInventory, ClutchBlockEntity clutch) {
        this(containerId, playerInventory, clutch.getInventory());
    }

    private ClutchMenu(int containerId, Inventory playerInventory, Container clutchInventory) {
        super(CreateMotorsport.CLUTCH_MENU.get(), containerId);
        checkContainerSize(clutchInventory, CLUTCH_SLOT_COUNT);
        this.clutchInventory = clutchInventory;
        clutchInventory.startOpen(playerInventory.player);

        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_CLUTCH, 26, 20));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_NEUTRAL, 62, 20));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_REVERSE, 98, 20));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_GEAR_1, 26, 56));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_GEAR_2, 44, 56));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_GEAR_3, 62, 56));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_GEAR_4, 80, 56));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_GEAR_5, 98, 56));
        addSlot(new Slot(clutchInventory, ClutchBlockEntity.SLOT_GEAR_6, 116, 56));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 198));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return clutchInventory.stillValid(player);
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

        if (index < CLUTCH_SLOT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, CLUTCH_SLOT_COUNT, false)) {
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
        clutchInventory.stopOpen(player);
    }
}
