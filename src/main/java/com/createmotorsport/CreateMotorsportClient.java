package com.createmotorsport;

import com.createmotorsport.client.EngineScreen;
import com.createmotorsport.client.ClutchScreen;
import com.createmotorsport.client.SuspensionRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreateMotorsport.MODID, dist = Dist.CLIENT)
public class CreateMotorsportClient {
    public CreateMotorsportClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerMenuScreens);
        modEventBus.addListener(this::registerRenderers);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CreateMotorsport.ENGINE_MENU.get(), EngineScreen::new);
        event.register(CreateMotorsport.CLUTCH_MENU.get(), ClutchScreen::new);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), context -> new SuspensionRenderer());
    }
}
