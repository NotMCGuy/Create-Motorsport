package com.createmotorsport.client;

import com.createmotorsport.block.entity.SuspensionBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SuspensionRenderer extends GeoBlockRenderer<SuspensionBlockEntity> {
    public SuspensionRenderer() {
        super(new SuspensionModel());
    }
}
