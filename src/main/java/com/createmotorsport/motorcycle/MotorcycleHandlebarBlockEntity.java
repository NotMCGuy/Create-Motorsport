package com.createmotorsport.motorcycle;

import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MotorcycleHandlebarBlockEntity extends SteeringWheelBlockEntity implements MotorcyclePart {
    private static final java.util.Set<SteeringWheelBlockEntity> CLIENT_LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static java.util.List<SteeringWheelBlockEntity> clientLoaded() { return java.util.List.copyOf(CLIENT_LOADED); }
    @Override public void initialize() {
        super.initialize();
        if (level != null && level.isClientSide) CLIENT_LOADED.add(this);
    }
    @Override public void remove() { CLIENT_LOADED.remove(this); super.remove(); }
    @Override public void onChunkUnloaded() { CLIENT_LOADED.remove(this); super.onChunkUnloaded(); }
    private final MotorcycleAppearance appearance = new MotorcycleAppearance();
    @Override public MotorcycleAppearance appearance() { return appearance; }
    @Override protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean packet) { super.write(tag,provider,packet); appearance.write(tag); }
    @Override protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean packet) { super.read(tag,provider,packet); appearance.read(tag); }
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() { return new net.minecraft.world.phys.AABB(worldPosition).inflate(10); }
    public MotorcycleHandlebarBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public BlockEntityType<?> getType() { return MotorcycleModule.HANDLEBAR_ENTITY.get(); }
    @Override public void setDriving(Player player, boolean driving) {
        if (driving && hasUser() && !isUser(player)) return;
        super.setDriving(player, driving);
    }
}
