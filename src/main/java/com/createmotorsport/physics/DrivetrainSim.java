package com.createmotorsport.physics;

import com.createmotorsport.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

// Updated torque and clutch math ported from Vdrift mostly (it was Speed Dreams based before)
// The original work was mostly a placeholder, this is much better as this now
// simulates the engines flywheel and has proper clutch grab behavior

public final class DrivetrainSim {
    private static final double RAD_TO_RPM = 60.0 / (2.0 * Math.PI);

    public static final int GEAR_REVERSE = 0;
    public static final int GEAR_NEUTRAL = 1;
    public static final int GEAR_FIRST = 2;

    private static final double IDLE_GAIN = 1.5;

    private static final double CLUTCH_ENGAGE_TIME = 0.15;

    private static final double CLUTCH_LAUNCH_GRAB = 0.75;
    private static final double CLUTCH_HOLD_GRAB = 1.30;
    private static final double CLUTCH_FLARE_BAND = 2500.0;
    private static final double CLUTCH_IDLE_CREEP = 0.10;
    private static final double CLUTCH_DUMP_BAND = 4000.0;

    private final EngineSpec spec;

    private double rpm;
    private int gear = GEAR_NEUTRAL;
    private double clutchEngage = 0.0; // 0 = disengaged
    private double shiftReleaseTimer = 0.0; // seconds left in the semi-auto clutch dip during a shift

    // last-computed values, for csv logging
    private double lastEngineTorque;
    private double lastRatio;
    private double lastWheelTorque;
    private boolean lastClutchLocked;

    public DrivetrainSim(EngineSpec spec) {
        this.spec = spec;
        this.rpm = spec.idleRpm();
    }

    private int maxGear() {
        return GEAR_FIRST + Config.gearRatios().length - 1;
    }

