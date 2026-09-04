package com.createmotorsport.client;

import com.createmotorsport.Config;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SuspensionRenderer extends GeoBlockRenderer<SuspensionBlockEntity> {
    private static final float SPIN_SIGN = 1.0F;
    private static final float STEER_SIGN = 1.0F;
    private static final float CAMBER_RENDER_SIGN = -1.0F; // which sign leans when inward

    private static final float DROP_HEIGHT = 0.5F;

    private static final float WHEEL_LIFT = 7.0F / 16.0F;


    private static final Vector3f LEFT_HUB = new Vector3f(8.0F / 16F, 1.0F / 16F, (8.0F + 25.0F) / 16F);
    private static final Vector3f RIGHT_HUB = new Vector3f(8.0F / 16F, 1.0F / 16F, (8.0F - 25.0F) / 16F);

    public SuspensionRenderer() {
        super(new SuspensionModel());
    }

    @Override
    public void render(SuspensionBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        ms.pushPose();
        super.render(be, partialTicks, ms, buffer, light, overlay);
        renderTires(be, partialTicks, ms, buffer, light, overlay);
        ms.popPose();
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(blockstateYaw(facing)));
    }

    private void renderTires(SuspensionBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                             int light, int overlay) {
        BlockState state = be.getBlockState();
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(blockstateYaw(be.getFacing())));
        ms.translate(-0.5, -0.5, -0.5);

        renderTire(be, WheelSide.LEFT, LEFT_HUB, state, ms, buffer, light, overlay, partialTicks);
        renderTire(be, WheelSide.RIGHT, RIGHT_HUB, state, ms, buffer, light, overlay, partialTicks);

        ms.popPose();
    }

    private void renderTire(SuspensionBlockEntity be, WheelSide side, Vector3f hub, BlockState state,
                            PoseStack ms, MultiBufferSource buffer, int light, int overlay, float partialTicks) {
        ItemStack stack = be.getTire(side);
        TireLike tire = stack.get(OffroadDataComponents.TIRE);
        if (tire == null) {
            return;
        }

        float raiseOffset = be.getLerpedRaise(side, partialTicks) * WHEEL_LIFT; // + as the suspension compresses
        float deploy = be.getLerpedDeploy(side, partialTicks);
        float dropOffset = (1.0F - deploy) * DROP_HEIGHT; // starts high, eases to 0
        float spin = be.getLerpedAngle(side, partialTicks);
        float steer = be.getLerpedSteer(side, partialTicks);
        float breakaway = (float) be.breakawayDrop(); // for tire breakaway from suspension with the lift heigh

        // flip one tire so its not inside out
        boolean flip = side == WheelSide.LEFT;

        ms.pushPose();
        ms.translate(hub.x, hub.y + raiseOffset + dropOffset - breakaway, hub.z);
        ms.mulPose(Axis.YP.rotation(steer * STEER_SIGN)); // steering yaw
        if (flip) {
            ms.mulPose(Axis.YP.rotation((float) Math.PI));
        }

        float camberDeg = (float) (be.isFrontAxle()
                ? Config.CAMBER_FRONT.getAsDouble() : Config.CAMBER_REAR.getAsDouble());
        if (camberDeg != 0.0F) {
            ms.mulPose(Axis.XP.rotation((float) Math.toRadians(camberDeg) * CAMBER_RENDER_SIGN));
        }
        ms.mulPose(Axis.ZP.rotation(spin * SPIN_SIGN * (flip ? -1.0F : 1.0F)));

        // Tire's own orientation offset (aero tires default to a 90-deg X rotation)
        Vec3 rot = tire.rotation();
        ms.mulPose(Axis.XP.rotation((float) Math.toRadians(rot.x)));
        ms.mulPose(Axis.YP.rotation((float) Math.toRadians(rot.y)));
        ms.mulPose(Axis.ZP.rotation((float) Math.toRadians(rot.z)));
        ms.translate(tire.offset().x, tire.offset().y, tire.offset().z);

        if (tire.model().isPresent()) {
            ResourceLocation model = tire.model().get();
            SuperByteBuffer wheel = CachedBuffers.partial(PartialModel.of(model), state);
            wheel.light(light).translate(-0.5F, 0.0F, -0.5F).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        } else {
            Minecraft.getInstance().getItemRenderer()
                    .renderStatic(stack, ItemDisplayContext.NONE, light, overlay, ms, buffer, be.getLevel(), 0);
        }
        ms.popPose();
    }

    private static float blockstateYaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> 90.0F;
            case WEST -> 0.0F;
            case NORTH -> 270.0F;
            default -> 180.0F; // EAST
        };
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen(SuspensionBlockEntity be) {
        return true;
    }
}
