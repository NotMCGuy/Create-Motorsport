package com.createmotorsport.trailer;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.UUID;

public final class CouplingRules {
    public enum Kind {
        FIFTH_WHEEL, KINGPIN, TOW_BALL, COUPLING_HEAD, LANDING_LEGS, CONTROL_PANEL;
        public boolean provider() { return this == FIFTH_WHEEL || this == TOW_BALL; }
        public boolean hitch() { return ordinal() < 4; }
        public boolean fifth() { return this == FIFTH_WHEEL || this == KINGPIN; }
        public boolean compatible(Kind other) {
            return this == FIFTH_WHEEL && other == KINGPIN || this == KINGPIN && other == FIFTH_WHEEL
                    || this == TOW_BALL && other == COUPLING_HEAD || this == COUPLING_HEAD && other == TOW_BALL;
        }
    }
    public enum Status {
        OPEN, ALIGNING, LOCKED, RELEASING, PENDING, RETRACTED, DEPLOYING, DEPLOYED, RETRACTING,
        DISTANCE, ALIGNMENT, SPEED, WRONG_TYPE, BUSY, INVALID, PERMISSION, UNSUPPORTED, OBSTRUCTED,
        REMOTE_DISABLED, COOLDOWN, JOINT_FAILED, BROKEN, UNLINKED;
        public boolean fault() { return ordinal() >= DISTANCE.ordinal(); }
    }
    private CouplingRules() {}
    public static boolean aligned(Quaterniondc a, Quaterniondc b, double degrees) {
        Vector3d upA = a.transform(new Vector3d(0, 1, 0));
        Vector3d upB = b.transform(new Vector3d(0, 1, 0));
        Vector3d frontA = a.transform(new Vector3d(0, 0, -1));
        Vector3d frontB = b.transform(new Vector3d(0, 0, -1));
        double cosine = Math.cos(Math.toRadians(degrees));
        return upA.dot(upB) >= cosine && frontA.dot(frontB) >= cosine;
    }
    public static boolean near(Vector3dc a, Vector3dc b, double range) {
        double distance = a.distanceSquared(b);
        return Double.isFinite(distance) && distance <= range * range;
    }
    public static boolean speed(double relative, double limit) { return Double.isFinite(relative) && relative <= limit; }
    public static boolean owners(UUID a, UUID b, boolean shared) { return shared || a == null || b == null || a.equals(b); }
    public static Status release(boolean remote, boolean remoteAllowed, boolean valid, double speed,
                                 double relative, double limit, boolean needsSupport, boolean supported, boolean unsafe) {
        if (!valid) return Status.INVALID;
        if (remote && !remoteAllowed) return Status.REMOTE_DISABLED;
        if (!speed(speed, limit) || !speed(relative, limit)) return Status.SPEED;
        if (needsSupport && !supported && !unsafe) return Status.UNSUPPORTED;
        return Status.OPEN;
    }
    public static double progress(double current, double target, double step) {
        return Math.clamp(current + Math.clamp(target - current, -step, step), 0, 1);
    }
    public static boolean reciprocal(UUID own, UUID other, UUID referenceToOwn, UUID referenceToOther) {
        return own != null && other != null && own.equals(referenceToOwn) && other.equals(referenceToOther);
    }
    public static final class Edge {
        private boolean high;
        public boolean sample(boolean value) { boolean rising = value && !high; high = value; return rising; }
        public boolean high() { return high; }
        public void restore(boolean value) { high = value; }
    }
}
