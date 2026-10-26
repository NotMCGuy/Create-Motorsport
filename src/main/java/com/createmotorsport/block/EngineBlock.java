package com.createmotorsport.block;

import net.minecraft.server.level.ServerPlayer;
import com.createmotorsport.menu.PartMenus;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class EngineBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable {
    public static final MapCodec<EngineBlock> CODEC = simpleCodec(EngineBlock::new);

    public static final BooleanProperty HAS_EXHAUST = BooleanProperty.create("has_exhaust");
    public static final BooleanProperty HAS_INTAKE = BooleanProperty.create("has_intake");

    public EngineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH).setValue(HAS_EXHAUST, false).setValue(HAS_INTAKE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_EXHAUST, HAS_INTAKE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return openEngineMenu(level, pos, player) ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(CreateMotorsport.EXHAUST_MANIFOLD.get()) || stack.is(CreateMotorsport.AIR_INTAKE.get())) {
            if (!(level.getBlockEntity(pos) instanceof EngineBlockEntity engine)) return ItemInteractionResult.FAIL;
            int slot = stack.is(CreateMotorsport.EXHAUST_MANIFOLD.get()) ? EngineBlockEntity.SLOT_EXHAUST : EngineBlockEntity.SLOT_INTAKE;
            if (!engine.getInventory().getItem(slot).isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                engine.getInventory().setItem(slot, stack.copyWithCount(1));
                if (!player.isCreative()) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof WrenchItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return openEngineMenu(level, pos, player) ? ItemInteractionResult.CONSUME : ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return cycle(context, false);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return cycle(context, true);
    }

    private static InteractionResult cycle(UseOnContext context, boolean gearbox) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof EngineBlockEntity engine)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            String message = gearbox
                    ? "Gearbox: " + engine.cycleGearbox().location()
                    : "Engine: " + engine.cycleEngine().location() + "  (sneak-click for gearbox)";
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal(message), true);
            }
            IWrenchable.playRotateSound(level, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean openEngineMenu(Level level, BlockPos pos, Player player) {
        return !level.isClientSide && player instanceof ServerPlayer sp && PartMenus.openPartScreen(sp, pos);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == CreateMotorsport.ENGINE_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, blockEntity) -> ((EngineBlockEntity) blockEntity).tick()
                : null;
    }

    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state,
                                                 net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        return ConfiguredDrops.attach(super.getDrops(state, builder), builder, this, EngineBlockEntity.CONFIGURATION_KEYS);
    }
}
