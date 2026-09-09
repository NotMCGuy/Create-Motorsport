package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.trailer.CouplingRules;
import com.createmotorsport.trailer.EquipmentBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;

public class TrailerRenderer implements BlockEntityRenderer<EquipmentBlockEntity> {
    private static final PartialModel JAW=part("fifth_wheel_jaw");
    private static final PartialModel HANDLE=part("release_handle");
    private static final PartialModel LATCH=part("tow_latch");
    private static final PartialModel INNER=part("landing_inner");
    private static final PartialModel FOOT=part("landing_foot");
    private static final PartialModel CRANK=part("landing_crank");
    private static final PartialModel LAMP=part("status_lamp");
    private static PartialModel part(String path) {return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID,"block/trailer/"+path));}
    public static void init() {}
    private void draw(PartialModel part,EquipmentBlockEntity be,PoseStack pose,MultiBufferSource buffer,int light) {
        CachedBuffers.partial(part,be.getBlockState()).light(light).renderInto(pose,buffer.getBuffer(RenderType.cutoutMipped()));
    }
    @Override public void render(EquipmentBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay) {
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch(be.facing()){case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));
        float scale=(float)((com.createmotorsport.trailer.EquipmentBlock)be.getBlockState().getBlock()).horizontalScale;
        float depth=(float)((com.createmotorsport.trailer.EquipmentBlock)be.getBlockState().getBlock()).depthScale();
        pose.translate((scale-1)*.5,0,(depth-1)*.5);
        pose.scale(scale,1,depth);
        pose.translate(-.5,0,-.5);
        float closed=be.jaw(partial);
        if(be.kind()==CouplingRules.Kind.FIFTH_WHEEL) {
            for(int sign:new int[]{-1,1}) {
                pose.pushPose();pose.translate(sign*(1-closed)*.15,0,0);
                if(sign==1){pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(-.5,0,-.5);}
                draw(JAW,be,pose,buffer,light);pose.popPose();
            }
            pose.pushPose();pose.translate((1-closed)*-.18,0,0);draw(HANDLE,be,pose,buffer,light);pose.popPose();
        }
        if(be.kind()==CouplingRules.Kind.COUPLING_HEAD) {
            pose.pushPose();pose.translate(.5,.75,.5);pose.mulPose(Axis.XP.rotationDegrees((1-closed)*-45));pose.translate(-.5,-.75,-.5);
            draw(LATCH,be,pose,buffer,light);pose.popPose();
        }
        if(be.kind()==CouplingRules.Kind.LANDING_LEGS) {
            for(int i=0;i<2;i++) {
                double length=be.legs.length(i,partial);
                pose.pushPose();pose.translate(i==0?-.65:.65,.2-length,0);pose.scale(1,(float)(length+.55),1);
                draw(INNER,be,pose,buffer,light);pose.popPose();
                pose.pushPose();pose.translate(i==0?-.65:.65,.2-1.5/16-length,0);
                draw(FOOT,be,pose,buffer,light);pose.popPose();
            }
            pose.pushPose();pose.translate(.5,.65,.5);
            pose.mulPose(Axis.XP.rotationDegrees((float)(be.legs.length(0,partial)*720)));pose.translate(-.5,-.65,-.5);
            draw(CRANK,be,pose,buffer,light);pose.popPose();
        }
        int color=be.status().fault()?0xE84A3C:be.connected() || be.status()==CouplingRules.Status.DEPLOYED?0x53D77A
                :be.pending() || be.status()==CouplingRules.Status.ALIGNING || be.legs.moving()?0xFFB83E:0xA5ACB5;
        CachedBuffers.partial(LAMP,be.getBlockState()).color(color).light(light).renderInto(pose,buffer.getBuffer(RenderType.cutoutMipped()));
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(EquipmentBlockEntity be){return be.kind()==CouplingRules.Kind.LANDING_LEGS || ((com.createmotorsport.trailer.EquipmentBlock)be.getBlockState().getBlock()).horizontalScale>1;}
}
