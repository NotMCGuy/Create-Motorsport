package com.createmotorsport.client;

import com.createmotorsport.block.entity.DownFlapBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class DownFlapRenderer extends SafeBlockEntityRenderer<DownFlapBlockEntity> {
    public DownFlapRenderer(final BlockEntityRendererProvider.Context context) {

    }
    public static float[] getRotatedContext(float x, float z, Direction facing) {
        switch (facing) {
            case EAST:
                return new float[]{-z, x};
            case SOUTH:
                return new float[]{-x, -z};
            case WEST:
                return new float[]{z, -x};
            case NORTH:
            default:
                return new float[]{x, z};
        }
    }

    public static float getFacingAngleDegrees(Direction facing) {
        switch (facing) {
            case EAST:
                return -90f;
            case SOUTH:
                return 180f;
            case WEST:
                return -270f;
            case NORTH:
            default:
                return 0f;
        }
    }

    @Override
    protected void renderSafe(DownFlapBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        final VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
        final BlockState blockState = be.getBlockState();
        final Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);


        float state = be.clientState.getValue(partialTicks);
        float angle = (float) ((state / 15) * 90 / 180 * Math.PI);

        // downflap wing
        SuperByteBuffer wing = CachedBuffers.partialFacing(MotorsportPartialModels.DOWNFLAP_WING, blockState, facing);
        float[] wingOffset = getRotatedContext(0f, 6.992f, facing);
        wing.translate(wingOffset[0] / 16f, 8.5446f / 16f, wingOffset[1] / 16f);
        float wingFacingAngle = getFacingAngleDegrees(facing);

        wing
                .translate(0.5, 0, 0.5)
                .rotateY(AngleHelper.rad(wingFacingAngle))
                .rotateX(-angle + (float) Math.PI)
                .rotateY(AngleHelper.rad(-wingFacingAngle))
                .translate(-0.5, 0, -0.5);
        wing
                .light(light)
                .overlay(overlay)
                .renderInto(ms, vb);

    }
}
