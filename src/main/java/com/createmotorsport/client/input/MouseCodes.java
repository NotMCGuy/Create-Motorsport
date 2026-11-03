package com.createmotorsport.client.input;

// Synthetic keycodes for mouse movement
public final class MouseCodes {
    private MouseCodes() {}

    public static final int BASE  = 6000;
    public static final int RANGE = 100;

    private static final int AXIS_OFF = 20;

    public static final int AXIS_X_POS = BASE + AXIS_OFF;     // move right
    public static final int AXIS_X_NEG = BASE + AXIS_OFF + 1; // move left
    public static final int AXIS_Y_POS = BASE + AXIS_OFF + 2; // move down
    public static final int AXIS_Y_NEG = BASE + AXIS_OFF + 3; // move up

    public static boolean isMouseCode(int code) { return code >= BASE && code < BASE + RANGE; }

    public static boolean isAxis(int code) {
        if (!isMouseCode(code)) return false;
        int o = code - BASE;
        return o >= AXIS_OFF && o < AXIS_OFF + 4;
    }

    // yes it is
    public static boolean isAnalog(int code) { return isAxis(code); }

    public static int axisOf(int code) { return ((code - BASE - AXIS_OFF) >= 2) ? 1 : 0; } // 0 = X, 1 = Y

    public static boolean axisPositive(int code) { return ((code - BASE - AXIS_OFF) & 1) == 0; }

    public static String name(int code) {
        return switch (code) {
            case AXIS_X_POS -> "Mouse Right";
            case AXIS_X_NEG -> "Mouse Left";
            case AXIS_Y_POS -> "Mouse Down";
            case AXIS_Y_NEG -> "Mouse Up";
            default -> "Mouse?";
        };
    }
}
