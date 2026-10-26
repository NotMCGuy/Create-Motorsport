package com.createmotorsport.motorcycle.client;

import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.client.input.SteeringInputHandler;
import com.createmotorsport.motorcycle.MotorcycleHandlebarBlockEntity;
import com.createmotorsport.motorcycle.MotorcycleSeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class MotorcycleSeatControls {
    private static Level level;
    private static int seatId = -1;
    private static BlockPos ownedControls;
    private static boolean started;

    static void tick() {
        var mc = Minecraft.getInstance();
        int currentSeat = mc.player != null && mc.player.getVehicle() instanceof MotorcycleSeatEntity seat
                && !seat.isRemoved() ? seat.getId() : -1;
        if (level != mc.level || seatId != currentSeat) {
            release();
            level = mc.level;
            seatId = currentSeat;
            started = false;
        }
        if (currentSeat == -1 || mc.level == null || mc.player == null) return;
        if (ownedControls != null && !(mc.level.getBlockEntity(ownedControls) instanceof MotorcycleHandlebarBlockEntity)) {
            release();
            return;
        }
        if (started || SteeringInputHandler.isDriving()) return;
        for (var candidate : MotorcycleHandlebarBlockEntity.clientLoaded()) {
            if (candidate instanceof MotorcycleHandlebarBlockEntity bars && !bars.isRemoved()
                    && bars.getLevel() == mc.level && bars.isUser(mc.player)) {
                ownedControls = bars.getBlockPos();
                SteeringInputHandler.startDriving(ownedControls);
                started = true;
                return;
            }
        }
    }

    private static void release() {
        if (ownedControls != null && ownedControls.equals(SteeringInputHandler.getActivePos())) SteeringInputHandler.stop();
        ownedControls = null;
    }
}
