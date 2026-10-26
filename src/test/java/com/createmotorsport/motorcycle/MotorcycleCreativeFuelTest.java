package com.createmotorsport.motorcycle;

import com.createmotorsport.fuel.FuelRules;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MotorcycleCreativeFuelTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        BuiltInRegistries.FLUID.bindTags(Map.of(FuelRules.FUELS,List.of(Fluids.LAVA.builtInRegistryHolder())));
    }
    @Test void repeatedEngineDrainsDoNotDepleteCreativeFuel() {
        var storage=new FluidTank(8000,FuelRules::accepts);var fuel=new MotorcycleCreativeFuel(storage);
        for(int i=0;i<100;i++) {
            assertEquals(1000,fuel.drain(new FluidStack(Fluids.LAVA,1000),FluidAction.SIMULATE).getAmount());
            assertEquals(1000,fuel.drain(new FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE).getAmount());
        }
        assertEquals(8000,fuel.getFluidInTank(0).getAmount());assertTrue(storage.isEmpty());
        assertTrue(fuel.drain(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE).isEmpty());
        assertTrue(fuel.drain(-1,FluidAction.EXECUTE).isEmpty());
    }
    @Test void simulationsDoNotChangeTemplateAndNormalStorageStillDepletes() {
        var storage=new FluidTank(8000,FuelRules::accepts);var fuel=new MotorcycleCreativeFuel(storage);
        assertEquals(1000,fuel.fill(new FluidStack(Fluids.LAVA,1000),FluidAction.SIMULATE));assertTrue(storage.isEmpty());
        assertEquals(0,fuel.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE));
        fuel.fill(new FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE);
        assertEquals(8000,storage.getFluidAmount());fuel.drain(1000,FluidAction.EXECUTE);assertEquals(8000,storage.getFluidAmount());
        storage.drain(1000,FluidAction.EXECUTE);assertEquals(7000,storage.getFluidAmount());
        var copy=fuel.getFluidInTank(0);copy.setAmount(1);assertEquals(8000,fuel.getFluidInTank(0).getAmount());
    }
}
