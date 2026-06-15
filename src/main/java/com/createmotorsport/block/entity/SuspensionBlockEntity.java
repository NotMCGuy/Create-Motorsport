package com.createmotorsport.block.entity;

import com.createmotorsport.CreateMotorsport;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SuspensionBlockEntity extends KineticBlockEntity implements GeoBlockEntity {
    private static final RawAnimation AXLE_ROTATION = RawAnimation.begin().thenLoop("axle_rotation");
    private static final RawAnimation LEFT_SUSPENSION = RawAnimation.begin().thenLoop("left_suspension");
    private static final RawAnimation RIGHT_SUSPENSION = RawAnimation.begin().thenLoop("right_suspension");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public SuspensionBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SuspensionBlockEntity suspension) {
        suspension.tick();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "axle", 0, state -> state.setAndContinue(AXLE_ROTATION)));
        controllers.add(new AnimationController<>(this, "left_suspension", 0, state -> state.setAndContinue(LEFT_SUSPENSION)));
        controllers.add(new AnimationController<>(this, "right_suspension", 0, state -> state.setAndContinue(RIGHT_SUSPENSION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
