package com.createmotorsport.physics.control;

/**
 * Ignition is done with a keybind or redstone signal that you hold down to start, press again to stop
 * EngineBlockEntity steps this in its ticks
 */
public final class IgnitionSwitch {

    private boolean on = true;
    private boolean held;
    private boolean cranking;

    /**
     * @param keyHeld keybind
     * @param keyed if the engine has an ingition through the steering wheel, otherwise its redstone controled
     * @param linked if its redstone controlled
     * @param linkOn
     * @param firing if the engine fired prev tick
     * @return whether starter is engaged
     */
    public boolean step(boolean keyHeld, boolean keyed, boolean linked, boolean linkOn, boolean firing) {
        boolean press = keyHeld && !this.held;
        this.held = keyHeld;
        if (!keyed) {
            this.cranking = false;
            this.on = !linked || linkOn;
            return this.on && !firing;
        }
        if (press) {
            this.on = !firing;
            this.cranking = !firing;
        }
        if (!keyHeld) {
            this.cranking = false;
        }
        return this.cranking;
    }

    public boolean on() {
        return this.on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }
}
