package com.createmotorsport.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class MotorsportKeybinds {
    private static final String CATEGORY = "key.categories.createmotorsport";

    // Hold this key to move camera while driving
    public static final KeyMapping FREE_LOOK = new KeyMapping(
            "key.createmotorsport.freelook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    // Stop driving, uncapture the keyboard from the steering wheel controller
    public static final KeyMapping EXIT_VEHICLE = new KeyMapping(
            "key.createmotorsport.exit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ESCAPE, CATEGORY);

    private MotorsportKeybinds() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(FREE_LOOK);
        event.register(EXIT_VEHICLE);
    }

    public static boolean freeLookHeld() {
        return FREE_LOOK.isDown();
    }

    public static boolean isExit(int key, int scanCode) {
        return EXIT_VEHICLE.matches(key, scanCode);
    }

    public static boolean isFreeLook(int key, int scanCode) {
        return FREE_LOOK.matches(key, scanCode);
    }

    public static Component exitKeyName() {
        return EXIT_VEHICLE.getTranslatedKeyMessage();
    }
}
