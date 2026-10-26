package com.createmotorsport.motorcycle;

import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class MotorcycleSeatEntity extends SeatEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DESIGN = net.minecraft.network.syncher.SynchedEntityData.defineId(MotorcycleSeatEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private String cachedJson = "{}";
    private MotorcycleDefinition definition = MotorcycleDefinition.DEFAULT;
    public void setDefinition(MotorcycleDefinition value) { entityData.set(DESIGN,value.json); definition=value; cachedJson=value.json; }
    public MotorcycleDefinition definition() {
        String json=entityData.get(DESIGN);if(!json.equals(cachedJson)){definition=MotorcycleDefinition.safe(json);cachedJson=json;}return definition;
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(DESIGN,"{}"); }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putString("BikeDesign",entityData.get(DESIGN)); }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { super.readAdditionalSaveData(tag); setDefinition(MotorcycleDefinition.safe(tag.contains("BikeDesign")?tag.getString("BikeDesign"):"{}")); }
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        var root=definition().socket("seat_root").position();
        var offset=new org.joml.Vector3d(root.x,root.y,root.z).rotateY(Math.toRadians(180-getYRot()));
        move.accept(passenger,getX()+offset.x,getY()+offset.y-.75,getZ()+offset.z);
    }
    public MotorcycleSeatEntity(EntityType<?> type, Level level) { super(type, level); noPhysics = true; }

    @Override public void tick() {
        super.tick();
        if (isRemoved() || level().isClientSide) return;
        var state = level().getBlockState(blockPosition());
        if (!(state.getBlock() instanceof MotorcycleSeatBlock)) { discard(); return; }
        setYRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot());
        if (getFirstPassenger() instanceof Player player) {
            var current = SteeringWheelBlockEntity.findDrivenBy(player);
            if (current != null) return;
            MotorcycleHandlebarBlockEntity nearest = null;
            double distance = 9.0;
            for (var candidate : SteeringWheelBlockEntity.serverLoaded()) {
                if (!(candidate instanceof MotorcycleHandlebarBlockEntity bars) || bars.isRemoved() || bars.getLevel() != level() || bars.hasUser()) continue;
                if (Sable.HELPER.getContaining(bars) != Sable.HELPER.getContaining(this)) continue;
                double d = bars.getBlockPos().distToCenterSqr(position());
                if (d < distance) { nearest = bars; distance = d; }
            }
            if (nearest != null) nearest.setDriving(player, true);
        }
    }

    @Override protected void removePassenger(Entity passenger) {
        if (!level().isClientSide && passenger instanceof Player player) {
            var bars = SteeringWheelBlockEntity.findDrivenBy(player);
            if (bars instanceof MotorcycleHandlebarBlockEntity) bars.setDriving(player, false);
        }
        super.removePassenger(passenger);
    }
}
