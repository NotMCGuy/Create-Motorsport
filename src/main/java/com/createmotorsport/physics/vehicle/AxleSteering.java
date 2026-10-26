package com.createmotorsport.physics.vehicle;

import net.minecraft.util.Mth;

public final class AxleSteering {

    private double signal;
    private double angle;
    private double lastAngle;
    private double lock = 1.0;
    private boolean assistOff;

    public boolean setSignal(double signal) {
        if (Math.abs(signal - this.signal) > 0.05) {
            this.signal = signal;
            return true;
        }
        return false;
    }

    public void chase(double fullLockDeg) {
        this.lastAngle = this.angle;
        this.angle = Mth.lerp(0.4, this.angle, target(fullLockDeg));
    }

    private double target(double fullLockDeg) {
        return this.signal / 15.0 * Math.toRadians(fullLockDeg) * (this.assistOff ? 1.0 : this.lock);
    }

    public double signal() {
        return this.signal;
    }

    public double angle() {
        return this.angle;
    }

    public double lerpedAngle(float partialTicks) {
        return Mth.lerp(partialTicks, this.lastAngle, this.angle);
    }

    public double lock() {
        return this.lock;
    }

    public void setLock(double lock) {
        this.lock = lock;
    }

    public boolean assistOff() {
        return this.assistOff;
    }

    public boolean setAssistOff(boolean off) {
        boolean changed = off != this.assistOff;
        this.assistOff = off;
        return changed;
    }

    public void readSynced(double signal, double lock, boolean assistOff) {
        this.signal = signal;
        this.lock = lock;
        this.assistOff = assistOff;
    }
}
