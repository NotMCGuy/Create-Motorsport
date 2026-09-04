package com.createmotorsport;

import com.createmotorsport.client.DownFlapRenderer;
import com.createmotorsport.client.EngineScreen;
import com.createmotorsport.client.MotorsportCommands;
import com.createmotorsport.client.MotorsportKeybinds;
import com.createmotorsport.client.MotorsportPartialModels;
import com.createmotorsport.client.SteeringInputHandler;
import com.createmotorsport.client.SteeringWheelScreen;
import com.createmotorsport.client.SteeringWheelRenderer;
import com.createmotorsport.client.SuspensionRenderer;
import com.createmotorsport.client.SuspensionScreen;
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
        MotorsportPartialModels.init();
        modEventBus.addListener(this::registerMenuScreens);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(MotorsportKeybinds::register);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Steering wheel live input intercepts keys and streams the driver's controls
        NeoForge.EVENT_BUS.addListener(SteeringInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(SteeringInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(SteeringInputHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.MouseSteerCamera::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(MotorsportCommands::register);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.MotorsportHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(com.createmotorsport.client.SkidmarkManager::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) {
                com.createmotorsport.client.SkidmarkManager.clear();
            }
        });
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CreateMotorsport.ENGINE_MENU.get(), EngineScreen::new);
        event.register(CreateMotorsport.SUSPENSION_MENU.get(), SuspensionScreen::new);
        event.register(CreateMotorsport.STEERING_WHEEL_MENU.get(), SteeringWheelScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), context -> new SuspensionRenderer());
        event.registerBlockEntityRenderer(CreateMotorsport.STEERING_WHEEL_BLOCK_ENTITY.get(), context -> new SteeringWheelRenderer());
        event.registerBlockEntityRenderer(CreateMotorsport.DOWN_FLAP_BLOCK_ENTITY.get(), DownFlapRenderer::new);
    }
}
