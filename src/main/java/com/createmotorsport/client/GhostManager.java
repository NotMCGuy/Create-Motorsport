package com.createmotorsport.client;

import com.createmotorsport.network.GhostSyncPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;


// Client side ghost playback holds the best laps samples and moves a marker along them, loops each lap while 'playing' is true
//
public final class GhostManager {
    // Not a car shaped ghost, but a bright blue box that is easy to see and draw
    private static final double BOX_HALF_WIDTH = 1.6;
    private static final double BOX_HEIGHT = 2.4;

    private static float[] samples = new float[0];
    private static long lapTicks;
    private static int sampleTicks = 2;
    private static long startGameTime;
    private static String name = "";
    private static boolean playing; // ghost is kept stored between races, so it only gets drawns while this is true

    private GhostManager() {
    }

    public static void accept(GhostSyncPacket packet) {
        samples = packet.samples();
        lapTicks = packet.lapTicks();
        sampleTicks = Math.max(1, packet.sampleTicks());
        startGameTime = packet.startGameTime();
        name = packet.name();
        playing = packet.playing();
    }

    public static void clear() {
        samples = new float[0];
        lapTicks = 0;
        playing = false;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!playing || mc.level == null || samples.length < 6 || lapTicks <= 0) {
            return;
        }

        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double elapsed = (mc.level.getGameTime() + partial) - startGameTime;
        if (elapsed < 0.0) {
            return;
        }
        double intoLap = elapsed % lapTicks;
        double exactIndex = intoLap / sampleTicks;

        int count = samples.length / 3;
        int i0 = (int) Math.floor(exactIndex);
        if (i0 >= count - 1) {
            i0 = count - 2;
        }
        double t = exactIndex - i0;
        Vec3 pos = lerpSample(i0, i0 + 1, t);

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        // Pulsing animation will make it look more interesting and like a ghost
        float pulse = 0.75F + 0.25F * (float) Math.sin((mc.level.getGameTime() + partial) * 0.25);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        AABB box = new AABB(-BOX_HALF_WIDTH, 0.0, -BOX_HALF_WIDTH,
                BOX_HALF_WIDTH, BOX_HEIGHT, BOX_HALF_WIDTH);

        VertexConsumer filled = buffers.getBuffer(RenderType.debugFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(ps, filled,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                0.3F, 0.95F, 1.0F, 0.35F * pulse);
        buffers.endBatch(RenderType.debugFilledBox());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(ps, lines, box, 0.5F, 1.0F, 1.0F, pulse);
        buffers.endBatch(RenderType.lines());

        ps.popPose();
    }

    private static Vec3 lerpSample(int a, int b, double t) {
        int count = samples.length / 3;
        a = Math.max(0, Math.min(a, count - 1));
        b = Math.max(0, Math.min(b, count - 1));
        double ax = samples[a * 3], ay = samples[a * 3 + 1], az = samples[a * 3 + 2];
        double bx = samples[b * 3], by = samples[b * 3 + 1], bz = samples[b * 3 + 2];
        return new Vec3(ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t);
    }

    public static String ghostName() {
        return name;
    }
}
