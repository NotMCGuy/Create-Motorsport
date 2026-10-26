package com.createmotorsport.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class MouseSteerCamera {
    private MouseSteerCamera() {}

    private static boolean locked = false;
    private static float frozenYaw;
    private static float frozenPitch;

    public static void update(boolean shouldLock) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            locked = false;
            return;
        }
        if (shouldLock) {
            if (!locked) {
                frozenYaw = player.getYRot();
                frozenPitch = player.getXRot();
                locked = true;
                MouseInput.recenter();
            }

            player.setYRot(frozenYaw);
            player.setXRot(frozenPitch);
            player.yRotO = frozenYaw;
            player.xRotO = frozenPitch;
        } else if (locked) {
            locked = false;
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (locked) {
            event.setYaw(frozenYaw);
            event.setPitch(frozenPitch);
        }
    }

    public static void reset() {
        locked = false;
    }
}
