package com.createmotorsport.item;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.FuelPumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.UUID;

public class FuelNozzleItem extends Item {
    public FuelNozzleItem(Properties properties) { super(properties); }
    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static UUID sessionId(ItemStack stack) {
        if (!stack.is(CreateMotorsport.FUEL_NOZZLE.get())) return null;
        CompoundTag tag = data(stack);
        return tag.hasUUID("Session") ? tag.getUUID("Session") : null;
    }
    public static ItemStack create(FuelPumpBlockEntity pump) {
        ItemStack stack = new ItemStack(CreateMotorsport.FUEL_NOZZLE.get());
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Session", pump.session.id());
        tag.putLong("Pump", pump.getBlockPos().asLong());
        tag.putString("Dimension", pump.getLevel().dimension().location().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
    public static FuelPumpBlockEntity resolve(Level level, ItemStack stack) {
        if (sessionId(stack) == null) return null;
        CompoundTag tag = data(stack);
        if (!tag.getString("Dimension").equals(level.dimension().location().toString()) || !tag.contains("Pump")) return null;
        BlockPos pos = BlockPos.of(tag.getLong("Pump"));
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FuelPumpBlockEntity pump ? pump : null;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide) {
            FuelPumpBlockEntity pump = resolve(ctx.getLevel(), stack);
            if (pump == null) { stack.setCount(0); FuelPumpBlockEntity.feedback(player, "lost"); }
            else if (ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof com.createmotorsport.block.FuelPumpBlock
                    && com.createmotorsport.block.FuelPumpBlock.base(ctx.getLevel().getBlockState(ctx.getClickedPos()), ctx.getClickedPos()).equals(pump.getBlockPos())) pump.dock(player, stack);
            else pump.fill(player, stack, ctx.getLevel(), ctx.getClickedPos(), ctx.getClickedFace());
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player) {
            FuelPumpBlockEntity pump = resolve(level, stack);
            if (pump == null || !pump.valid(player, stack)) stack.setCount(0);
        }
    }
    @Override public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (!player.level().isClientSide) {
            FuelPumpBlockEntity pump = resolve(player.level(), stack);
            if (pump != null && pump.session.matches(player.getUUID(), sessionId(stack))) pump.reset();
            stack.setCount(0);
        }
        return false;
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        if (!entity.level().isClientSide) entity.discard();
        return true;
    }
    public static int count(Player player, UUID session) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (session.equals(sessionId(stack))) count += stack.getCount();
        }
        if (session.equals(sessionId(player.containerMenu.getCarried()))) count++;
        return count;
    }
    public static void remove(Player player, UUID session) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (session.equals(sessionId(stack))) stack.setCount(0);
        }

        for (var slot : player.containerMenu.slots) {
            if (session.equals(sessionId(slot.getItem()))) slot.set(ItemStack.EMPTY);
        }
        if (session.equals(sessionId(player.containerMenu.getCarried()))) player.containerMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.broadcastChanges();
    }
    public static void cleanup(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        for (var slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.is(CreateMotorsport.FUEL_NOZZLE.get())) continue;
            FuelPumpBlockEntity pump = resolve(player.level(), stack);
            if (pump == null || !pump.valid(player, stack)) {
                if (pump != null && pump.session.matches(player.getUUID(), sessionId(stack))) pump.reset();
                slot.set(ItemStack.EMPTY);
            }
        }
        ItemStack cursor = player.containerMenu.getCarried();
        if (cursor.is(CreateMotorsport.FUEL_NOZZLE.get())) {
            FuelPumpBlockEntity pump = resolve(player.level(), cursor);
            if (pump != null && pump.session.matches(player.getUUID(), sessionId(cursor))) pump.reset();
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        FuelPumpBlockEntity pump = resolve(player.level(), player.getMainHandItem());
        if (pump != null && pump.session.matches(player.getUUID(), sessionId(player.getMainHandItem()))) pump.reset();
    }
}
