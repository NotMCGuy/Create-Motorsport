package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.fuel.FuelRules;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import java.util.List;

public class FuelTankBlockEntity extends SmartBlockEntity {
    public final FluidTank tank = new FluidTank(Config.FUEL_TANK_CAPACITY.get(), FuelRules::accepts) {
        @Override protected void onContentsChanged() { notifyUpdate(); }
    };

    public FuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.FUEL_TANK_ENTITY.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
