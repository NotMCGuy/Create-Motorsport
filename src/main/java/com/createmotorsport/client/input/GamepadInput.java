package com.createmotorsport.client.input;

import com.createmotorsport.Config;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class GamepadInput {

    private static float stickDeadzone() {return (float) Config.STICK_DEADZONE.getAsDouble(); }
    private static float triggerDeadzone() {return (float) Config.TRIGGER_DEADZONE.getAsDouble(); }
    private static float rawDeadzone() {return (float) Config.ADVANCED_INPUT_DEADZONE.getAsDouble(); }

    private static final JoystickPoller POLLER = new JoystickPoller();
    private static final Set<Integer> captureLast = new HashSet<>();

    private GamepadInput() {
    }

    private static boolean advanced() {
        return Config.ENABLE_ADVANCED_INPUT.get();
    }

    public static void poll() {
        POLLER.poll(advanced());
    }

    public static float analogMagnitude(int code) {
        if (!GamepadCodes.isGamepadCode(code)) {
            return 0.0F;
        }
        int d = GamepadCodes.deviceOf(code);
        if (!POLLER.present(d)) {
            return 0.0F;
        }
        if (GamepadCodes.isBasicButton(code)) {
            return POLLER.button(d, GamepadCodes.basicButtonIndex(code)) ? 1.0F : 0.0F;
        }
        if (GamepadCodes.isBasicTrigger(code)) {
            float raw = (POLLER.axis(d, GamepadCodes.triggerAxis(code)) + 1.0F) * 0.5F; // -1 rest to +1 pulled
            return raw <= triggerDeadzone() ? 0.0F : ramp(raw, triggerDeadzone());
        }
        if (GamepadCodes.isBasicStick(code)) {
            float v = POLLER.axis(d, GamepadCodes.stickAxis(code));
            float dir = GamepadCodes.stickPositive(code) ? v : -v;
            return dir <= stickDeadzone() ? 0.0F : ramp(dir, stickDeadzone());
        }
        if (GamepadCodes.isRawButton(code)) {
            return POLLER.button(d, GamepadCodes.rawButtonIndex(code)) ? 1.0F : 0.0F;
        }
        if (GamepadCodes.isRawAxisPos(code)) {
            float dz = rawDeadzone();
            float v = POLLER.axis(d, GamepadCodes.rawAxisIndex(code));
            return v <= dz ? 0.0F : ramp(v, dz);
        }
        if (GamepadCodes.isRawAxisNeg(code)) {
            float dz = rawDeadzone();
            float v = -POLLER.axis(d, GamepadCodes.rawAxisIndex(code));
            return v <= dz ? 0.0F : ramp(v, dz);
        }
        if (GamepadCodes.isRawHat(code)) {
            int bits = POLLER.hat(d, GamepadCodes.rawHatIndex(code)) & 0xFF;
            return (bits & hatBit(GamepadCodes.rawHatDir(code))) != 0 ? 1.0F : 0.0F;
        }
        return 0.0F;
    }

    public static boolean isDown(int code) {
        return analogMagnitude(code) > 0.0F;
    }

    public static void beginCapture() {
        poll();
        captureLast.clear();
        captureLast.addAll(pressedSet());
    }

    public static int pollCapture() {
        poll();
        Set<Integer> now = pressedSet();
        int found = -1;
        for (int code : now) {
            if (!captureLast.contains(code)) {
                found = code;
                break;
            }
        }
        captureLast.clear();
        captureLast.addAll(now);
        return found;
    }

    // Everything currently pressed across every device, as synthetic codes (for bind capture)
    private static Set<Integer> pressedSet() {
        Set<Integer> set = new HashSet<>();
        for (int d = 0; d < JoystickPoller.MAX_DEVICES; d++) {
            if (!POLLER.present(d)) {
                continue;
            }
            if (POLLER.isBasic(d)) {
                for (int i = 0; i < GamepadCodes.BUTTON_COUNT; i++) {
                    if (POLLER.button(d, i)) {
                        set.add(GamepadCodes.basicButton(d, i));
                    }
                }
                if ((POLLER.axis(d, GamepadCodes.AXIS_LT) + 1.0F) * 0.5F >= triggerDeadzone()) {
                    set.add(GamepadCodes.triggerLT(d));
                }
                if ((POLLER.axis(d, GamepadCodes.AXIS_RT) + 1.0F) * 0.5F >= triggerDeadzone()) {
                    set.add(GamepadCodes.triggerRT(d));
                }
                addBasicStick(set, d, POLLER.axis(d, GamepadCodes.AXIS_LEFT_X), POLLER.axis(d, GamepadCodes.AXIS_LEFT_Y), 0);
                addBasicStick(set, d, POLLER.axis(d, GamepadCodes.AXIS_RIGHT_X), POLLER.axis(d, GamepadCodes.AXIS_RIGHT_Y), 4);
            } else {
                for (int i = 0; i < POLLER.buttonCount(d); i++) {
                    if (POLLER.button(d, i)) {
                        set.add(GamepadCodes.rawButton(d, i));
                    }
                }
                for (int a = 0; a < POLLER.axisCount(d); a++) {
                    float v = POLLER.axis(d, a);
                    if (v >= rawDeadzone()) {
                        set.add(GamepadCodes.rawAxisPos(d, a));
                    } else if (v <= -rawDeadzone()) {
                        set.add(GamepadCodes.rawAxisNeg(d, a));
                    }
                }
                for (int h = 0; h < POLLER.hatCount(d); h++) {
                    int bits = POLLER.hat(d, h) & 0xFF;
                    for (int dir = 0; dir < 4; dir++) {
                        if ((bits & hatBit(dir)) != 0) {
                            set.add(GamepadCodes.rawHat(d, h, dir));
                        }
                    }
                }
            }
        }
        return set;
    }

    private static void addBasicStick(Set<Integer> set, int d, float x, float y, int kBase) {
        if (x >= stickDeadzone())  set.add(GamepadCodes.stickCode(d, kBase));       // right (+x)
        if (x <= -stickDeadzone()) set.add(GamepadCodes.stickCode(d, kBase + 1));   // left  (-x)
        if (y >= stickDeadzone())  set.add(GamepadCodes.stickCode(d, kBase + 2));   // down  (+y)
        if (y <= -stickDeadzone()) set.add(GamepadCodes.stickCode(d, kBase + 3));   // up    (-y)
    }

    // GamepadCodes hat direction
    private static int hatBit(int dir) {
        return switch (dir) {
            case 0 -> GLFW.GLFW_HAT_UP;
            case 1 -> GLFW.GLFW_HAT_RIGHT;
            case 2 -> GLFW.GLFW_HAT_DOWN;
            default -> GLFW.GLFW_HAT_LEFT;
        };
    }

    private static float ramp(float raw, float threshold) {
        float m = (raw - threshold) / (1.0F - threshold);
        return m < 0.0F ? 0.0F : Math.min(1.0F, m);
    }
}
