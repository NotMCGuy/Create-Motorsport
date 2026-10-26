package com.createmotorsport.motorcycle;

import com.createmotorsport.fuel.FuelRules;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class MotorcycleCreativeFuel implements IFluidHandler {
    private final FluidTank template;
    public MotorcycleCreativeFuel(FluidTank template) { this.template=template; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int index) {
        if (index!=0) return FluidStack.EMPTY;
        FluidStack fluid=template.isEmpty()?new FluidStack(Fluids.LAVA,template.getCapacity()):template.getFluid().copyWithAmount(template.getCapacity());
        return FuelRules.accepts(fluid)?fluid:FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int index) { return index==0?template.getCapacity():0; }
    @Override public boolean isFluidValid(int index,FluidStack fluid) { return index==0&&FuelRules.accepts(fluid); }
    @Override public int fill(FluidStack resource,FluidAction action) {
        if (!FuelRules.accepts(resource)) return 0;
        int amount=Math.min(resource.getAmount(),template.getCapacity());
        if (action.execute()) template.setFluid(resource.copyWithAmount(template.getCapacity()));
        return amount;
    }
    @Override public FluidStack drain(FluidStack resource,FluidAction action) {
        if(resource.isEmpty()||!FluidStack.isSameFluidSameComponents(resource,getFluidInTank(0)))return FluidStack.EMPTY;
        return drain(resource.getAmount(),action);
    }
    @Override public FluidStack drain(int amount,FluidAction action) {
        var fluid=getFluidInTank(0);
        return amount<=0||fluid.isEmpty()?FluidStack.EMPTY:fluid.copyWithAmount(Math.min(amount,template.getCapacity()));
    }
}
