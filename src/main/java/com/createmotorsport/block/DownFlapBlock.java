package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.DownFlapBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.foundation.placement.PoleHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class DownFlapBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable, BlockSubLevelLiftProvider {
   public static final MapCodec<DownFlapBlock> CODEC = simpleCodec(DownFlapBlock::new);
   public static final BooleanProperty PILLAR = BooleanProperty.create("pillar");
   public static final BooleanProperty LEFT = BooleanProperty.create("left");
   public static final BooleanProperty RIGHT = BooleanProperty.create("right");
   public static final IntegerProperty FLAP_POWER = IntegerProperty.create("flap_power", 0, 15);

    private static final int placementHelperId = PlacementHelpers.register(new DownFlapBlock.PlacementHelper());

    public DownFlapBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PILLAR, true).setValue(FLAP_POWER, 0).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    public MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DownFlapBlockEntity(pos, state);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
        if (!player.isShiftKeyDown() && player.mayBuild()) {
            if (placementHelper.matchesItem(stack))
                return placementHelper.getOffset(player, level, state, pos, hitResult).placeInWorld(level, ((BlockItem) stack.getItem()), player, hand, hitResult);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        BlockState newState = state.setValue(PILLAR, !state.getValue(PILLAR));
        level.setBlockAndUpdate(pos, newState);

        // TODO: add diff sound
        if (level.getBlockState(pos) != state) {
            IWrenchable.playRotateSound(level, pos);
        }

        return InteractionResult.SUCCESS;
    }


    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        if (level.isClientSide) {
            return;
        }

        updateConnection(level, pos, state);

        if (state.getValue(PILLAR)) {
            Direction facing = state.getValue(FACING);
            DownFlapBlockEntity be = (DownFlapBlockEntity) level.getBlockEntity(pos);
            if (be == null) { return; }
            int power = 0;

            if (state.getValue(PILLAR)) {
                power = level.getBestNeighborSignal(pos);
            }

            if (state.getValue(FLAP_POWER) != power && power > -1) {
                state = state.setValue(FLAP_POWER, power);
                level.setBlock(pos, state, 2);
                be.changeState(power);
                level.sendBlockUpdated(pos, state, state, 2);
                updateNeighborFlap(level, pos, facing.getClockWise(), power);
                updateNeighborFlap(level, pos, facing.getCounterClockWise(), power);
            }
        }

        if (!level.getBlockTicks().willTickThisTick(pos, this))
            level.scheduleTick(pos, this, 1);
    }

    public void updateNeighborFlap(Level level, BlockPos pos, Direction direction, int power) {
        Stack<BlockPos> flapStack = new Stack<>();
        BlockPos nextPos = pos.relative(direction);

        // clockwise or counter-clockwise
        while (level.getBlockState(nextPos).getBlock() instanceof DownFlapBlock) {
            if (level.getBlockState(nextPos).getValue(PILLAR)) {
                power = Math.max(level.getBlockState(nextPos).getValue(FLAP_POWER), power);
                break;
            }
            flapStack.push(nextPos);
            nextPos = nextPos.relative(direction);
        }
        while (!flapStack.empty()) {
            nextPos = flapStack.pop();
            if (level.getBlockEntity(nextPos) instanceof DownFlapBlockEntity be) {
                BlockState state = level.getBlockState(nextPos).setValue(FLAP_POWER, power);
                level.setBlock(nextPos, state, 4);
                be.changeState(power);
                level.sendBlockUpdated(nextPos, state, state, 2);
            }
        }
    }

    public void updateConnection(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos left = pos.relative(facing.getCounterClockWise());
        BlockPos right = pos.relative(facing.getClockWise());
        state = state.setValue(LEFT, !(level.getBlockState(left).getBlock() instanceof DownFlapBlock)).setValue(RIGHT, !(level.getBlockState(right).getBlock() instanceof DownFlapBlock));
        level.setBlock(pos, state, 2);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        if (state.getValue(PILLAR)) {
            return side != null;
        }
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, PILLAR, FLAP_POWER, LEFT, RIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Boolean left = !(level.getBlockState(pos.relative(facing.getCounterClockWise())).getBlock() instanceof DownFlapBlock);
        Boolean right = !(level.getBlockState(pos.relative(facing.getClockWise())).getBlock() instanceof DownFlapBlock);
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing).setValue(LEFT, left).setValue(RIGHT, right);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == CreateMotorsport.DOWN_FLAP_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, be) -> ((DownFlapBlockEntity) be).tick()
                : null;
    }


    @MethodsReturnNonnullByDefault
    private static class PlacementHelper extends PoleHelper<Direction> {
        private PlacementHelper() {
            super(state -> state.getBlock() instanceof DownFlapBlock, state -> state.getValue(FACING).getClockWise().getAxis(), FACING);
        }

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.is(CreateMotorsport.DOWN_FLAP_ITEM);
        }


        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            List<Direction> directions = IPlacementHelper.orderedByDistance(pos, ray.getLocation(), dir -> dir.getAxis() == axisFunction.apply(state));
            for (Direction dir : directions) {
                int range = AllConfigs.server().equipment.placementAssistRange.get();
                AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
                if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id()))
                    range += 4;
                int poles = attachedPoles(world, pos, dir);
                if (poles >= range)
                    continue;

                BlockPos newPos = pos.relative(dir, poles + 1);
                BlockState newState = world.getBlockState(newPos);
                Direction facing = state.getValue(property);
                // Inverted compared to #getStateForPlacement
                Boolean right = !(world.getBlockState(newPos.relative(facing.getClockWise())).getBlock() instanceof DownFlapBlock);
                Boolean left = !(world.getBlockState(newPos.relative(facing.getCounterClockWise())).getBlock() instanceof DownFlapBlock);
                int power = world.getBlockState(pos).getValue(FLAP_POWER);

                if (newState.canBeReplaced())
                    return PlacementOffset.success(newPos, bState -> bState.setValue(property, state.getValue(property)).setValue(PILLAR, false).setValue(LEFT, left).setValue(RIGHT, right).setValue(FLAP_POWER, power));

            }

            return PlacementOffset.fail();
        }
    }

    @Override
    @NotNull
    public Direction sable$getNormal(BlockState state) {
        return state.getValue(FACING);
    }


    // force of the flap at 52.5321°
    @Override
    public float sable$getParallelDragScalar() {
        return 1.0F;
    }

    @Override
    public float sable$getLiftScalar() {
        return 0.0F;
    }

    @Override
    public void sable$contributeLiftAndDrag(BlockSubLevelLiftProvider.LiftProviderContext ctx, ServerSubLevel subLevel, @NotNull Pose3d localPose, double timeStep, Vector3dc linearVelocity, Vector3dc angularVelocity, Vector3d linearImpulse, Vector3d angularImpulse, @Nullable BlockSubLevelLiftProvider.LiftProviderGroup group) {
        BlockSubLevelLiftProvider.resetVectors();
        BlockState state = ctx.state();
        float angle = (float) state.getValue(DownFlapBlock.FLAP_POWER) / 30 * (float) Math.PI;
        float dragScalar = (float) (this.sable$getParallelDragScalar() * Math.sin(angle) * (1 - Math.cos(2 * angle)));

        LIFT_NORMAL.set(ctx.dir().x(), ctx.dir().y(), ctx.dir().z());
        LIFT_POS.set((double)ctx.pos().getX() + (double)0.5F, (double) ctx.pos().getY() + (double)0.5F, (double) ctx.pos().getZ() + (double)0.5F);
        if (localPose != null) {
            localPose.transformNormal(LIFT_NORMAL);
            localPose.transformPosition(LIFT_POS);
        }

        Pose3d pose = subLevel.logicalPose();
        double pressure = DimensionPhysicsData.getAirPressure(subLevel.getLevel(), pose.transformPosition(LIFT_POS, TEMP));
        pose.transformPosition(LIFT_POS, TEMP).sub(pose.position());
        LIFT_VELO.set(linearVelocity).add(angularVelocity.cross(TEMP, TEMP));
        pose.transformNormalInverse(LIFT_VELO);
        LIFT_FORCE.zero();
        if (dragScalar > 0.0F) {
            double dragStrength = LIFT_NORMAL.dot(LIFT_VELO) * (double) dragScalar * pressure * timeStep;
            Vector3d parallelDrag = LIFT_NORMAL.mul(dragStrength, DRAG);
            LIFT_FORCE.add(parallelDrag);
            if (group != null) {
                group.totalDrag().sub(parallelDrag);
                group.dragCenter().fma(Math.abs(dragStrength), LIFT_POS);
                group.totalDragStrength += Math.abs(dragStrength);
            }
        }

        if (this.sable$getDirectionlessDragScalar() > 0.0F) {
            double dragStrength = (double)this.sable$getDirectionlessDragScalar() * pressure * timeStep;
            Vector3d directionlessDrag = LIFT_VELO.mul(dragStrength, TEMP);
            LIFT_FORCE.add(directionlessDrag);
            if (group != null) {
                group.totalDrag().sub(directionlessDrag);
                group.dragCenter().fma(directionlessDrag.length(), LIFT_POS);
                group.totalDragStrength += directionlessDrag.length();
            }
        }

        linearImpulse.sub(LIFT_FORCE);
        LIFT_POS.sub(subLevel.getMassTracker().getCenterOfMass(), TEMP);
        angularImpulse.sub(TEMP.cross(LIFT_FORCE));
        BlockSubLevelLiftProvider.resetVectors();
    }
}
