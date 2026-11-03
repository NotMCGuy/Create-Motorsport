package com.createmotorsport.physics.control;

import net.minecraft.util.Mth;

/**
 * Power mode lets user set torque at mode/max, based on F1 power modes
 * Also covers the overtake boost
 * EngineBlockEntity steps this every tick and multiplies the torque scale by the return
 */
public final class PowerMode {

    public static final int MAX = 8;

    private static final double BOOST_FACTOR = 1.25;
    private static final double BOOST_DRAIN = 1.0 / (20 * 8); // 20 tick * 8 seconds
    private static final double BOOST_RECHARGE = 1.0 / (20 * 20); // 20 tick * 20 seconds

    private int mode = MAX;
    private double reserve = 1.0;
    private boolean boosting;
    private double factor = 1.0;

    public void up() {
        mode = Math.min(MAX, mode + 1);
    }

    public void down() {
        mode = Math.max(1, mode - 1);
    }

    public int mode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = Mth.clamp(mode, 1, MAX);
    }

    public double step(boolean wantBoost) {
        double next = mode / (double) MAX;
        boosting = wantBoost && reserve > 0.0;
        if (boosting) {
            next *= BOOST_FACTOR;
            reserve = Math.max(0.0, reserve - BOOST_DRAIN);
        } else if (!wantBoost) {
            reserve = Math.min(1.0, reserve + BOOST_RECHARGE);
        }
        factor = next;
        return next;
    }

    public double reserve() {
        return reserve;
    }

    public boolean boosting() {
        return boosting;
    }

    public double factor() {
        return factor;
    } // for logging
}
