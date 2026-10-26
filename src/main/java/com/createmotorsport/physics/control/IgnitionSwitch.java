package com.createmotorsport.physics.control;

public final class IgnitionSwitch {

    private boolean on = true;
    private boolean held;
    private boolean cranking;

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
