package com.createmotorsport.menu;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
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

    private final Container engineInventory;

    public EngineMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ENGINE_SLOT_COUNT));
    }

    public EngineMenu(int containerId, Inventory playerInventory, EngineBlockEntity engine) {
        this(containerId, playerInventory, engine.getInventory());
    }

    private EngineMenu(int containerId, Inventory playerInventory, Container engineInventory) {
        super(CreateMotorsport.ENGINE_MENU.get(), containerId);
        checkContainerSize(engineInventory, ENGINE_SLOT_COUNT);
        this.engineInventory = engineInventory;
        engineInventory.startOpen(playerInventory.player);

        addSlot(new Slot(engineInventory, EngineBlockEntity.SLOT_EXHAUST, 62, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(CreateMotorsport.EXHAUST_MANIFOLD.get());
            }
        });
        addSlot(new Slot(engineInventory, EngineBlockEntity.SLOT_INTAKE, 98, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(CreateMotorsport.AIR_INTAKE.get());
            }
        });
        addSlot(new Slot(engineInventory, EngineBlockEntity.SLOT_RED_LINK, 62, 56));
        addSlot(new Slot(engineInventory, EngineBlockEntity.SLOT_BLUE_LINK, 98, 56));

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
        } else if (!moveItemStackTo(stack, 0, ENGINE_SLOT_COUNT, false)) {
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
