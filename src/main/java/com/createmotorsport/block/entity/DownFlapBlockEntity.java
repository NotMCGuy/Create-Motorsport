package com.createmotorsport.block.entity;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.DownFlapBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;


public class DownFlapBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, BlockEntitySubLevelActor {
    // max state limit
    public ScrollValueBehaviour targetState;
    int state = 0;
    public LerpedFloat clientState;


    public DownFlapBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.DOWN_FLAP_BLOCK_ENTITY.get(), pos, state);
        clientState = LerpedFloat.linear();
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Flap State", state);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        state = tag.getInt("Flap State");
        clientState.chase(state, 1f, LerpedFloat.Chaser.EXP);
        super.read(tag, registries, clientPacket);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        targetState = new ScrollValueBehaviour(CreateLang.translateDirect("downflap.state.limit"),
                this, new ControllerValueBoxTransform());
        targetState.between(0, 15);
        targetState.value = 15;

        behaviours.add(targetState);
    }

    public void changeState(int n) {
        state = n;
        sendData();
    }

    public int getLimit() {
        return targetState.value;
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            clientState.tickChaser();
        }
    }

    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("tooltip.flapState", this.state).forGoggles(tooltip);
        return true;
    }

    private class ControllerValueBoxTransform extends ValueBoxTransform.Sided {

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 6, 15.5f);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            // also check if pillar
            if (!state.getValue(DownFlapBlock.PILLAR) || direction.getAxis()
                    .isVertical())
                return false;
            return state.getValue(DownFlapBlock.FACING).getAxis() != direction.getClockWise().getAxis();
        }

        @Override
        public float getScale() {
            return 0.5f;
        }

    }

}
