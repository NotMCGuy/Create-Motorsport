package com.createmotorsport.client.render;

import com.createmotorsport.physics.spec.SuspensionArt;
import net.minecraft.client.Minecraft;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class SuspensionModel extends GeoModel<SuspensionBlockEntity> {

    public static final double RENDER_REST_RAISE = 0.8;
    public static final double HUB_TRAVEL = 7.0 / 16.0;

    public static double hubRaise(SuspensionBlockEntity be, WheelSide side, float partialTicks) {
        double droop = be.getLerpedSpringLength(side, partialTicks) - SuspensionBlockEntity.REST_LENGTH;
        return RENDER_REST_RAISE - droop / HUB_TRAVEL;
    }

    public static float raise(SuspensionBlockEntity be, WheelSide side, float partialTicks) {
        return (float) Mth.clamp(hubRaise(be, side, partialTicks), 0.0, 1.0);
    }


    private record BoneDelta(String bone, double rotX, double rotY, double rotZ, double posX, double posY, double posZ) {}

    private static final BoneDelta[] LEFT = {
            new BoneDelta("LeftUpperWishbone", 30.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("LeftLowerWishbone", 27.5, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("SteeringShaftLeft", 30.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("group26", 0.0, 0.0, 0.0, -1.2, 0.0, 0.0),
            new BoneDelta("group27", 0.0, 35.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("group28", 27.5, 0.0, 0.0, 0.0, 7.0, 0.0),
            new BoneDelta("LeftAttachment", 0.0, 0.0, 0.0, 0.0, 7.0, 0.0),
            new BoneDelta("LeftShaftRotation", 35.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("bone3", 0.0, 35.0, 0.0, 0.0, 0.0, 0.0),
    };

    private static final BoneDelta[] RIGHT = {
            new BoneDelta("RightUpperWishbone", -32.5, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("RightLowerWishbone", -27.5, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("RightSteer", -30.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("group17", 0.0, 0.0, 0.0, -1.2, 0.0, 0.0),
            new BoneDelta("group10", 0.0, -35.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("group13", -27.5, 0.0, 0.0, 0.0, 7.0, 0.0),
            new BoneDelta("RightAttachment", 0.0, 0.0, 0.0, 0.0, 7.0, 0.0),
            new BoneDelta("ShaftRight", -37.5, 0.0, 0.0, 0.0, 0.0, 0.0),
            new BoneDelta("bone4", 0.0, -35.0, 0.0, 0.0, 0.0, 0.0),
    };

    private static final ResourceLocation DEFAULT_MODEL = ResourceLocation.parse(SuspensionArt.DEFAULT.model());
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.parse(SuspensionArt.DEFAULT.texture());
    private static final ResourceLocation DEFAULT_ANIMATION = ResourceLocation.parse(SuspensionArt.DEFAULT.animation());

    private static final Map<ResourceLocation, Boolean> PRESENT = new HashMap<>();

    private static ResourceLocation art(SuspensionBlockEntity animatable, Function<SuspensionArt, String> pick,
                                        ResourceLocation fallback) {
        if (animatable == null) {
            return fallback;
        }
        ResourceLocation location = ResourceLocation.tryParse(pick.apply(animatable.getSetup().art()));
        if (location == null || location.equals(fallback)) {
            return fallback;
        }
        boolean present = PRESENT.computeIfAbsent(location,
                l -> Minecraft.getInstance().getResourceManager().getResource(l).isPresent());
        return present ? location : fallback;
    }

    @Override
    public ResourceLocation getModelResource(SuspensionBlockEntity animatable) {
        return art(animatable, SuspensionArt::model, DEFAULT_MODEL);
    }

    @Override
    public ResourceLocation getTextureResource(SuspensionBlockEntity animatable) {
        return art(animatable, SuspensionArt::texture, DEFAULT_TEXTURE);
    }

    @Override
    public ResourceLocation getAnimationResource(SuspensionBlockEntity animatable) {
        return art(animatable, SuspensionArt::animation, DEFAULT_ANIMATION);
    }

    private static final float SPIN_SIGN = 1.0F;


    private static final float STEER_RACK_PX_PER_RAD = (float) (3.0 / Math.toRadians(30.0));


    @Override
    public void setCustomAnimations(SuspensionBlockEntity animatable, long instanceId, AnimationState<SuspensionBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        float partialTick = animationState.getPartialTick();

        applyPose(LEFT, raise(animatable, WheelSide.LEFT, partialTick));
        applyPose(RIGHT, raise(animatable, WheelSide.RIGHT, partialTick));

        applySpin(animatable.getLerpedAngle(WheelSide.LEFT, partialTick),
                animatable.getLerpedAngle(WheelSide.RIGHT, partialTick));

        applySteer(animatable, partialTick);
    }

    private void applySpin(float angleLeft, float angleRight) {
        spinZ("LeftWheel", angleLeft);
        spinZ("bone", angleLeft);
        spinZ("bone5", angleLeft);
        spinZ("RightWheel", angleRight);
        spinZ("bone2", angleRight);
        spinZ("ShaftRightRotation", angleRight);
        spinZ("MiddleShaft", 0.5F * (angleLeft + angleRight));
        GeoBone prop = getAnimationProcessor().getBone("MainShaft");
        if (prop != null) {
            prop.setRotX(SPIN_SIGN * 0.5F * (angleLeft + angleRight));
        }
    }

    private void spinZ(String bone, float angle) {
        GeoBone b = getAnimationProcessor().getBone(bone);
        if (b != null) {
            b.setRotZ(SPIN_SIGN * angle);
        }
    }


    private void applySteer(SuspensionBlockEntity animatable, float partialTick) {
        yawUpright("LeftAttachment", animatable.getLerpedSteer(WheelSide.LEFT, partialTick));
        yawUpright("RightAttachment", animatable.getLerpedSteer(WheelSide.RIGHT, partialTick));
        float rack = STEER_RACK_PX_PER_RAD * animatable.getLerpedSteer(partialTick);
        slideRack("RightSteer", rack);
        slideRack("MiddleSteer", rack);
        slideRack("SteeringShaftLeft", rack);
    }

    private void yawUpright(String bone, float steer) {
        GeoBone b = getAnimationProcessor().getBone(bone);
        if (b != null) {
            b.setRotY(b.getInitialSnapshot().getRotY() + steer);
        }
    }

    private void slideRack(String bone, float rackPx) {
        GeoBone b = getAnimationProcessor().getBone(bone);
        if (b != null) {
            b.setPosZ(rackPx);
        }
    }


    private void applyPose(BoneDelta[] deltas, float raise) {
        for (BoneDelta d : deltas) {
            GeoBone bone = getAnimationProcessor().getBone(d.bone());
            if (bone == null) {
                continue;
            }
            var rest = bone.getInitialSnapshot();
            if (d.rotX() != 0.0 || d.rotY() != 0.0 || d.rotZ() != 0.0) {
                bone.setRotX(rest.getRotX() + (float) Math.toRadians(-d.rotX() * raise));
                bone.setRotY(rest.getRotY() + (float) Math.toRadians(-d.rotY() * raise));
                bone.setRotZ(rest.getRotZ() + (float) Math.toRadians(d.rotZ() * raise));
            }
            if (d.posX() != 0.0 || d.posY() != 0.0 || d.posZ() != 0.0) {
                bone.setPosX((float) (d.posX() * raise));
                bone.setPosY((float) (d.posY() * raise));
                bone.setPosZ((float) (d.posZ() * raise));
            }
        }
    }
}
