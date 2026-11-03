package com.createmotorsport.client.input;

import com.createmotorsport.Config;
import net.minecraft.client.Minecraft;

// mouse input ported from Universal Keyboard, but looked at how speed dreams did it for calibration
public final class MouseInput {
    private MouseInput() {}

    private static final float FULL_DEFLECTION_PX = 18f;
    private static final float ABSOLUTE_SENS_FACTOR = 0.1f;
    private static final float CAPTURE_MOVE = 0.5f; // larger deadzone for initial binding

    private static double lastX = Double.NaN, lastY;
    private static float curDX, curDY;      // per tick pixel delta
    private static double anchorX, anchorY; // cursor position when the lock engaged, for absolute mode
    private static float absOffX, absOffY;

    public static boolean enabled() {
        return Config.ENABLE_MOUSE_INPUT.get();
    }

    private static boolean absolute() {
        return Config.MOUSE_ABSOLUTE_MODE.get();
    }

    private static double sensitivity() {
        return Config.MOUSE_SENSITIVITY.getAsDouble();
    }

    private static float deadzone() {
        return (float) Config.MOUSE_DEADZONE.getAsDouble();
    }

    public static void poll() {
        if (!enabled()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        if (Double.isNaN(lastX)) {
            lastX = x;
            lastY = y;
            anchorX = x;
            anchorY = y;
            curDX = curDY = 0f;
            return;
        }
        curDX = (float) (x - lastX);
        curDY = (float) (y - lastY);
        lastX = x;
        lastY = y;
        absOffX = (float) (x - anchorX);
        absOffY = (float) (y - anchorY);
    }

    // Recenter to the current cursor
    public static void recenter() {
        Minecraft mc = Minecraft.getInstance();
        anchorX = mc.mouseHandler.xpos();
        anchorY = mc.mouseHandler.ypos();
        absOffX = absOffY = 0f;
        curDX = curDY = 0f;
        lastX = Double.NaN;
    }

    public static void reset() {
        lastX = Double.NaN;
        curDX = curDY = 0f;
        absOffX = absOffY = 0f;
    }

    private static float axisMag(float deltaPx) {
        float sens = (float) sensitivity() * (absolute() ? ABSOLUTE_SENS_FACTOR : 1f);
        return Math.max(0f, Math.min(1f, Math.abs(deltaPx) / FULL_DEFLECTION_PX * sens));
    }

    public static float analogMagnitude(int code) {
        if (!enabled() || !MouseCodes.isAxis(code)) {
            return 0f;
        }
        boolean xAxis = MouseCodes.axisOf(code) == 0;
        float source = absolute() ? (xAxis ? absOffX : absOffY) : (xAxis ? curDX : curDY);
        boolean dirOk = MouseCodes.axisPositive(code) ? source > 0f : source < 0f;
        if (!dirOk) {
            return 0f;
        }
        float raw = axisMag(source);
        float dz = deadzone();
        return raw <= dz ? 0f : Math.min(1f, (raw - dz) / (1f - dz));
    }

    public static boolean isDown(int code) {
        return analogMagnitude(code) > 0f;
    }

    // Binding capture
    public static void beginCapture() {
        poll();
    }

    public static int pollCapture() {
        poll();
        if (axisMag(curDX) >= CAPTURE_MOVE) {
            return curDX > 0f ? MouseCodes.AXIS_X_POS : MouseCodes.AXIS_X_NEG;
        }
        if (axisMag(curDY) >= CAPTURE_MOVE) {
            return curDY > 0f ? MouseCodes.AXIS_Y_POS : MouseCodes.AXIS_Y_NEG;
        }
        return -1;
    }
}
