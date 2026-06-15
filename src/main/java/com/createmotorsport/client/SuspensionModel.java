package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SuspensionModel extends GeoModel<SuspensionBlockEntity> {
    @Override
    public ResourceLocation getModelResource(SuspensionBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "geo/suspension.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SuspensionBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "textures/block/suspension.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SuspensionBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "animations/suspension.animation.json");
    }
}
