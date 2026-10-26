package com.createmotorsport;

import com.createmotorsport.client.hud.MotorsportHud;
import com.createmotorsport.client.input.MouseSteerCamera;
import com.createmotorsport.client.screen.TrailerScreen;
import com.createmotorsport.client.screen.LapGateScreen;
import com.createmotorsport.client.render.GhostManager;
import com.createmotorsport.client.render.LapGateLinkPreview;
import com.createmotorsport.client.render.SkidmarkManager;
import com.createmotorsport.client.render.TrailerRenderer;
import com.createmotorsport.client.render.FuelPumpRenderer;
import com.createmotorsport.block.SteeringWheelBlock;
import com.createmotorsport.client.render.DownFlapRenderer;
import com.createmotorsport.client.screen.EngineScreen;
import com.createmotorsport.client.MotorsportCommands;
import com.createmotorsport.client.input.MotorsportKeybinds;
import com.createmotorsport.client.render.MotorsportPartialModels;
import com.createmotorsport.client.input.SteeringInputHandler;
import com.createmotorsport.client.screen.SteeringWheelScreen;
import com.createmotorsport.client.render.SteeringWheelRenderer;
import com.createmotorsport.client.render.SuspensionRenderer;
import com.createmotorsport.client.screen.SuspensionScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateMotorsport.MODID, dist = Dist.CLIENT)
public class CreateMotorsportClient {
    public CreateMotorsportClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(() -> net.createmod.ponder.foundation.PonderIndex.addPlugin(
                        new com.createmotorsport.client.ponder.MotorsportPonderPlugin())));
        MotorsportPartialModels.init();
        com.createmotorsport.client.render.TrailerRenderer.init();
        modEventBus.addListener(this::registerMenuScreens);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(MotorsportPartialModels::scanParts);
        modEventBus.addListener(MotorsportKeybinds::register);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        SteeringWheelBlock.clientDriveToggle = SteeringInputHandler::toggleDriving;

        NeoForge.EVENT_BUS.addListener(SteeringInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(SteeringInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(SteeringInputHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.input.MouseSteerCamera::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(MotorsportCommands::register);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.hud.MotorsportHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.render.SkidmarkManager::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.render.LapGateLinkPreview::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.render.GhostManager::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) {
                com.createmotorsport.client.render.SkidmarkManager.clear();
                com.createmotorsport.client.render.GhostManager.clear();
            }
        });
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(com.createmotorsport.trailer.TrailerRegistry.PANEL_MENU.get(), com.createmotorsport.client.screen.TrailerScreen::new);
        event.register(CreateMotorsport.ENGINE_MENU.get(), EngineScreen::new);
        event.register(CreateMotorsport.SUSPENSION_MENU.get(), SuspensionScreen::new);
        event.register(CreateMotorsport.STEERING_WHEEL_MENU.get(), SteeringWheelScreen::new);
        event.register(CreateMotorsport.LAP_GATE_MENU.get(), com.createmotorsport.client.screen.LapGateScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.createmotorsport.trailer.TrailerRegistry.EQUIPMENT_ENTITY.get(), context -> new com.createmotorsport.client.render.TrailerRenderer());
        event.registerBlockEntityRenderer(CreateMotorsport.FUEL_PUMP_ENTITY.get(), context -> new com.createmotorsport.client.render.FuelPumpRenderer());
        event.registerBlockEntityRenderer(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), context -> new SuspensionRenderer());
        event.registerBlockEntityRenderer(CreateMotorsport.STEERING_WHEEL_BLOCK_ENTITY.get(), context -> new SteeringWheelRenderer());
        event.registerBlockEntityRenderer(CreateMotorsport.DOWN_FLAP_BLOCK_ENTITY.get(), DownFlapRenderer::new);
    }
}
