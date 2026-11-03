package com.createmotorsport.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class MouseSteerCamera {
    private MouseSteerCamera() {}

    private static boolean locked = false;
    private static float frozenYaw;
    private static float frozenPitch;

    // Called every client tick, shouldLock = driving & mouse steering & free look key not held
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
                MouseInput.recenter(); // recenter absolute mode
            }

            // undoes whatever the camera turn did to the body this tick so it stays put
            player.setYRot(frozenYaw);
            player.setXRot(frozenPitch);
            player.yRotO = frozenYaw;
            player.xRotO = frozenPitch;
        } else if (locked) {
            locked = false; //holding free look key to look around
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
