package com.createmotorsport.menu;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity.SteeringControl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SteeringWheelMenu extends AbstractContainerMenu {
    private static final int GHOST_COUNT = SteeringWheelBlockEntity.SLOT_COUNT;

    public static final int WIDTH = 286;
    public static final int ROW_Y0 = 24;
    public static final int ROW_H = 19;

    public static final int ROWS_PER_COL = (SteeringWheelBlockEntity.CONTROLS.length + 1) / 2;
    private static final int COL0_X = 8;
    private static final int COL_STRIDE = 140;
    private static final int LABEL_OFF = 4;
    private static final int SLOT_A_OFF = 90;
    private static final int SLOT_B_OFF = 112;
    public static final int COLUMN_W = SLOT_B_OFF + 18;

    public static int columnX(int ordinal) { return COL0_X + (ordinal < ROWS_PER_COL ? 0 : COL_STRIDE); }
    public static int rowY(int ordinal)    { return ROW_Y0 + (ordinal < ROWS_PER_COL ? ordinal : ordinal - ROWS_PER_COL) * ROW_H; }
    public static int labelX(int ordinal)  { return columnX(ordinal) + LABEL_OFF; }
    public static int slotAX(int ordinal)  { return columnX(ordinal) + SLOT_A_OFF; }
    public static int slotBX(int ordinal)  { return columnX(ordinal) + SLOT_B_OFF; }

    // inventory
    public static final int INV_X = (WIDTH - 9 * 18) / 2;
    public static final int INV_Y = ROW_Y0 + ROWS_PER_COL * ROW_H + 10;
    public static final int HOTBAR_Y = INV_Y + 58;

    private final Level level;
    private final BlockPos wheelPos;
    private final SimpleContainer ghosts;
    private boolean suppressWriteThrough;

    // Server: wheel + position
    public SteeringWheelMenu(int containerId, Inventory playerInventory, SteeringWheelBlockEntity wheel) {
        this(containerId, playerInventory, wheel.getBlockPos());
    }

    // Client: reads wheel position
    public SteeringWheelMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(CreateMotorsport.STEERING_WHEEL_MENU.get(), containerId);
        this.level = playerInventory.player.level();
        this.wheelPos = pos;

        this.ghosts = new SimpleContainer(GHOST_COUNT) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!suppressWriteThrough && !level.isClientSide) {
                    SteeringWheelBlockEntity be = currentBe();
                    if (be != null) {
                        for (int i = 0; i < GHOST_COUNT; i++) {
                            be.setFrequencyItem(i, getItem(i));
                        }
                    }
                }
            }
        };

        // Seed the ghosts from the block entity
        SteeringWheelBlockEntity be = currentBe();
        if (be != null) {
            suppressWriteThrough = true;
            try {
                for (int i = 0; i < GHOST_COUNT; i++) {
                    ghosts.setItem(i, be.getFrequencyItem(i));
                }
            } finally {
                suppressWriteThrough = false;
            }
        }

        for (SteeringControl control : SteeringWheelBlockEntity.CONTROLS) {
            int o = control.ordinal();
            addGhostSlot(SteeringWheelBlockEntity.slotA(control), slotAX(o), rowY(o));
            addGhostSlot(SteeringWheelBlockEntity.slotB(control), slotBX(o), rowY(o));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, INV_X + column * 18, INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INV_X + column * 18, HOTBAR_Y));
        }
    }

    private void addGhostSlot(int index, int x, int y) {
        addSlot(new Slot(ghosts, index, x, y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });
    }

    public BlockPos getWheelPos() {
        return wheelPos;
    }

    private SteeringWheelBlockEntity currentBe() {
        BlockEntity be = level.getBlockEntity(wheelPos);
        return be instanceof SteeringWheelBlockEntity wheel ? wheel : null;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < GHOST_COUNT) {
            Slot slot = slots.get(slotId);
            ItemStack carried = getCarried();
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                if (!carried.isEmpty()) {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    slot.set(copy);
                } else {
                    slot.set(ItemStack.EMPTY);
                }
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) {
            SteeringWheelBlockEntity be = currentBe();
            if (be != null) {
                suppressWriteThrough = true;
                try {
                    for (int i = 0; i < GHOST_COUNT; i++) {
                        ItemStack target = be.getFrequencyItem(i);
                        if (!ItemStack.matches(ghosts.getItem(i), target)) {
                            ghosts.setItem(i, target);
                        }
                    }
                } finally {
                    suppressWriteThrough = false;
                }
            }
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking a ghost clears it; ghosts never steal yo shi'
        if (index >= 0 && index < GHOST_COUNT) {
            slots.get(index).set(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return currentBe() != null && player.distanceToSqr(
                wheelPos.getX() + 0.5, wheelPos.getY() + 0.5, wheelPos.getZ() + 0.5) < 64 * 64;
    }
}
