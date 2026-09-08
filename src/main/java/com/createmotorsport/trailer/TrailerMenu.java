package com.createmotorsport.trailer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TrailerMenu extends AbstractContainerMenu {
    private final Level level;
    private final BlockPos position;
    private final SimpleContainerData data=new SimpleContainerData(6);
    public TrailerMenu(int id,Inventory inv,BlockPos pos) {
        super(TrailerRegistry.PANEL_MENU.get(),id);level=inv.player.level();position=pos;addDataSlots(data);
    }
    public int value(int index) {return data.get(index);}
    @Override public void broadcastChanges() {
        if(!level.isClientSide && level.getBlockEntity(position) instanceof EquipmentBlockEntity panel) {
            var hitch=panel.linked(false);var legs=panel.linked(true);
            data.set(0,panel.status().ordinal());
            data.set(1,hitch==null?CouplingRules.Status.UNLINKED.ordinal():hitch.status().ordinal());
            data.set(2,legs==null?CouplingRules.Status.UNLINKED.ordinal():legs.status().ordinal());
            data.set(3,legs==null?0:legs.legs.progress());
            data.set(4,TrailerConfig.REMOTE_RELEASE.get()?1:0);
            data.set(5,(hitch==null?0:1)+(legs==null?0:2));
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(button<0 || button>2 || !stillValid(player))return false;
        if(!level.isClientSide && level.getBlockEntity(position) instanceof EquipmentBlockEntity panel)panel.panelAction(player,button);
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) {return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player) {
        return level.hasChunkAt(position) && level.getBlockEntity(position) instanceof EquipmentBlockEntity panel
                && panel.kind()==CouplingRules.Kind.CONTROL_PANEL && (level.isClientSide || panel.canUse(player));
    }
}
