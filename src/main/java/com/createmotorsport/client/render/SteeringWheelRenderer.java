package com.createmotorsport.client.render;

import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.client.widget.Theme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

// Steering wheel rendering taken from universal keyboard mod
public class SteeringWheelRenderer implements BlockEntityRenderer<SteeringWheelBlockEntity> {
    // Wheel rotation pivot + axis (pixels / 16), authored in the block's default (facing=north) frame
    private static final Vector3f PIVOT = new Vector3f(0.0F / 16F, 11.96447F / 16F, 7.96447F / 16F);
    private static final Vector3f WHEEL_AXIS = new Vector3f(0.96593F, -0.25882F, 0F).normalize();

    // Tilt up to driver so you can read the screen
    private static final float TILT_DEG = 5.0F;

    // Screen face placement (pixels)
    private static final float FACE_X = 0.0F;
    private static final float CENTER_Y = 15.5F;
    private static final float CENTER_Z = 8.0F;
    private static final float FACE_YAW = -90.0F;
    private static final float SCREEN_W = 11.5F / 16F;
    private static final float SCREEN_H = 4.6F / 16F;
    private static final float EPS = 0.02F;
    private static final float MIN_SCALE = 0.0065F;
    private static final float MAX_SCALE = 0.0100F;
    private static final float COL_FILL = 0.90F;
    private static final int SCREEN_LIGHT = 0xF000F0;
    private static final int LINE_GAP = 2;


    public SteeringWheelRenderer() {
    }

    @Override
    public void render(SteeringWheelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Matrix4f orient = BlockModelRotation.by(0, blockstateYaw(facing)).getRotation().getMatrix();

        // Spinning rim
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(orient);
        ms.translate(-0.5, -0.5, -0.5);

        ms.translate(PIVOT.x, PIVOT.y, PIVOT.z);
        ms.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(-TILT_DEG), 0F, 0F, 1F));
        ms.translate(-PIVOT.x, -PIVOT.y, -PIVOT.z);

        float angle = be.getLerpedWheelAngle(partialTicks);
        if (angle != 0.0F) {
            ms.translate(PIVOT.x, PIVOT.y, PIVOT.z);
            ms.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(angle),
                    WHEEL_AXIS.x, WHEEL_AXIS.y, WHEEL_AXIS.z));
            ms.translate(-PIVOT.x, -PIVOT.y, -PIVOT.z);
        }

        SuperByteBuffer wheel = CachedBuffers.partial(MotorsportPartialModels.DASHBOARD_WHEEL, state);
        wheel.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();

        // Telemetry screen on the dashboard face
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(orient);
        ms.translate(-0.5, -0.5, -0.5);
        ms.translate((FACE_X - EPS) / 16F, CENTER_Y / 16F, CENTER_Z / 16F);
        ms.mulPose(Axis.YP.rotationDegrees(FACE_YAW));
        drawScreen(be, ms);
        ms.popPose();
    }

    private record Line(String text, int color) {}

    private void drawScreen(SteeringWheelBlockEntity be, PoseStack ms) {
        String gear = switch (be.dashboard().gearCode()) {
            case 0 -> "R";
            case 1 -> "N";
            default -> String.valueOf(be.dashboard().gearCode() - 1);
        };
        Line speed = new Line(be.dashboard().speedKmh() + " km/h", Theme.DASH_SPEED);
        Line gearLine = new Line("Gear " + gear, Theme.DASH_GEAR);
        Line rpm = new Line(be.dashboard().rpm() + " rpm", Theme.DASH_RPM);
        Line brake = be.dashboard().braking() ? new Line("BRAKE", Theme.DASH_BRAKE) : new Line("--", Theme.DASH_OFF);
        Line modeLine = be.dashboard().boosting()
                ? new Line("BOOST", Theme.DASH_BOOST)
                : new Line("MODE " + be.dashboard().powerMode(), Theme.DASH_MODE);
        Line tcLine = be.isTractionControlOn() ? new Line("TC ON", Theme.DASH_TC) : new Line("TC off", Theme.DASH_OFF);

        // Three columns, each top-to-bottom: speed/rpm ; gear/brake ; mode-or-boost/traction-control
        List<List<Line>> columns = List.of(
                List.of(speed, rpm), List.of(gearLine, brake), List.of(modeLine, tcLine));

        Font font = Minecraft.getInstance().font;
        int lineH = font.lineHeight + LINE_GAP;
        int nCols = columns.size();
        int maxRows = 1;
        int widest = 1;
        for (List<Line> col : columns) {
            maxRows = Math.max(maxRows, col.size());
            for (Line l : col) {
                widest = Math.max(widest, font.width(l.text()));
            }
        }
        float fitW = (SCREEN_W * COL_FILL / nCols) / widest;
        float fitH = SCREEN_H / (maxRows * (float) lineH);
        float scale = Math.max(MIN_SCALE, Math.min(Math.min(fitW, fitH), MAX_SCALE));

        ms.pushPose();
        ms.scale(scale, -scale, scale);
        Matrix4f matrix = ms.last().pose();
        MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource();

        float halfW = (SCREEN_W / scale) / 2F;
        float halfH = (SCREEN_H / scale) / 2F;
        VertexConsumer bg = src.getBuffer(RenderType.textBackground());
        bg.addVertex(matrix, -halfW, -halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, -halfW, halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, halfW, halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, halfW, -halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, -halfW, -halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, halfW, -halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, halfW, halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);
        bg.addVertex(matrix, -halfW, halfH, 0).setColor(Theme.DASH_BG).setLight(SCREEN_LIGHT);

        float totalWFont = SCREEN_W / scale;
        float colWFont = totalWFont / nCols;
        for (int c = 0; c < nCols; c++) {
            List<Line> col = columns.get(c);
            float colCenterX = -totalWFont / 2F + colWFont * (c + 0.5F);
            float startY = -(col.size() * lineH) / 2F;
            for (int i = 0; i < col.size(); i++) {
                Line l = col.get(i);
                float lx = colCenterX - font.width(l.text()) / 2F;
                float ly = startY + i * lineH;
                font.drawInBatch(l.text(), lx, ly, l.color(), false,
                        matrix, src, Font.DisplayMode.POLYGON_OFFSET, 0, SCREEN_LIGHT);
            }
        }
        src.endBatch();
        ms.popPose();
    }

    private static int blockstateYaw(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(SteeringWheelBlockEntity be) {
        return true;
    }
}
