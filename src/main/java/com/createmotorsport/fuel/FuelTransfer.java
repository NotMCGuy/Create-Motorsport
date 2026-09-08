package com.createmotorsport.fuel;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class FuelTransfer {
    private FuelTransfer() {}

    public static int transfer(FluidTank source, IFluidHandler target, int limit) {
        if (target == source || limit <= 0 || source.isEmpty()) return 0;
        FluidStack offer = source.getFluid().copyWithAmount(Math.min(limit, source.getFluidAmount()));
        int accepted = Math.clamp(target.fill(offer.copy(), IFluidHandler.FluidAction.SIMULATE), 0, offer.getAmount());
        if (accepted == 0) return 0;


        FluidStack drained = source.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        int inserted = Math.clamp(target.fill(drained.copy(), IFluidHandler.FluidAction.EXECUTE), 0, drained.getAmount());
        if (inserted < drained.getAmount()) {
            int amount = source.getFluidAmount() + drained.getAmount() - inserted;
            source.setFluid(drained.copyWithAmount(amount));
        }
        return inserted;
    }
}