    /**
     * Advances the drivetrain by 1 tick
     * @param running       engine has fuel and is on
     * @param throttle      0-1, from pedal
     * @param clutchHeld    true while the clutch pedal (signal) is held, true is a disengaged clutch
     * @param semiAuto      true for F1-style paddle shifting: shift without the clutch, the box blips it
     * @param shiftUpEdge   rising edge of the shift-up signal this tick
     * @param shiftDownEdge rising edge of the shift-down signal this tick
     * @param wheelOmega    average driven-wheel angular velocity (rad/s), positive rolling forward
     * @param dt            tick length (s)
     * @return torque delivered to the driven wheels in newton-meters (Nm), signed (negative in reverse)
     */
    public double update(boolean running, double throttle, boolean clutchHeld, boolean semiAuto,
                         boolean shiftUpEdge, boolean shiftDownEdge, double wheelOmega, double dt) {
        double engineScale = MassScale.design(this.spec.designVehicleMassBlocks());
        double inertia = Config.ENGINE_INERTIA.getAsDouble() * engineScale;
        double omega = this.rpm / RAD_TO_RPM;
        double maxOmega = this.spec.redlineRpm() * 1.05 / RAD_TO_RPM;

        if (!running) {
            this.rpm = Math.max(0.0, this.rpm - this.spec.redlineRpm() * 2.0 * dt);
            this.clutchEngage = 0.0;
            return record(0.0, 0.0, 0.0, false);
        }

        double interrupt = Config.SHIFT_INTERRUPT_TIME.getAsDouble();
        boolean canShift = semiAuto || clutchHeld;
        if (canShift) {
            if (shiftUpEdge) {
                this.gear = Math.min(maxGear(), this.gear + 1);
                if (semiAuto) this.shiftReleaseTimer = interrupt;
            } else if (shiftDownEdge) {
                this.gear = Math.max(GEAR_REVERSE, this.gear - 1);
                if (semiAuto) this.shiftReleaseTimer = interrupt;
            }
        }
        if (this.shiftReleaseTimer > 0.0) {
            this.shiftReleaseTimer = Math.max(0.0, this.shiftReleaseTimer - dt);
        }
        boolean shiftDip = this.shiftReleaseTimer > 0.0;

        double ratio = overallRatio(this.gear);
        boolean disengaged = clutchHeld || shiftDip || this.gear == GEAR_NEUTRAL;

        double idleErr = (this.spec.idleRpm() - this.rpm) / this.spec.idleRpm();
        double idleThrottle = Mth.clamp(idleHoldThrottle() + idleErr * IDLE_GAIN, 0.0, 0.6);
        double driverThrottle = shiftDip ? 0.0 : throttle;
        double effThrottle = Math.max(driverThrottle, idleThrottle);

        double tEng = netEngineTorque(effThrottle, omega);

        double absRatio = Math.abs(ratio);
        double omegaGb = wheelOmega * ratio;
        double rpmFromWheels = Math.abs(wheelOmega) * absRatio * RAD_TO_RPM;

        double idleRpm = this.spec.idleRpm();
        double moving = Mth.clamp((rpmFromWheels - idleRpm) / (idleRpm * 0.25), 0.0, 1.0);
        double aboveIdle = Mth.clamp((this.rpm - idleRpm) / (idleRpm * 0.2), 0.0, 1.0);
        double launchRpm = Math.max(idleRpm, Config.LAUNCH_RPM.getAsDouble());
        double lockPoint = idleRpm * 1.25;
        double launchBlend = Mth.clamp(rpmFromWheels / lockPoint, 0.0, 1.0);
        double launchFloor = Mth.lerp(launchBlend, launchRpm, rpmFromWheels);
        double launchTarget = Math.max(rpmFromWheels, launchFloor);
        double over = Mth.clamp((this.rpm - launchTarget) / CLUTCH_FLARE_BAND, 0.0, 1.0);
        double grabFrac = CLUTCH_LAUNCH_GRAB + (CLUTCH_HOLD_GRAB - CLUTCH_LAUNCH_GRAB) * over;

        double launchGrab = disengaged ? 0.0
                : Mth.clamp(grabFrac * tEng / (Config.CLUTCH_MAX_TORQUE.getAsDouble() * engineScale), 0.0, 1.0) * aboveIdle;

        double overspeed = disengaged ? 0.0
                : Mth.clamp((this.rpm - launchTarget - CLUTCH_FLARE_BAND) / CLUTCH_DUMP_BAND, 0.0, 1.0);

        double creep = disengaged ? 0.0
                : CLUTCH_IDLE_CREEP * Mth.clamp((this.rpm - idleRpm * 0.9) / (idleRpm * 0.1), 0.0, 1.0);

        double engageDemand = disengaged ? 0.0
                : Math.max(Math.max(moving, launchGrab), Math.max(overspeed, creep));

        double engageUp = dt / CLUTCH_ENGAGE_TIME;
        double engageDown = engageUp * 3.0;
        if (engageDemand > this.clutchEngage) {
            this.clutchEngage = Math.min(engageDemand, this.clutchEngage + engageUp);
        } else {
            this.clutchEngage = Math.max(engageDemand, this.clutchEngage - engageDown);
        }

        if (disengaged) {
            omega = clampOmega(omega + dt / inertia * tEng, maxOmega);
            this.rpm = omega * RAD_TO_RPM;
            return record(tEng, ratio, 0.0, false);
        }

        double tCap = (Config.CLUTCH_MAX_TORQUE.getAsDouble() * engineScale) * this.clutchEngage;
        double kc = Config.CLUTCH_LOCK_STIFFNESS.getAsDouble();

        double omegaStick = (omega + dt / inertia * (tEng + kc * omegaGb)) / (1.0 + dt * kc / inertia);
        double tClutch = kc * (omegaStick - omegaGb);
        boolean locked;
        double omegaNew;
        if (Math.abs(tClutch) <= tCap) {
            locked = true;
            omegaNew = omegaStick;
        } else {
            locked = false;
            tClutch = Math.copySign(tCap, tClutch);
            omegaNew = omega + dt / inertia * (tEng - tClutch);
        }

        this.rpm = clampOmega(omegaNew, maxOmega) * RAD_TO_RPM;

        double wheelTorque = tClutch * ratio * Config.DRIVELINE_EFFICIENCY.getAsDouble();
        return record(tEng, ratio, wheelTorque, locked);
    }

