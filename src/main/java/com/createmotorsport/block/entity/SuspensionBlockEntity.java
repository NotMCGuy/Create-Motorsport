package com.createmotorsport.block.entity;

import com.createmotorsport.CreateMotorsport;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SuspensionBlockEntity extends KineticBlockEntity implements GeoBlockEntity {
    private static final RawAnimation AXLE_ROTATION = RawAnimation.begin().thenLoop("axle_rotation");
    private static final RawAnimation AXLE_ROTATION_REVERSE = RawAnimation.begin().thenLoop("axle_rotation_reverse");
    private static final RawAnimation LEFT_SUSPENSION = RawAnimation.begin().thenLoop("left_suspension");
    private static final RawAnimation RIGHT_SUSPENSION = RawAnimation.begin().thenLoop("right_suspension");
    private static final int SUSPENSION_MOVEMENT_TICKS = 8;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private float leftCompression;
    private float rightCompression;
    private int leftSuspensionMovementTicks;
    private int rightSuspensionMovementTicks;

    public enum WheelMountSide {
        LEFT,
        RIGHT
    }

    public enum SuspensionStiffness {
        SOFT("Soft", 0.55F),
        MEDIUM("Medium", 1.0F),
        HARD("Hard", 1.55F);

        private final String displayName;
        private final float compressionResistance;

        SuspensionStiffness(String displayName, float compressionResistance) {
            this.displayName = displayName;
            this.compressionResistance = compressionResistance;
        }

        public String getDisplayName() {
            return displayName;
        }

        public float getCompressionResistance() {
            return compressionResistance;
        }

        public SuspensionStiffness next() {
            SuspensionStiffness[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private SuspensionStiffness stiffness = SuspensionStiffness.MEDIUM;

    public SuspensionBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.SUSPENSION_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SuspensionBlockEntity suspension) {
        suspension.tick();
        suspension.tickSuspensionMovement();
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0D, 1.0D, 2.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "axle", 0, state -> {
            float animationSpeed = getWheelAnimationSpeed();
            if (Math.abs(animationSpeed) <= 0.001F) {
                state.setControllerSpeed(0.0F);
                return state.setAndContinue(AXLE_ROTATION);
            }

            state.setControllerSpeed(Math.abs(animationSpeed));
            return state.setAndContinue(animationSpeed < 0.0F ? AXLE_ROTATION_REVERSE : AXLE_ROTATION);
        }));
        controllers.add(new AnimationController<>(this, "left_suspension", 0, state -> {
            if (!isSuspensionMoving(WheelMountSide.LEFT)) {
                return PlayState.STOP;
            }

            state.setControllerSpeed(1.0F);
            return state.setAndContinue(LEFT_SUSPENSION);
        }));
        controllers.add(new AnimationController<>(this, "right_suspension", 0, state -> {
            if (!isSuspensionMoving(WheelMountSide.RIGHT)) {
                return PlayState.STOP;
            }

            state.setControllerSpeed(1.0F);
            return state.setAndContinue(RIGHT_SUSPENSION);
        }));
    }

    public Direction getWheelMountDirection(WheelMountSide side) {
        Direction.Axis axis = getBlockState().getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
        return switch (axis) {
            case X -> side == WheelMountSide.LEFT ? Direction.NORTH : Direction.SOUTH;
            case Z -> side == WheelMountSide.LEFT ? Direction.WEST : Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    public BlockPos getWheelMountPosition(WheelMountSide side) {
        return worldPosition.relative(getWheelMountDirection(side));
    }

    public boolean isWheelMountPosition(BlockPos pos) {
        return pos.equals(getWheelMountPosition(WheelMountSide.LEFT))
                || pos.equals(getWheelMountPosition(WheelMountSide.RIGHT));
    }

    public void setWheelCompression(WheelMountSide side, float compression) {
        float clampedCompression = Math.clamp(compression / stiffness.getCompressionResistance(), 0.0F, 1.0F);

        if (side == WheelMountSide.LEFT) {
            if (Math.abs(leftCompression - clampedCompression) > 0.01F) {
                leftSuspensionMovementTicks = SUSPENSION_MOVEMENT_TICKS;
            }
            leftCompression = clampedCompression;
        } else {
            if (Math.abs(rightCompression - clampedCompression) > 0.01F) {
                rightSuspensionMovementTicks = SUSPENSION_MOVEMENT_TICKS;
            }
            rightCompression = clampedCompression;
        }

        setChanged();
    }

    public float getWheelCompression(WheelMountSide side) {
        return side == WheelMountSide.LEFT ? leftCompression : rightCompression;
    }

    public SuspensionStiffness getStiffness() {
        return stiffness;
    }

    public SuspensionStiffness cycleStiffness() {
        stiffness = stiffness.next();
        setChanged();
        notifyUpdate();
        return stiffness;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("LeftCompression", leftCompression);
        tag.putFloat("RightCompression", rightCompression);
        tag.putString("Stiffness", stiffness.name());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        leftCompression = tag.getFloat("LeftCompression");
        rightCompression = tag.getFloat("RightCompression");
        if (tag.contains("Stiffness")) {
            try {
                stiffness = SuspensionStiffness.valueOf(tag.getString("Stiffness"));
            } catch (IllegalArgumentException ignored) {
                stiffness = SuspensionStiffness.MEDIUM;
            }
        }
    }

    private float getWheelAnimationSpeed() {
        return Math.clamp(getSpeed() / 48.0F, -2.5F, 2.5F);
    }

    private boolean isSuspensionMoving(WheelMountSide side) {
        return side == WheelMountSide.LEFT ? leftSuspensionMovementTicks > 0 : rightSuspensionMovementTicks > 0;
    }

    private void tickSuspensionMovement() {
        if (leftSuspensionMovementTicks > 0) {
            leftSuspensionMovementTicks--;
        }

        if (rightSuspensionMovementTicks > 0) {
            rightSuspensionMovementTicks--;
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
