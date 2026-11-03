package com.createmotorsport.physics.drivetrain;

import com.createmotorsport.Config;
import com.createmotorsport.physics.MassScale;
import com.createmotorsport.physics.Starter;
import com.createmotorsport.physics.starter.ElectricStarter;

import com.createmotorsport.physics.spec.ClutchSpec;
import com.createmotorsport.physics.spec.EngineSpec;
import com.createmotorsport.physics.spec.GearboxSpec;
import net.minecraft.util.Mth;

public final class DrivetrainSim {
    private static final double RAD_TO_RPM = 60.0 / (2.0 * Math.PI);

    public static final int GEAR_REVERSE = 0;
    public static final int GEAR_NEUTRAL = 1;
    public static final int GEAR_FIRST = 2;

    private static final double IDLE_GAIN = 1.5;

    private EngineSpec spec;
    private GearboxSpec gearbox;

    private double rpm;
    private int gear = GEAR_NEUTRAL;
    private double clutchEngage = 0.0; // 0 = disengaged
    private double shiftReleaseTimer = 0.0;

    // For csv logging, last computed value
    private double lastEngineTorque;
    private double lastRatio;
    private double lastWheelTorque;
    private boolean lastClutchLocked;


    public void setSpec(EngineSpec spec) {
        this.spec = spec;
    }

    public void setGearbox(GearboxSpec gearbox) {
        this.gearbox = gearbox;
        this.gear = Mth.clamp(this.gear, GEAR_REVERSE, maxGear());
    }

    public DrivetrainSim(EngineSpec spec, GearboxSpec gearbox) {
        this.spec = spec;
        this.gearbox = gearbox;
        this.rpm = spec.idleRpm();
    }

    private int maxGear() {
        return GEAR_FIRST + this.gearbox.gearCount() - 1;
    }

    /**
     * The clutch's hold on the wheels for one substep, before the caller's torque scale
     *
     * @param stiffness Nm less per rad/s the gearbox gains
     * @param limit the most the coupling passes either direction before it slips
     */
    public record Coupling(double torque, double stiffness, double limit) {
        public static final Coupling NONE = new Coupling(0.0, 0.0, 0.0);
    }

    private boolean firing = true;
    private boolean starterEngaged;
    private Starter starter;
    private EngineSpec starterFor;
    private boolean disengaged = true;
    private boolean released = true;
    private boolean blipping;
    private double blipTimer;
    private static final double BLIP_TIMEOUT = 0.25;
    private double effThrottle;
    private double clutchCap;
    private boolean pending;
    private double pendingOmega;
    private double pendingGearbox;
    private double pendingRatio;
    private double pendingEngineTorque;
    private double pendingTorque;
    private double pendingStiffness;
    private double pendingDt;

