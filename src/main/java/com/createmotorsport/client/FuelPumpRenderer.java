package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.FuelPumpBlock;
import com.createmotorsport.block.entity.FuelPumpBlockEntity;
import com.createmotorsport.fuel.FuelCoordinates;
import com.createmotorsport.item.FuelNozzleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3d;

public class FuelPumpRenderer implements BlockEntityRenderer<FuelPumpBlockEntity> {
    private ItemStack model;
    private final Vector3d vertexPosition = new Vector3d();
    private static final int[] RING_U = {1, 0, -1, 0, 1};
    private static final int[] RING_V = {0, 1, 0, -1, 0};

    private static Vec3 local(Vec3 global, Pose3dc pose, Vec3 origin) {
        return (pose == null ? global : pose.transformPositionInverse(global)).subtract(origin);
    }

    private static Vec3 nozzle(Player player, float partial, Minecraft mc) {
        int arm = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {


            Vector3f offset = new Vector3f(arm * 0.32f, -0.27f, -0.55f);
            mc.gameRenderer.getMainCamera().rotation().transform(offset);
            return mc.gameRenderer.getMainCamera().getPosition().add(offset.x, offset.y, offset.z);
        }
        double yaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        Vec3 point = player.getPosition(partial).add(-Math.cos(yaw) * arm * 0.34,
                player.isCrouching() ? 0.85 : 1.05, -Math.sin(yaw) * arm * 0.34)
                .add(-Math.sin(yaw) * 0.22, 0, Math.cos(yaw) * 0.22);
        var sub = Sable.HELPER.getContaining(player);
        return FuelCoordinates.transform(point, sub instanceof ClientSubLevel client ? client.renderPose(partial) : null);
    }

    @Override public void render(FuelPumpBlockEntity pump, float partial, PoseStack matrices,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (pump.isRemoved() || pump.getLevel() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = new ItemStack(CreateMotorsport.FUEL_NOZZLE.get());
        Vec3 origin = Vec3.atLowerCornerOf(pump.getBlockPos());
        var sub = Sable.HELPER.getContaining(pump);
        Pose3dc pose = sub instanceof ClientSubLevel client ? client.renderPose(partial) : null;
        var facing = pump.getBlockState().getValue(FuelPumpBlock.FACING);
        Vec3 start = FuelCoordinates.transform(origin.add(FuelCoordinates.attachment(facing)), pose);
        Vec3 end;
        Quaternionf rotation = new Quaternionf();
        if (pump.session.held()) {
            Player player = pump.getLevel().getPlayerByUUID(pump.session.owner());
            if (player == null || !player.isAlive() || !pump.session.matches(player.getUUID(), FuelNozzleItem.sessionId(player.getMainHandItem()))) return;
            end = nozzle(player, partial, mc);
            if (!FuelCoordinates.inRange(start, end, pump.hoseRange() + 2)) return;
            if (pose != null) rotation.set(pose.orientation()).conjugate();
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) rotation.mul(mc.gameRenderer.getMainCamera().rotation());
            else rotation.rotateY((float) Math.toRadians(180 - player.getViewYRot(partial)));
        } else {
            end = FuelCoordinates.transform(origin.add(FuelCoordinates.rotate(new Vec3(3.5 / 16, 17.0 / 16, 11.0 / 16), facing)), pose);
            rotation.rotateY((float) Math.toRadians(switch (facing) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; }));
            rotation.rotateY((float) Math.toRadians(270));
        }
        Vec3 nozzleLocal = local(end, pose, origin);
        matrices.pushPose();
        matrices.translate(nozzleLocal.x, nozzleLocal.y, nozzleLocal.z);
        matrices.mulPose(rotation);
        matrices.scale(0.8f, 0.8f, 0.8f);
        mc.getItemRenderer().renderStatic(model, ItemDisplayContext.FIXED, light, overlay, matrices, buffers, pump.getLevel(), 0);
        matrices.popPose();

        double length = start.distanceTo(end);
        int segments = Math.clamp((int) Math.ceil(length * 5), 12, 64);
        double sag = pump.session.held() ? Math.min(1.8, 0.15 + length * 0.15) : 0.55;
        VertexConsumer vertices = buffers.getBuffer(RenderType.textBackground());
        Vec3 previous = start;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 next = start.lerp(end, t).add(0, -4 * sag * t * (1 - t), 0);
            tube(vertices, matrices, previous, next, pose, origin, light);
            previous = next;
        }
    }

    private void tube(VertexConsumer vertices, PoseStack matrices, Vec3 a, Vec3 b, Pose3dc pose, Vec3 origin, int light) {
        Vec3 axis = b.subtract(a).normalize();
        Vec3 u = axis.cross(Math.abs(axis.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize().scale(0.025);
        Vec3 v = axis.cross(u);
        for (int side = 0; side < 4; side++) {
            vertex(vertices, matrices, a, u, v, side, pose, origin, light);
            vertex(vertices, matrices, b, u, v, side, pose, origin, light);
            vertex(vertices, matrices, b, u, v, side + 1, pose, origin, light);
            vertex(vertices, matrices, a, u, v, side + 1, pose, origin, light);
        }
    }
    private void vertex(VertexConsumer vertices, PoseStack matrices, Vec3 point, Vec3 u, Vec3 v, int side,
                        Pose3dc pose, Vec3 origin, int light) {
        vertexPosition.set(point.x + u.x * RING_U[side] + v.x * RING_V[side],
                point.y + u.y * RING_U[side] + v.y * RING_V[side],
                point.z + u.z * RING_U[side] + v.z * RING_V[side]);
        if (pose != null) pose.transformPositionInverse(vertexPosition);
        vertices.addVertex(matrices.last().pose(), (float) (vertexPosition.x - origin.x),
                (float) (vertexPosition.y - origin.y), (float) (vertexPosition.z - origin.z))
                .setColor(0xFF151515).setLight(light);
    }
    @Override public boolean shouldRenderOffScreen(FuelPumpBlockEntity pump) { return true; }
    @Override public int getViewDistance() { return 96; }
}
