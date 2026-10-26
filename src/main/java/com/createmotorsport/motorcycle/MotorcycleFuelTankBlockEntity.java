package com.createmotorsport.motorcycle;

import com.createmotorsport.block.entity.FuelTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class MotorcycleFuelTankBlockEntity extends FuelTankBlockEntity {
    private final IFluidHandler creativeHandler;
    public MotorcycleFuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(pos,state);
        tank.setCapacity(Math.max(1, com.createmotorsport.Config.FUEL_TANK_CAPACITY.get()/2));
        creativeHandler = new MotorcycleCreativeFuel(tank);
    }
    @Override public BlockEntityType<?> getType() { return MotorcycleModule.FUEL_TANK_ENTITY.get(); }
    public IFluidHandler handler() { return ((MotorcycleFuelTankBlock)getBlockState().getBlock()).creative ? creativeHandler : tank; }
    @Override protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean packet) {
        super.read(tag,provider,packet);
        if (tank.getFluidAmount()>tank.getCapacity()) tank.setFluid(tank.getFluid().copyWithAmount(tank.getCapacity()));
    }
}
