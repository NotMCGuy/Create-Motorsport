package com.createmotorsport.physics.vehicle;

/**
 * Ackermann steering adjustment for the front wheel's steer angle from the centerline,
 *   cot(inner) - cot(outer) = k
 * Called by SuspensionBlockEntity; {@link com.createmotorsport.physics.spec.SuspensionSpec#wheelSteerK} turns the % into k
 */
public final class Ackermann {

    private Ackermann() {
    }

    /**
     * @param base  the centerline angle (rad), positive is right
     * @param k     positive is anti-Ackermann, negative classic, 0 parallel
     * @param right if right/left wheel
     */
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
