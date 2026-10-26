package com.createmotorsport.client.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class MotorsportPonderPlugin implements PonderPlugin {
    @Override public String getModId() { return "createmotorsport"; }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("createmotorsport", name); }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(id("engine_block"), id("passenger_engine_block"), id("truck_engine_block"), id("exhaust_manifold"), id("air_intake"))
                .addStoryBoard("engine_attachments", MotorsportScenes::attachments);
        helper.forComponents(id("fuel_pump"), id("fuel_tank"), id("fuel_nozzle"))
                .addStoryBoard("refuelling", MotorsportScenes::refuelling);
        helper.forComponents(id("suspension"), id("racing_tire"), id("truck_tire"), id("steering_wheel"))
                .addStoryBoard("suspension", MotorsportScenes::suspension);
        helper.forComponents(id("motorcycle_forks"), id("motorcycle_rear_arm"), id("motorcycle_gearbox"), id("motorcycle_handlebars"),
                id("motorcycle_seat"), id("motorcycle_fuel_tank_slab"), id("creative_motorcycle_fuel_tank_slab"))
                .addStoryBoard("motorcycle", MotorsportScenes::motorcycle);
    }
    @Override public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("parts_intro", "Fit an Exhaust Manifold and Air Filter directly to an engine by right-clicking with the part.");
        helper.registerSharedText("parts_intake", "The air filter fits the intake. Each attachment has its own slot.");
        helper.registerSharedText("parts_remove", "Open the engine, then Attachments & Redstone Links to remove or replace either part.");
        helper.registerSharedText("parts_save", "Installed parts stay with the engine when it is broken and placed again. Engine tuning is configured separately.");
        helper.registerSharedText("fuel_supply", "Supply the fuel pump through Create fluid pipes connected to a fuel source.");
        helper.registerSharedText("fuel_take", "Right-click the pump with an empty main hand to borrow its nozzle.");
        helper.registerSharedText("fuel_fill", "Right-click a nearby fuel tank with the nozzle to transfer fuel. Keep the tank next to the engine.");
        helper.registerSharedText("fuel_return", "Right-click the same pump to dock the nozzle. Stay within hose range while refuelling.");
        helper.registerSharedText("fuel_types", "Lava and tagged petrol, gasoline and diesel are accepted. Engines draw fuel from adjacent tanks.");
        helper.registerSharedText("axle_fit", "Right-click each side of a suspension with a tyre. Ordinary suspension carries two tyres.");
        helper.registerSharedText("axle_settings", "Open the suspension to choose Front or Rear and configure steering. Tune controls also configure drive.");
        helper.registerSharedText("axle_remove", "Sneak-right-click a tyre with an empty hand to remove it. A wrench cycles suspension presets.");
        helper.registerSharedText("axle_body", "Assemble the engine, suspension and controls into one moving body using your assembly tool. Keep all axles aligned.");
        helper.registerSharedText("bike_drive", "Place the gearbox immediately in front of the rear arm, with an engine beside the gearbox at the same height.");
        helper.registerSharedText("bike_wheels", "Fit one tyre to the forks and one to the rear arm. Forks steer; the connected rear arm receives engine drive.");
        helper.registerSharedText("bike_controls", "Add handlebars and a seat nearby. Sit to claim the controls, or right-click the handlebars to drive.");
        helper.registerSharedText("bike_fuel", "Place a slab fuel tank next to the engine. Fill it with an accepted fuel; a creative slab tank supplies fuel without depletion.");
        helper.registerSharedText("bike_assemble", "Assemble every bike part into the same moving body. The connected gearbox selects motorcycle profiles on an untuned engine.");
    }
}
