package com.createmotorsport.client.input;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

// Polls every connected joystick each tick
// Theres a basic mode that is on by default, which looks for GLFW reconized axis.
// to use a racing wheel and pedal, enable Advanced Controls which allows for raw inputs
public final class JoystickPoller {

    public static final int MAX_DEVICES = GamepadCodes.MAX_DEVICES;
    public static final int MAX_AXES    = 16;
    public static final int MAX_BUTTONS = 32;
    public static final int MAX_HATS    = 4;

    private final boolean[]   present     = new boolean[MAX_DEVICES];
    private final boolean[]   basic       = new boolean[MAX_DEVICES];
    private final float[][]   axes        = new float[MAX_DEVICES][MAX_AXES];
    private final boolean[][] buttons     = new boolean[MAX_DEVICES][MAX_BUTTONS];
    private final byte[][]    hats        = new byte[MAX_DEVICES][MAX_HATS];
    private final int[]       axisCount   = new int[MAX_DEVICES];
    private final int[]       buttonCount = new int[MAX_DEVICES];
    private final int[]       hatCount    = new int[MAX_DEVICES];

    private GLFWGamepadState state;

    public void poll(boolean advanced) {
        if (state == null) state = GLFWGamepadState.create();
        for (int d = 0; d < MAX_DEVICES; d++) {
            if (!GLFW.glfwJoystickPresent(d)) { clear(d); continue; }
            if (!advanced) pollBasic(d);
            else           pollRaw(d);
        }
    }

    private void pollBasic(int d) {
        // Basic mode only brings up devices that GLFW recognizes as gamepads
        if (GLFW.glfwJoystickIsGamepad(d) && GLFW.glfwGetGamepadState(d, state)) {
            present[d] = true;
            basic[d]   = true;
            buttonCount[d] = 15;
            for (int i = 0; i < 15; i++) buttons[d][i] = state.buttons(i) != 0;
            for (int i = 15; i < MAX_BUTTONS; i++) buttons[d][i] = false;
            axisCount[d] = 6;
            for (int i = 0; i < 6; i++) axes[d][i] = state.axes(i);
            for (int i = 6; i < MAX_AXES; i++) axes[d][i] = 0f;
            hatCount[d] = 0;
        } else {
            clear(d);
        }
    }

    private void pollRaw(int d) {
        FloatBuffer ax  = GLFW.glfwGetJoystickAxes(d);
        ByteBuffer  btn = GLFW.glfwGetJoystickButtons(d);
        ByteBuffer  ht  = GLFW.glfwGetJoystickHats(d);
        if (ax == null && btn == null) { clear(d); return; }

        present[d] = true;
        basic[d]   = false;

        int ac = ax == null ? 0 : Math.min(MAX_AXES, ax.remaining());
        axisCount[d] = ac;
        for (int i = 0; i < ac; i++) axes[d][i] = ax.get(i);
        for (int i = ac; i < MAX_AXES; i++) axes[d][i] = 0f;

        int bc = btn == null ? 0 : Math.min(MAX_BUTTONS, btn.remaining());
        buttonCount[d] = bc;
        for (int i = 0; i < bc; i++) buttons[d][i] = btn.get(i) != 0;
        for (int i = bc; i < MAX_BUTTONS; i++) buttons[d][i] = false;

        int hc = ht == null ? 0 : Math.min(MAX_HATS, ht.remaining());
        hatCount[d] = hc;
        for (int i = 0; i < hc; i++) hats[d][i] = ht.get(i);
        for (int i = hc; i < MAX_HATS; i++) hats[d][i] = 0;
    }

    private void clear(int d) {
        present[d] = false;
        basic[d]   = false;
        axisCount[d] = buttonCount[d] = hatCount[d] = 0;
        for (int i = 0; i < MAX_AXES; i++)    axes[d][i]    = 0f;
        for (int i = 0; i < MAX_BUTTONS; i++) buttons[d][i] = false;
        for (int i = 0; i < MAX_HATS; i++)    hats[d][i]    = 0;
    }

    public boolean present(int d)   { return d >= 0 && d < MAX_DEVICES && present[d]; }
    public boolean isBasic(int d)   { return d >= 0 && d < MAX_DEVICES && basic[d]; }
    public int  axisCount(int d)    { return d >= 0 && d < MAX_DEVICES ? axisCount[d] : 0; }
    public int  buttonCount(int d)  { return d >= 0 && d < MAX_DEVICES ? buttonCount[d] : 0; }
    public int  hatCount(int d)     { return d >= 0 && d < MAX_DEVICES ? hatCount[d] : 0; }
    public float axis(int d, int i) { return inBounds(d, i, MAX_AXES) ? axes[d][i] : 0f; }
    public boolean button(int d, int i) { return inBounds(d, i, MAX_BUTTONS) && buttons[d][i]; }
    public byte hat(int d, int i)   { return inBounds(d, i, MAX_HATS) ? hats[d][i] : 0; }


    private static boolean inBounds(int d, int i, int max) {
        return d >= 0 && d < MAX_DEVICES && i >= 0 && i < max;
    }
}
