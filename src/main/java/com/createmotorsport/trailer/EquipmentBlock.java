package com.createmotorsport.trailer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EquipmentBlock extends HorizontalDirectionalBlock implements EntityBlock, com.simibubi.create.content.equipment.wrench.IWrenchable {
    public static final MapCodec<EquipmentBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(CouplingRules.Kind::valueOf, CouplingRules.Kind::name).fieldOf("kind").forGetter(b -> b.kind),
            Codec.DOUBLE.optionalFieldOf("horizontal_scale", 1.0).forGetter(b -> b.horizontalScale),
            propertiesCodec()).apply(instance, EquipmentBlock::new));
    public final CouplingRules.Kind kind;
    public final double horizontalScale;
    private final VoxelShape[] shapes = new VoxelShape[4];
    public EquipmentBlock(CouplingRules.Kind kind, BlockBehaviour.Properties properties) {
        this(kind, 1.0, properties);
    }
    public EquipmentBlock(CouplingRules.Kind kind, double horizontalScale, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        this.horizontalScale = horizontalScale;
        double widthScale=kind==CouplingRules.Kind.KINGPIN?1:horizontalScale;
        VoxelShape[] scaled = {Shapes.empty()};
        baseShape().forAllBoxes((x1,y1,z1,x2,y2,z2) -> scaled[0] = Shapes.or(scaled[0], Shapes.box(
                x1*widthScale,y1,z1*depthScale(),
                x2*widthScale,y2,z2*depthScale())));
        VoxelShape shape = scaled[0];
        for(int rotation=0;rotation<4;rotation++) {
            shapes[rotation]=shape;
            VoxelShape[] next={Shapes.empty()};
            shape.forAllBoxes((x1,y1,z1,x2,y2,z2)->next[0]=Shapes.or(next[0],Shapes.box(1-z2,y1,x1,1-z1,y2,x2)));
            shape=next[0];
        }
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
    public double depthScale() { return kind == CouplingRules.Kind.KINGPIN ? 1.0 : horizontalScale; }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, placementFacing(kind,ctx.getHorizontalDirection(),ctx.getClickedFace()));
    }
    public static Direction placementFacing(CouplingRules.Kind kind,Direction looking,Direction face) {
        if(kind==CouplingRules.Kind.TOW_BALL)return face.getAxis().isHorizontal()?face.getOpposite():looking;
        if(kind==CouplingRules.Kind.COUPLING_HEAD)return face.getAxis().isHorizontal()?face:looking.getOpposite();
        return looking.getOpposite();
    }
    @Override public InteractionResult onWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext ctx) {
        var level=ctx.getLevel();
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(ctx.getClickedPos()) instanceof EquipmentBlockEntity be) || !be.canUse(ctx.getPlayer()))return InteractionResult.FAIL;
        if(be.connected() || be.pending()) {be.feedback(ctx.getPlayer(),CouplingRules.Status.BUSY);return InteractionResult.FAIL;}
        level.setBlockAndUpdate(ctx.getClickedPos(),state.setValue(FACING,state.getValue(FACING).getClockWise()));
        com.simibubi.create.content.equipment.wrench.IWrenchable.playRotateSound(level,ctx.getClickedPos());
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult onSneakWrenched(BlockState state,net.minecraft.world.item.context.UseOnContext ctx) {
        return onWrenched(state,ctx);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof EquipmentBlockEntity be) be.setOwner(player.getUUID());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EquipmentBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == TrailerRegistry.EQUIPMENT_ENTITY.get() ? (l, p, s, be) -> ((EquipmentBlockEntity) be).tick() : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EquipmentBlockEntity be && be.canUse(player)) {
            if (kind == CouplingRules.Kind.CONTROL_PANEL) {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TrailerMenu(id, inv, pos),
                        net.minecraft.network.chat.Component.translatable("block.createmotorsport.trailer_control_panel")), pos);
            } else be.operate(player, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof EquipmentBlockEntity be) be.destroyEquipment();
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof EquipmentBlockEntity be ? be.comparator() : 0;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[switch(state.getValue(FACING)){case EAST->1;case SOUTH->2;case WEST->3;default->0;}];
    }
    private VoxelShape baseShape() {
        return switch (kind) {
            case FIFTH_WHEEL -> Shapes.or(box(1, 0, 1, 5, 10, 15), box(11, 0, 1, 15, 10, 15), box(5, 0, 1, 11, 10, 4));
            case KINGPIN -> Shapes.or(box(2*horizontalScale,12,2,14*horizontalScale,16,14),
                    box(8*horizontalScale-2,3,6,8*horizontalScale+2,12,10),
                    box(8*horizontalScale-2.5,1,5.5,8*horizontalScale+2.5,3,10.5));
            case TOW_BALL -> Shapes.or(box(5,0,2,11,4,12),box(6,3,11,10,8,15));
            case COUPLING_HEAD -> box(4,11,6,12,16,16);
            case LANDING_LEGS -> box(-4, 2, 5, 20, 14, 11);
            case CONTROL_PANEL -> box(2, 0, 2, 14, 12, 14);
        };
    }
}