    public void tick(boolean ignition, boolean fuel, boolean starter, double throttle, boolean clutchHeld,
                     boolean shiftUpEdge, boolean shiftDownEdge, double wheelOmega, double dt) {
        ClutchSpec clutch = this.gearbox.clutch();
        boolean semiAuto = this.gearbox.semiAuto();
        double engineScale = MassScale.design(this.spec.designVehicleMassBlocks());

        this.firing = ignition && fuel && this.rpm >= this.spec.stallRpmOrDerived();
        this.starterEngaged = starter;

        double interrupt = this.gearbox.shiftInterruptTime();
        boolean canShift = semiAuto || clutchHeld;
        if (canShift) {
            if (shiftUpEdge) {
                this.gear = Math.min(maxGear(), this.gear + 1);
                if (semiAuto) this.shiftReleaseTimer = interrupt;
            } else if (shiftDownEdge) {
                this.gear = Math.max(GEAR_REVERSE, this.gear - 1);
                if (semiAuto) this.shiftReleaseTimer = interrupt;
                if (semiAuto && this.gearbox.revMatch() && this.gear >= 1) {
                    this.blipping = true;
                    this.blipTimer = BLIP_TIMEOUT;
                }
            }
        }
        if (this.blipping) {
            this.blipTimer -= dt;
            if (this.blipTimer <= 0.0) {
                this.blipping = false;
            }
        }
        if (this.shiftReleaseTimer > 0.0) {
            this.shiftReleaseTimer = Math.max(0.0, this.shiftReleaseTimer - dt);
        }
        boolean shiftDip = this.shiftReleaseTimer > 0.0;

        double ratio = overallRatio(this.gear);
        boolean disengaged = clutchHeld || shiftDip || this.gear == GEAR_NEUTRAL;
        this.released = disengaged;
        this.disengaged = disengaged || this.blipping;

        double idleErr = (this.spec.idleRpm() - this.rpm) / this.spec.idleRpm();
        double idleThrottle = Mth.clamp(idleHoldThrottle() + idleErr * IDLE_GAIN, 0.0, 0.6);
        double driverThrottle = shiftDip ? 0.0 : throttle;
        this.effThrottle = this.firing ? Math.max(driverThrottle, idleThrottle) : driverThrottle;

        double tEng = netEngineTorque(this.effThrottle, this.rpm / RAD_TO_RPM);
        double absRatio = Math.abs(ratio);

        double rpmFromWheels = Math.abs(wheelOmega) * absRatio * RAD_TO_RPM;
        double idleRpm = this.spec.idleRpm();
        double moving = Mth.clamp((rpmFromWheels - idleRpm) / (idleRpm * 0.25), 0.0, 1.0);
        double aboveIdle = Mth.clamp((this.rpm - idleRpm) / (idleRpm * 0.2), 0.0, 1.0);
        double launchRpm = Math.max(idleRpm, clutch.launchRpm());
        double lockPoint = idleRpm * 1.25;
        double launchBlend = Mth.clamp(rpmFromWheels / lockPoint, 0.0, 1.0);
        double launchFloor = Mth.lerp(launchBlend, launchRpm, rpmFromWheels);
        double launchTarget = Math.max(rpmFromWheels, launchFloor);
        double over = Mth.clamp((this.rpm - launchTarget) / clutch.flareBand(), 0.0, 1.0);
        double grabFrac = clutch.launchGrab() + (clutch.holdGrab() - clutch.launchGrab()) * over;

        double launchGrab = disengaged ? 0.0
                : Mth.clamp(grabFrac * tEng / (clutch.maxTorque() * engineScale), 0.0, 1.0) * aboveIdle;

        double overspeed = disengaged ? 0.0
                : Mth.clamp((this.rpm - launchTarget - clutch.flareBand()) / clutch.dumpBand(), 0.0, 1.0);

        double creep = disengaged ? 0.0
                : clutch.idleCreep() * Mth.clamp((this.rpm - idleRpm * 0.9) / (idleRpm * 0.1), 0.0, 1.0);

        double engageDemand = disengaged ? 0.0
                : Math.max(Math.max(moving, launchGrab), Math.max(overspeed, creep));

        double engageUp = dt / clutch.engageTime();
        double engageDown = engageUp * 3.0;
        if (engageDemand > this.clutchEngage) {
            this.clutchEngage = Math.min(engageDemand, this.clutchEngage + engageUp);
        } else {
            this.clutchEngage = Math.max(engageDemand, this.clutchEngage - engageDown);
        }

        this.clutchCap = (clutch.maxTorque() * engineScale) * this.clutchEngage;
    }

    /**
     * The wheel step solves the Coupling with its tyre and brake, so engine, clutch and wheels step together implicitly
     *
     * @param wheelOmega  average driven-wheel spin (rad/s) at the substep's start
     * @param minThrottle from engine drag control
     */
    public Coupling couple(double wheelOmega, double dt, double minThrottle) {
        settle(wheelOmega);
        double inertia = this.spec.engineInertia();
        double omega = this.rpm / RAD_TO_RPM;
        double maxOmega = this.spec.redlineRpm() * 1.05 / RAD_TO_RPM;
        double cranking = this.starterEngaged ? starter().torque(omega) : 0.0;
        double tEng = netEngineTorque(this.effThrottle, omega) + cranking;
        double ratio = overallRatio(this.gear);

        if (this.blipping && ratio != 0.0) {
            double target = Math.abs(wheelOmega * ratio);
            if (omega >= target) {
                this.blipping = false;
                this.disengaged = this.released;
            } else {
                tEng = Mth.clamp(inertia * (target - omega) / dt, netEngineTorque(0.0, omega),
                        netEngineTorque(1.0, omega));
            }
        }

        if (this.disengaged || ratio == 0.0) {
            this.rpm = clampOmega(omega + dt / inertia * tEng, maxOmega) * RAD_TO_RPM;
            record(tEng, ratio, 0.0, false);
            return Coupling.NONE;
        }

        tEng = netEngineTorque(Math.max(this.effThrottle, minThrottle), omega) + cranking;

        // The engine's half of the implicit step, with the gearbox's change left as the unknown the wheels solve
        double kc = this.gearbox.clutch().lockStiffness();
        double omegaGb = wheelOmega * ratio;
        double stiffness = kc / (1.0 + dt * kc / inertia);
        double torque = stiffness * (omega - omegaGb + dt / inertia * tEng);

        this.pending = true;
        this.pendingOmega = omega;
        this.pendingGearbox = omegaGb;
        this.pendingRatio = ratio;
        this.pendingEngineTorque = tEng;
        this.pendingTorque = torque;
        this.pendingStiffness = stiffness;
        this.pendingDt = dt;

        double eta = this.spec.drivelineEfficiency();
        return new Coupling(torque * ratio * eta, stiffness * ratio * ratio * eta,
                this.clutchCap * Math.abs(ratio) * eta);
    }

