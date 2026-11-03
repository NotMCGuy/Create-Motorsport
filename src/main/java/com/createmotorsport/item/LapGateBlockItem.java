package com.createmotorsport.item;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;


public class LapGateBlockItem extends BlockItem {
    public static final double MAX_GATE_LENGTH = 60.0;

    public LapGateBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    // Stores the gate to link the next placement to, called by the block when it's clicked with this item
    public static void armLink(ItemStack stack, BlockPos pos) {
        stack.set(CreateMotorsport.GATE_FIRST_POST.get(), pos.asLong());
    }

    public static BlockPos getArmed(ItemStack stack) {
        Long packed = stack.get(CreateMotorsport.GATE_FIRST_POST.get());
        return packed == null ? null : BlockPos.of(packed);
    }

    public static void clearArmed(ItemStack stack) {
        stack.remove(CreateMotorsport.GATE_FIRST_POST.get());
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        Level level = context.getLevel();
        if (!placed || level.isClientSide) {
            return placed;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos armed = getArmed(stack);
        if (armed == null) {
            return true;
        }
        BlockPos here = context.getClickedPos();
        Player player = context.getPlayer();
        clearArmed(stack);

        if (!(level.getBlockEntity(armed) instanceof LapGateBlockEntity first)
                || !(level.getBlockEntity(here) instanceof LapGateBlockEntity placedGate)) {
            return true;
        }
        if (!armed.closerThan(here, MAX_GATE_LENGTH)) {
            if (player != null) {
                player.displayClientMessage(Component.literal(
                        "§c[Gate] Gate has been placed too far away to link"), true);
            }
            return true;
        }
        first.linkTo(placedGate);
        Vec3 mid = Vec3.atCenterOf(armed).add(Vec3.atCenterOf(here)).scale(0.5);
        level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player != null) {
            player.displayClientMessage(Component.literal(
                    "§a[Gate] Timing line created (marker " + first.getMarker() + ")"), true);
        }
        return true;
    }
}