    private static double clampOmega(double omega, double maxOmega) {
        return Math.min(Math.max(0.0, omega), maxOmega);
    }


    private double idleHoldThrottle() {
        double scale = Config.ENGINE_PEAK_TORQUE.getAsDouble() / this.spec.peakTorque();
        double idleTorque = this.spec.torqueAt(this.spec.idleRpm()) * scale;
        if (idleTorque <= 1.0e-6) {
            return 0.1;
        }
        double idleFriction = frictionTorque(this.spec.idleRpm(), 0.0);
        return Mth.clamp(idleFriction / idleTorque, 0.0, 0.6);
    }

    private double netEngineTorque(double effThrottle, double omega) {
        double rpmNow = Math.abs(omega) * RAD_TO_RPM;
        double scale = Config.ENGINE_PEAK_TORQUE.getAsDouble() / this.spec.peakTorque();
        double combustion = rpmNow >= this.spec.redlineRpm()
                ? 0.0
                : effThrottle * this.spec.torqueAt(rpmNow) * scale;
        double sign = omega >= 0.0 ? 1.0 : -1.0;
        return combustion - sign * frictionTorque(rpmNow, effThrottle);
    }

    private double frictionTorque(double rpm, double effThrottle) {
        double base = Config.ENGINE_BRAKE_FRACTION.getAsDouble() * Config.ENGINE_PEAK_TORQUE.getAsDouble();
        double n = Mth.clamp(Math.abs(rpm) / this.spec.redlineRpm(), 0.0, 1.2);
        double mechanical = base * (0.25 + 0.45 * n);
        double pumping = base * (0.35 + 1.40 * n * n) * (1.0 - Mth.clamp(effThrottle, 0.0, 1.0));
        return mechanical + pumping;
    }

    private double record(double engineTorque, double ratio, double wheelTorque, boolean clutchLocked) {
        this.lastEngineTorque = engineTorque;
        this.lastRatio = ratio;
        this.lastWheelTorque = wheelTorque;
        this.lastClutchLocked = clutchLocked;
        return wheelTorque;
    }

    public double lastEngineTorque() {
        return this.lastEngineTorque;
    }

    public double lastRatio() {
        return this.lastRatio;
    }

    public double lastWheelTorque() {
        return this.lastWheelTorque;
    }

    public boolean lastClutchLocked() {
        return this.lastClutchLocked;
    }

    // Signed overall ratio from crank -> wheel for gear index; 0 for neutral, negative for reverse
    // Comes from the config now
    private double overallRatio(int gearIndex) {
        double finalDrive = Config.FINAL_DRIVE.getAsDouble();
        if (gearIndex == GEAR_REVERSE) {
            return -Config.REVERSE_RATIO.getAsDouble() * finalDrive;
        }
        if (gearIndex == GEAR_NEUTRAL) {
            return 0.0;
        }
        double[] ratios = Config.gearRatios();
        int i = Mth.clamp(gearIndex - GEAR_FIRST, 0, ratios.length - 1);
        return ratios[i] * finalDrive;
    }

    public double getRpm() {
        return this.rpm;
    }

    public double getRpmFraction() {
        return Mth.clamp(this.rpm / this.spec.redlineRpm(), 0.0, 1.0);
    }

    public int getGearIndex() {
        return this.gear;
    }

    public String gearDisplay() {
        return switch (this.gear) {
            case GEAR_REVERSE -> "R";
            case GEAR_NEUTRAL -> "N";
            default -> String.valueOf(this.gear - GEAR_NEUTRAL);
        };
    }

    // menu-sync code: 0 = R, 1=N, 2= 1st gear, etc
    public int gearCode() {
        return this.gear;
    }

    public EngineSpec spec() {
        return this.spec;
    }

    public void save(CompoundTag tag) {
        tag.putDouble("Rpm", this.rpm);
        tag.putInt("Gear", this.gear);
    }

    public void load(CompoundTag tag) {
        this.rpm = tag.getDouble("Rpm");
        this.gear = Mth.clamp(tag.getInt("Gear"), GEAR_REVERSE, maxGear());
    }
}
