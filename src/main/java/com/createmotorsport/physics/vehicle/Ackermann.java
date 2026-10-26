package com.createmotorsport.physics.vehicle;

public final class Ackermann {

    private Ackermann() {
    }

    public static double wheelAngle(double base, double k, boolean right) {
        if (k == 0.0 || base == 0.0) {
            return base;
        }
        boolean rightIsOuter = base > 0.0;
        boolean isOuter = right == rightIsOuter;
        boolean larger = (k > 0.0) == isOuter;
        double half = 0.5 * Math.abs(k);
        double t = Math.tan(Math.abs(base));
        double denom = Math.max(0.3, larger ? 1.0 - half * t : 1.0 + half * t);
        return Math.copySign(Math.atan(t / denom), base);
    }
}
