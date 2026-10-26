package com.createmotorsport.motorcycle.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.motorcycle.MotorcycleModule;
import com.createmotorsport.motorcycle.MotorcycleSeatEntity;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateMotorsport.MODID, dist = Dist.CLIENT)
public final class MotorcycleClient {
    private MotorcyclePlayerRenderer normal;
    private MotorcyclePlayerRenderer slim;
    private boolean renderingRider;

    public MotorcycleClient(IEventBus bus) {
        MotorcycleRenderer.init();
        bus.addListener(this::renderers);
        bus.addListener(this::layers);
        NeoForge.EVENT_BUS.addListener(this::rider);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> { MotorcycleRiderState.tick(); MotorcycleSeatControls.tick(); });
    }
    private void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MotorcycleModule.SUSPENSION_ENTITY.get(), context -> new MotorcycleRenderer.Suspension());
        event.registerBlockEntityRenderer(MotorcycleModule.HANDLEBAR_ENTITY.get(), context -> new MotorcycleRenderer.Handlebars());
        event.registerBlockEntityRenderer(MotorcycleModule.MOUNT_ENTITY.get(), context -> new MotorcycleRenderer.Mount());
        event.registerEntityRenderer(MotorcycleModule.SEAT_ENTITY.get(), NoopRenderer::new);
    }
    private void layers(EntityRenderersEvent.AddLayers event) {
        MotorcycleRiderState.clear();
        MotorcycleRenderer.clear();
        normal = new MotorcyclePlayerRenderer(event.getContext(), false);
        slim = new MotorcyclePlayerRenderer(event.getContext(), true);
    }
    private void rider(RenderPlayerEvent.Pre event) {
        if (renderingRider || normal == null) return;
        var player = (net.minecraft.client.player.AbstractClientPlayer) event.getEntity();
        if (MotorcycleRiderState.get(player,event.getPartialTick()) == null) return;
        var renderer = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? slim : normal;
        event.setCanceled(true);
        renderingRider = true;
        try {
            renderer.render(player, player.getYRot(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        } finally { renderingRider = false; }
    }
}
