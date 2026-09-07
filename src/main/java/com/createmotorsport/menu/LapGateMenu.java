package com.createmotorsport.menu;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class LapGateMenu extends AbstractContainerMenu {
    private final BlockPos gatePos;
    private final Player player;

    public LapGateMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(CreateMotorsport.LAP_GATE_MENU.get(), containerId);
        this.gatePos = pos;
        this.player = playerInventory.player;
    }

    public LapGateMenu(int containerId, Inventory playerInventory, LapGateBlockEntity gate) {
        this(containerId, playerInventory, gate.getBlockPos());
    }

    public BlockPos getGatePos() {
        return gatePos;
    }

    public LapGateBlockEntity getGate() {
        return player.level().getBlockEntity(gatePos) instanceof LapGateBlockEntity gate ? gate : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(gatePos) instanceof LapGateBlockEntity
                && player.distanceToSqr(gatePos.getX() + 0.5, gatePos.getY() + 0.5, gatePos.getZ() + 0.5) <= 64.0;
    }
}