    public void settle(double wheelOmega) {
        if (!this.pending) {
            return;
        }
        this.pending = false;
        double torque = this.pendingTorque
                - this.pendingStiffness * (wheelOmega * this.pendingRatio - this.pendingGearbox);
        boolean locked = Math.abs(torque) <= this.clutchCap;
        torque = Mth.clamp(torque, -this.clutchCap, this.clutchCap);
        double omega = this.pendingOmega
                + this.pendingDt / this.spec.engineInertia() * (this.pendingEngineTorque - torque);
        this.rpm = clampOmega(omega, this.spec.redlineRpm() * 1.05 / RAD_TO_RPM) * RAD_TO_RPM;
        record(this.pendingEngineTorque, this.pendingRatio,
                torque * this.pendingRatio * this.spec.drivelineEfficiency(), locked);
    }

    private static double clampOmega(double omega, double maxOmega) {
        return Math.min(Math.max(0.0, omega), maxOmega);
    }


    private double idleHoldThrottle() {
        double idleTorque = this.spec.torqueAt(this.spec.idleRpm());
        if (idleTorque <= 1.0e-6) {
            return 0.1;
        }
        double idleFriction = frictionTorque(this.spec.idleRpm(), 0.0);
        return Mth.clamp(idleFriction / idleTorque, 0.0, 0.6);
    }

    private double netEngineTorque(double effThrottle, double omega) {
        double rpmNow = Math.abs(omega) * RAD_TO_RPM;
        double combustion = !this.firing || rpmNow >= this.spec.redlineRpm()
                ? 0.0
                : effThrottle * this.spec.torqueAt(rpmNow);
        double sign = omega >= 0.0 ? 1.0 : -1.0;
        return combustion - sign * frictionTorque(rpmNow, effThrottle);
    }

    private Starter starter() {
        if (this.starter == null || this.starterFor != this.spec) {
            double stall = this.spec.starter().stallTorque() > 0.0 ? this.spec.starter().stallTorque()
                    : 2.5 * frictionTorque(0.0, 0.0);
            double free = this.spec.starter().freeRpm() > 0.0 ? this.spec.starter().freeRpm() : this.spec.idleRpm();
            this.starter = new ElectricStarter(stall, free / RAD_TO_RPM);
            this.starterFor = this.spec;
        }
        return this.starter;
    }

    private double frictionTorque(double rpm, double effThrottle) {
        double base = this.spec.engineBrakeFraction() * this.spec.peakTorque();
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
    private double overallRatio(int gearIndex) {
        double finalDrive = this.gearbox.finalDriveOr(this.spec.finalDrive());
        if (gearIndex == GEAR_REVERSE) {
            return -this.gearbox.reverseRatio() * finalDrive;
        }
        if (gearIndex == GEAR_NEUTRAL) {
            return 0.0;
        }
        return this.gearbox.forwardRatio(gearIndex - GEAR_FIRST) * finalDrive;
    }

    public boolean firing() {
        return this.firing;
    }

    public double starterTorque() {
        return this.starterEngaged ? starter().torque(this.rpm / RAD_TO_RPM) : 0.0;
    }

    public double getRpm() {
        return this.rpm;
    }

    // combustion plus the pumping loss throttle takes away. Cruise control divides its gains by this
    public double wheelTorquePerThrottle() {
        double n = Mth.clamp(Math.abs(this.rpm) / this.spec.redlineRpm(), 0.0, 1.2);
        double pumping = this.spec.engineBrakeFraction() * this.spec.peakTorque() * (0.35 + 1.40 * n * n);
        return (this.spec.torqueAt(Math.abs(this.rpm)) + pumping) * Math.abs(overallRatio(this.gear))
                * this.spec.drivelineEfficiency();
    }

    public double torqueScale(double powerFactor) {
        return this.spec.drivetrainTorqueScale() * Config.DRIVETRAIN_TRIM.getAsDouble() * powerFactor;
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

    // menu sync code, 0=R, 1=N, 2=1st gear
    public int gearCode() {
        return this.gear;
    }

    public EngineSpec spec() {
        return this.spec;
    }

    public GearboxSpec gearbox() {
        return this.gearbox;
    }

    public void restore(double rpm, int gear) {
        this.pending = false;
        this.rpm = rpm;
        this.gear = Mth.clamp(gear, GEAR_REVERSE, maxGear());
        this.firing = rpm >= this.spec.stallRpmOrDerived();
    }
}
