package com.createmotorsport.block.entity;

import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.createmotorsport.CreateMotorsport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class GearboxBlockEntity extends SplitShaftBlockEntity {
    private static final float[] RATIOS = {0.0F, 0.35F, 0.55F, 0.80F, 1.10F, 1.45F, 1.85F};
    private static final String[] GEAR_NAMES = {"N", "1", "2", "3", "4", "5", "6"};
    private static final String[] GEAR_BEHAVIOR = {
            "Neutral",
            "Launch gear: strong pull, low top speed",
            "Low gear: strong pull",
            "Mid gear: balanced acceleration",
            "Cruise gear: more speed, less pull",
            "High gear: high speed, weak pull",
            "Overdrive: top speed, needs momentum"
    };
    private static final float GEAR_LOAD = 48.0F;

    private int gear = 1;

    public GearboxBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.GEARBOX_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GearboxBlockEntity gearbox) {
        gearbox.tick();
    }

    @Override
    public float getRotationSpeedModifier(Direction direction) {
        if (!hasSource() || direction == getSourceFacing()) {
            return 1.0F;
        }

        return RATIOS[gear];
    }

    @Override
    public float calculateStressApplied() {
        return gear == 0 ? 0.0F : GEAR_LOAD * RATIOS[gear];
    }

    public void changeGear(Player player, boolean downshift) {
        int previousGear = gear;
        gear = downshift ? Math.max(0, gear - 1) : Math.min(RATIOS.length - 1, gear + 1);

        if (gear == previousGear) {
            return;
        }

        detachKinetics();
        attachKinetics();
        setChanged();
        notifyUpdate();
        player.displayClientMessage(Component.literal("Gear " + GEAR_NAMES[gear]
                + " (" + RATIOS[gear] + ":1) - " + GEAR_BEHAVIOR[gear]), true);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Gear", gear);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        gear = Math.clamp(tag.getInt("Gear"), 0, RATIOS.length - 1);
    }
}
