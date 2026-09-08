package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.FuelPumpBlock;
import com.createmotorsport.fuel.*;
import com.createmotorsport.item.FuelNozzleItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import java.util.List;

public class FuelPumpBlockEntity extends SmartBlockEntity {
    public final PumpSession session = new PumpSession();
    private boolean transferring;
    private double clientHoseRange = 12;
    private final FluidTank tank = new FluidTank(Config.PUMP_CAPACITY.get(), FuelRules::accepts) {
        @Override protected void onContentsChanged() { notifyUpdate(); }
    };

    public final IFluidHandler fluidAccess = new IFluidHandler() {
        public int getTanks() { return 1; }
        public FluidStack getFluidInTank(int i) { return tank.getFluid().copy(); }
        public int getTankCapacity(int i) { return tank.getCapacity(); }
        public boolean isFluidValid(int i, FluidStack fluid) { return FuelRules.accepts(fluid); }
        public int fill(FluidStack fluid, FluidAction action) { return transferring ? 0 : tank.fill(fluid, action); }
        public FluidStack drain(FluidStack fluid, FluidAction action) { return transferring ? FluidStack.EMPTY : tank.drain(fluid, action); }
        public FluidStack drain(int amount, FluidAction action) { return transferring ? FluidStack.EMPTY : tank.drain(amount, action); }
    };

    public FuelPumpBlockEntity(BlockPos pos, BlockState state) { super(CreateMotorsport.FUEL_PUMP_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(66);
    }
    public double hoseRange() { return level != null && level.isClientSide ? clientHoseRange : Config.PUMP_RANGE.get(); }
    public Vec3 attachment() { return FuelCoordinates.attachment(level, worldPosition, getBlockState().getValue(FuelPumpBlock.FACING)); }
    public static void feedback(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.createmotorsport.fuel." + key, args), true);
    }
    private void sound(SoundEvent sound) {
        Vec3 p = attachment();
        level.playSound(null, p.x, p.y, p.z, sound, SoundSource.BLOCKS, 0.7f, 1f);
    }
    public void take(Player player) {
        if (level.isClientSide || !reachable(player, 5)) return;
        if (session.held()) { feedback(player, "busy"); return; }
        if (!player.getMainHandItem().isEmpty() || player.isSpectator()) { feedback(player, "empty_hand"); return; }
        session.start(player.getUUID(), player.getInventory().selected);
        player.setItemInHand(InteractionHand.MAIN_HAND, FuelNozzleItem.create(this));
        notifyUpdate();
        sound(SoundEvents.IRON_TRAPDOOR_OPEN);
    }
    public boolean reachable(Player player, double range) {
        return player.level() == level && FuelCoordinates.inRange(attachment(),
                FuelCoordinates.global(player.level(), player.getEyePosition()), range);
    }
    public boolean valid(Player player, ItemStack stack) {
        return !isRemoved() && player.isAlive() && !player.isSpectator()
                && session.matches(player.getUUID(), FuelNozzleItem.sessionId(stack))
                && player.getInventory().selected == session.slot()
                && player.getMainHandItem() == stack && stack.getCount() == 1
                && reachable(player, Config.PUMP_RANGE.get());
    }
    @Override public void tick() {
        super.tick();
        if (level.isClientSide || !session.held()) return;
        Player player = level.getPlayerByUUID(session.owner());
        if (player == null || !valid(player, player.getMainHandItem()) || FuelNozzleItem.count(player, session.id()) != 1) {
            if (player != null) feedback(player, player.level() == level && !reachable(player, Config.PUMP_RANGE.get()) ? "range" : "lost");
            reset();
        }
    }
    public void reset() {
        if (level == null || level.isClientSide || !session.held()) return;
        Player player = level.getServer().getPlayerList().getPlayer(session.owner());
        if (player != null) FuelNozzleItem.remove(player, session.id());
        session.reset();
        notifyUpdate();
    }
    public void dock(Player player, ItemStack stack) {
        if (!valid(player, stack) || !reachable(player, 5)) { feedback(player, "lost"); return; }
        sound(SoundEvents.IRON_TRAPDOOR_CLOSE);
        reset();
    }
    public void fill(Player player, ItemStack stack, Level targetLevel, BlockPos target, Direction side) {
        if (!valid(player, stack)) {
            feedback(player, "lost");
            if (session.matches(player.getUUID(), FuelNozzleItem.sessionId(stack))) reset();
            stack.setCount(0);
            return;
        }
        if (targetLevel != level || !targetLevel.hasChunkAt(target)) { feedback(player, "lost"); return; }

        Vec3 globalTarget = FuelCoordinates.global(targetLevel, Vec3.atCenterOf(target));
        if (!FuelCoordinates.inRange(attachment(), globalTarget, Config.PUMP_RANGE.get())
                || !FuelCoordinates.inRange(FuelCoordinates.global(level, player.getEyePosition()), globalTarget, 6)) {
            feedback(player, "range"); return;
        }
        if (!session.claimTransfer(level.getGameTime())) return;
        if (tank.isEmpty()) { feedback(player, "empty"); return; }
        if (!FuelRules.accepts(tank.getFluid())) { feedback(player, "invalid_fuel"); return; }
        IFluidHandler receiver = FluidUtil.getFluidHandler(targetLevel, target, side).orElse(null);
        if (receiver == null || receiver == fluidAccess) { feedback(player, "full"); return; }
        transferring = true;
        int amount;
        try { amount = FuelTransfer.transfer(tank, receiver, Config.PUMP_TRANSFER.get()); }
        finally { transferring = false; notifyUpdate(); }
        feedback(player, amount > 0 ? "success" : "full", amount);
        if (amount > 0) sound(SoundEvents.BUCKET_EMPTY_LAVA);
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        if (clientPacket) tag.putDouble("HoseRange", Config.PUMP_RANGE.get());
        if (clientPacket && session.held()) {
            tag.putUUID("Owner", session.owner());
            tag.putUUID("Session", session.id());
        }
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        if (clientPacket) clientHoseRange = Math.clamp(tag.getDouble("HoseRange"), 2, 64);
        session.reset();
        if (clientPacket && tag.hasUUID("Owner") && tag.hasUUID("Session")) session.readClient(tag.getUUID("Owner"), tag.getUUID("Session"));
    }
    @Override public void remove() { reset(); super.remove(); }
    @Override public void onChunkUnloaded() { reset(); super.onChunkUnloaded(); }
}
