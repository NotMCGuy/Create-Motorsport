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
    private static final float[] RATIOS = {0.0F, 0.50F, 0.75F, 1.00F, 1.50F, 2.00F};
    private static final String[] GEAR_NAMES = {"N", "1", "2", "3", "4", "5"};

    private int gear = 3;

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
        player.displayClientMessage(Component.literal("Gear " + GEAR_NAMES[gear] + " (" + RATIOS[gear] + ":1)"), true);
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
