package com.createmotorsport.client.render;

import com.createmotorsport.CreateMotorsport;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;


public class MotorsportPartialModels {
    public static final PartialModel DASHBOARD_WHEEL = block("dashboard_wheel");
    public static final PartialModel DOWNFLAP_WING = block("down_flap/wing");

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "block/" + path));
    }

    public static void init() {
    }

    private static final Map<ResourceLocation, PartialModel> PARTS = new HashMap<>();

    private static final String ROOT = "models/parts";

    @Nullable
    public static PartialModel part(ResourceLocation modelId) {
        return PARTS.get(modelId);
    }

    public static void scanParts(ModelEvent.RegisterAdditional event) {
        PARTS.clear();
        Minecraft.getInstance().getResourceManager()
                .listResources(ROOT, path -> path.getPath().endsWith(".json"))
                .keySet()
                .forEach(resource -> {
                    // assets/<ns>/models/parts/tire/x.json is the model <ns>:parts/tire/x
                    String path = resource.getPath();
                    String modelPath = path.substring("models/".length(), path.length() - ".json".length());
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), modelPath);
                    event.register(ModelResourceLocation.standalone(id));
                    PARTS.put(id, PartialModel.of(id));
                });
        CreateMotorsport.LOGGER.info("[PartArt] {} model(s) under {}/: {}",
                PARTS.size(), ROOT, PARTS.keySet());
    }
}
