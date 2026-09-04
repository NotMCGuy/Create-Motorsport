package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayDeque;

// Client-only store + renderer for skid marks

public final class SkidmarkManager {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "textures/effect/skidmark.png");
    private static final RenderType RENDER_TYPE = RenderType.textPolygonOffset(TEXTURE);

    private static final ArrayDeque<Skid> SKIDS = new ArrayDeque<>();

    private SkidmarkManager() {
    }

    private static final class Skid {
        final float[] q; // 12 corner floats: prevL, prevR, curR, curL
        final float intensity;
        final long spawnMs;
        final int light;

        Skid(float[] q, float intensity, long spawnMs, int light) {
            this.q = q;
            this.intensity = intensity;
            this.spawnMs = spawnMs;
            this.light = light;
        }
    }

    // Called from the packet handler, 13 floats, 12 corners + intensity
    public static void add(float[] quad) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int max = Config.SKIDMARK_MAX.getAsInt();
        while (SKIDS.size() >= max) {
            SKIDS.pollFirst();
        }
        float[] corners = new float[12];
        System.arraycopy(quad, 0, corners, 0, 12);
        float intensity = quad[12];

        double cx = (corners[0] + corners[3] + corners[6] + corners[9]) * 0.25;
        double cy = (corners[1] + corners[4] + corners[7] + corners[10]) * 0.25;
        double cz = (corners[2] + corners[5] + corners[8] + corners[11]) * 0.25;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(cx, cy + 0.1, cz));

        SKIDS.addLast(new Skid(corners, intensity, System.currentTimeMillis(), light));
    }

    public static void clear() {
        SKIDS.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SKIDS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        double lifeMs = Config.SKIDMARK_LIFETIME.getAsDouble() * 1000.0;
        float opacity = (float) Config.SKIDMARK_OPACITY.getAsDouble();

        // Drop the oldest ones from the head
        while (!SKIDS.isEmpty() && now - SKIDS.peekFirst().spawnMs >= lifeMs) {
            SKIDS.pollFirst();
        }
        if (SKIDS.isEmpty()) {
            return;
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RENDER_TYPE);

        for (Skid s : SKIDS) {
            float fade = 1.0f - (float) ((now - s.spawnMs) / lifeMs);
            if (fade <= 0.0f) {
                continue;
            }
            int alpha = (int) (fade * s.intensity * opacity * 255.0f);
            if (alpha <= 0) {
                continue;
            }
            float[] q = s.q;

            corner(vc, matrix, cam, q, 0, 0.0f, 0.0f, alpha, s.light); // prevL
            corner(vc, matrix, cam, q, 3, 1.0f, 0.0f, alpha, s.light); // prevR
            corner(vc, matrix, cam, q, 6, 1.0f, 1.0f, alpha, s.light); // curR
            corner(vc, matrix, cam, q, 9, 0.0f, 1.0f, alpha, s.light); // curL

            corner(vc, matrix, cam, q, 9, 0.0f, 1.0f, alpha, s.light); // curL
            corner(vc, matrix, cam, q, 6, 1.0f, 1.0f, alpha, s.light); // curR
            corner(vc, matrix, cam, q, 3, 1.0f, 0.0f, alpha, s.light); // prevR
            corner(vc, matrix, cam, q, 0, 0.0f, 0.0f, alpha, s.light); // prevL
        }

        buffers.endBatch(RENDER_TYPE);
        pose.popPose();
    }

    private static void corner(VertexConsumer vc, Matrix4f matrix, Vec3 cam, float[] q, int i,
                               float u, float v, int alpha, int light) {
        vc.addVertex(matrix, (float) (q[i] - cam.x), (float) (q[i + 1] - cam.y), (float) (q[i + 2] - cam.z))
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setLight(light);
    }
}
