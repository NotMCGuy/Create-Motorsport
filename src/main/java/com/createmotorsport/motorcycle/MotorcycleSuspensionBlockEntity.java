package com.createmotorsport.motorcycle;

import com.createmotorsport.block.entity.SuspensionBlockEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MotorcycleSuspensionBlockEntity extends SuspensionBlockEntity implements MotorcyclePart {
    private final MotorcycleAppearance appearance = new MotorcycleAppearance();
    @Override public MotorcycleAppearance appearance() { return appearance; }
    @Override protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean packet) { super.write(tag,provider,packet); appearance.write(tag); }
    @Override protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean packet) { super.read(tag,provider,packet); appearance.read(tag); }
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() { return new net.minecraft.world.phys.AABB(worldPosition).inflate(10); }
    private double handlingSpeed;
    private double brakingBank, brakingPitch, brakingPitchRate;
    private double requestedBrake, brakePressure, requestedSteer;
    private long brakeInputTime = Long.MIN_VALUE;
    private double handlingWheelbase = 3;
    private final MotorcycleBalance balance = new MotorcycleBalance();
    public MotorcycleSuspensionBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }

    @Override public BlockEntityType<?> getType() { return MotorcycleModule.SUSPENSION_ENTITY.get(); }
    @Override public boolean isFrontAxle() { return ((MotorcycleSuspensionBlock) getBlockState().getBlock()).isFork(); }
    @Override public void toggleAxleEnd() {                                     }

    @Override public Direction getSideDirection(WheelSide side) { return Direction.UP; }
    @Override protected double camberSign(WheelSide side) { return 0; }
    @Override public boolean isSteered() { return isFrontAxle(); }
    @Override public boolean isDriven() { return hasDriveConnection(); }
    @Override protected boolean steerAssistOff(boolean requested) {
        return requested || MotorcycleBalanceConfig.HANDLING.get();
    }
    @Override public double wheelSteerAngle(double base, WheelSide side) {
        if (!isFrontAxle()) return 0;
        return MotorcycleBalanceConfig.HANDLING.get()
                ? MotorcycleHandling.steering(base,handlingSpeed,handlingWheelbase,MotorcycleBalanceConfig.CORNERING_G.get()) : base;
    }
    @Override public void setDriverSteering(double steer,double brake) {
        if(level==null||level.isClientSide)return;
        requestedSteer=steer;requestedBrake=Math.clamp(brake/15,0,1);brakeInputTime=level.getGameTime();
        super.setDriverSteering(steer,MotorcycleBalanceConfig.HANDLING.get()?brakePressure*15:brake);
    }
    @Override public void tick() {
        if (level != null && level.isClientSide) updateHandling();
        super.tick();
    }

    private void updateHandling() {
        if (level != null) {
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
            handlingSpeed = 0;
            if (body != null) {
                var velocity = dev.ryanhcode.sable.Sable.HELPER.getVelocity(level,axleCentre());
                var forward = body.logicalPose().transformNormal(new org.joml.Vector3d(getFacing().getStepX(),0,getFacing().getStepZ())).normalize();
                handlingSpeed = velocity.x * forward.x + velocity.y * forward.y + velocity.z * forward.z;
            }
            if (body != null && level.getGameTime()%10==0) {
                for (var actor : body.getPlot().getBlockEntityActors()) {
                    if (actor instanceof MotorcycleSuspensionBlockEntity rear && !rear.isFrontAxle() && rear.getFacing()==getFacing()) {
                        double distance=Math.abs((axleCentre().x-rear.axleCentre().x)*getFacing().getStepX()
                                +(axleCentre().z-rear.axleCentre().z)*getFacing().getStepZ());
                        if(distance>=.5)handlingWheelbase=distance * body.logicalPose().scale().get(getFacing().getAxis() == Direction.Axis.X ? 0 : 2);
                    }
                }
            }
        }
    }

    @Override public void gameStep() {
        updateHandling();
        if (level != null) {
            if(!level.isClientSide && MotorcycleBalanceConfig.HANDLING.get() && brakeInputTime != Long.MIN_VALUE) {
                boolean fresh=level.getGameTime()-brakeInputTime<=3;
                brakePressure=MotorcycleHandling.brakeResponse(brakePressure,fresh?requestedBrake:0,.05);
                if(fresh) {
                    super.setDriverSteering(requestedSteer,brakePressure*15);
                }
            }
        }
        super.gameStep();
    }
    @Override public boolean installTire(WheelSide side, ItemStack stack) { return super.installTire(WheelSide.LEFT, stack); }
    @Override public ItemStack removeTire(WheelSide side) { return super.removeTire(WheelSide.LEFT); }

    public boolean hasDriveConnection() {
        return !isFrontAxle() && level != null && MotorcycleGearboxBlock.connectedEngine(level, worldPosition, getFacing()) != null;
    }

    @Override public void setDrive(double torque, double stiffness, double min, double max) {
        if (hasDriveConnection()) super.setDrive(torque, stiffness, min, max);
        else super.setDrive(0, 0, 0, 0);
    }

    @Override public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double dt) {
        var forward=subLevel.logicalPose().transformNormal(new org.joml.Vector3d(getFacing().getStepX(),0,getFacing().getStepZ())).normalize();
        handlingSpeed=handle.getLinearVelocity(new org.joml.Vector3d()).dot(forward);
        var up=subLevel.logicalPose().transformNormal(new org.joml.Vector3d(0,1,0)).normalize();
        var right=getFacing().getClockWise();
        var bodyRight=subLevel.logicalPose().transformNormal(new org.joml.Vector3d(right.getStepX(),0,right.getStepZ())).normalize();
        brakingBank=Math.acos(Math.clamp(up.y,-1,1));
        brakingPitch=Math.atan2(forward.y,Math.hypot(forward.x,forward.z));
        brakingPitchRate=handle.getAngularVelocity(new org.joml.Vector3d()).dot(bodyRight);
        if (!hasDriveConnection()) {
            super.setDrive(0, 0, 0, 0);
        }
        super.sable$physicsTick(subLevel, handle, dt);
        if (isFrontAxle()) balance.step(this, subLevel, handle, dt);
    }

    public static double aerodynamicScale(ServerSubLevel body) {
        int fronts = 0, rears = 0;
        Direction facing = null;
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof SuspensionBlockEntity suspension) || suspension.isRemoved()) continue;
            if (!(suspension instanceof MotorcycleSuspensionBlockEntity bike) || !bike.hasAnyTire()) return 1;
            if (facing != null && facing != bike.getFacing()) return 1;
            facing = bike.getFacing();
            if (bike.isFrontAxle()) fronts++; else rears++;
        }
        return fronts == 1 && rears == 1 ? MotorcycleHandling.AERO_DRAG_SCALE : 1;
    }

    public double brakingAmount() { return brakePressure; }

    @Override protected double limitBrakeTorque(double requested,double radius,double inertia,double omega,
                                                double driveTorque,double previousForce,double speed,
                                                double normalForce,double gripForce,double dt) {
        if (!MotorcycleBalanceConfig.HANDLING.get()) return requested;
        return MotorcycleHandling.brakeTorque(requested*MotorcycleBalanceConfig.BRAKE_GAIN.get(),radius,inertia,
                omega,driveTorque,previousForce,speed,normalForce,gripForce,brakingBank,
                brakingPitch,brakingPitchRate,dt);
    }
}
