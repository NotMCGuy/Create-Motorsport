package com.createmotorsport.motorcycle;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.List;

public class MotorcycleMountBlockEntity extends SmartBlockEntity implements MotorcyclePart {
    private final MotorcycleAppearance appearance=new MotorcycleAppearance();
    public MotorcycleMountBlockEntity(BlockPos pos,BlockState state){super(MotorcycleModule.MOUNT_ENTITY.get(),pos,state);}
    @Override public MotorcycleAppearance appearance(){return appearance;}
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){}
    @Override protected void write(CompoundTag tag,HolderLookup.Provider provider,boolean packet){super.write(tag,provider,packet);appearance.write(tag);}
    @Override protected void read(CompoundTag tag,HolderLookup.Provider provider,boolean packet){super.read(tag,provider,packet);appearance.read(tag);}
    @Override protected AABB createRenderBoundingBox(){return new AABB(worldPosition).inflate(10);}
}
