package com.createmotorsport.physics.vehicle;

import com.createmotorsport.Config;
import com.createmotorsport.physics.MassScale;
import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.control.Abs;
import com.createmotorsport.physics.spec.BrakeSpec;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.spec.SuspensionSpec;
import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.physics.suspension.SpringDamper;
import com.createmotorsport.physics.suspension.SuspensionScale;
import com.createmotorsport.physics.suspension.UnsprungSolver;
import com.createmotorsport.physics.terrain.TerrainAssist;
import com.createmotorsport.physics.tire.TireModel;
import com.createmotorsport.physics.tire.TireModels;
import com.createmotorsport.physics.tire.TireThermal;

import net.minecraft.util.Mth;

public final class Corner {

    public static final double MAX_BODY_MODE_OMEGA_DT = 1.8;
    private static final double UNSPRUNG_MAX_STEP = 0.004;
    private static final double CAMBER_FADE_SPEED = 2.0;

    private Corner() {
    }

    public record Axle(SuspensionSpec setup, double restLength, double maxTravel, double maxDroop,
                       double groundMargin, double brake01, double driveTorque, double driveStiffness,
                       double driveMin, double driveMax, double carMass,
                       int wheelCount, double sprungMassPerWheel, double gravity,
                       TerrainAssist.Level terrainAssist, double carSpeed, double suspensionScale) {

        public Axle withDrive(double torque, double stiffness, double min, double max) {
            return new Axle(setup, restLength, maxTravel, maxDroop, groundMargin, brake01, torque, stiffness, min, max,
                    carMass, wheelCount, sprungMassPerWheel, gravity, terrainAssist, carSpeed, suspensionScale);
        }
    }

    private static final double NO_GROUND_DISTANCE = 5.0;

    public static final class Modes {
        public double bodyOmegaDt;
        public double rollOmegaDt;
    }

    public static double wheelMass(double carMass) {
        return Math.max(0.1, Config.WHEEL_MASS.getAsDouble() * MassScale.measured(carMass));
    }

    public static double wheelMass(double stated, double carMass) {
        return stated > 0.0 ? stated : wheelMass(carMass);
    }

    public static double wheelInertia(double radius, double wheelMass) {
        return Math.max(0.5, wheelMass * radius * radius);
    }

    public static double rollResponseMass(double invCorner, double invMid, int wheels) {
        double invRoll = 2.0 * (invCorner - invMid);
        return invRoll > 1.0e-9 ? 1.0 / (Math.max(1, wheels) * invRoll) : Double.POSITIVE_INFINITY;
    }

    @FunctionalInterface
    public interface BrakeLimiter {
        double limit(double requested, double radius, double inertia, double omega, double driveTorque,
                     double previousForce, double speed, double normalForce, double gripForce, double dt);
    }

    public static CornerOutput step(Axle axle, double camberSign, CornerInput in, CornerState wheel, Modes modes,
                                    double dt, BrakeLimiter brakeLimiter) {
        double radius = in.radius();
        wheel.spinFrom = wheel.omega;
        wheel.spinCoupling = Double.NaN;

        double assistLift = 0.0;
        double distance = NO_GROUND_DISTANCE;
        if (in.hit()) {
            TerrainAssist.Level level = axle.terrainAssist();
            assistLift = TerrainAssist.lift(axle.setup().terrainAssist(), level.enabled(),
                    level == TerrainAssist.Level.EXTRA && axle.setup().terrainAssist().extraEnabled(),
                    radius, in.rigidRaw(), axle.restLength() + axle.groundMargin(), axle.carSpeed(), dt,
                    in.probe(), wheel.assist);
            wheel.telem.assistTarget = wheel.assist.target;
            wheel.telem.assistRise = wheel.assist.rise;
            wheel.telem.assistProbeD = wheel.assist.probeDistance;
            wheel.telem.assistWhy = wheel.assist.why;
            distance = in.rigidRaw() - assistLift + radius;
        }
        wheel.telem.castLift = in.castLift();
        wheel.telem.assistLift = assistLift;
        boolean grounded = distance <= axle.restLength() + radius + axle.groundMargin();
        wheel.grounded = grounded;
        wheel.surface = in.surface();
        wheel.surfaceMu = in.surfaceMu();

        double wheelInertia = wheelInertia(radius, in.wheelMass());
        BrakeSpec brakes = axle.setup().brakes();
        double pedal = axle.brake01();
        wheel.brakePressure = pedal <= wheel.brakePressure || brakes.applyTime() <= 0.0 ? pedal
                : Math.min(pedal, wheel.brakePressure + dt / brakes.applyTime());
        double brakeTorque = wheel.brakePressure * brakes.strength()
                * MassScale.measured(axle.carMass()) * radius;

        if (!grounded) {
            wheel.unsprungVel = Mth.lerp(0.4, wheel.unsprungVel, in.airborneVel());
            wheel.springLength = Mth.clamp(Mth.lerp(0.4, wheel.springLength, axle.restLength() + axle.maxDroop()),
                    axle.restLength() - axle.maxTravel(), axle.restLength() + axle.maxDroop());
            double spun = TireModel.integrateSpin(wheel.omega, radius, wheelInertia, axle.driveTorque(),
                    axle.driveStiffness(), axle.driveMin(), axle.driveMax(), brakeTorque, 0.0, wheel.omega * radius,
                    dt);
            wheel.telem.driveTorque = TireModel.drive(axle.driveTorque(), axle.driveStiffness(), axle.driveMin(),
                    axle.driveMax(), wheel.omega, spun);
            wheel.spinCoupling = brakeTorque > 0.0 && spun == 0.0 ? Double.NaN : 0.0;
            wheel.omega = spun * 0.995;
            wheel.telem.load = 0.0;
            wheel.longForce = 0.0;
            wheel.telem.latForce = 0.0;
            wheel.slipRatio = 0.0;
            wheel.slipAngleRad = 0.0;
            wheel.peakSlipAngleRad = Double.NaN;
            wheel.peakForce = 0.0;
            wheel.telem.slipCombined = 0.0;
            wheel.telem.slipAtPeak = 0.0;
            wheel.telem.curveForce = 0.0;
            wheel.telem.gripUse = 0.0;
            wheel.latUse = 0.0;
            wheel.telem.gripLon = 0.0;
            wheel.telem.gripLat = 0.0;
            wheel.vLon = 0.0;
            wheel.vLat = 0.0;
            wheel.telem.vLat = 0.0;
            wheel.telem.compression = 0.0;
            wheel.telem.brakeTorque = brakeTorque;
            wheel.telem.wheelSpeed = wheel.omega * radius;
            wheel.prevRigidLength = Double.NaN;
            return null;
        }

        double rigidLength = distance - radius;
        wheel.telem.rigidLength = rigidLength;

        double invNormalMass = in.invNormalMass();
        double responseMass = 1.0 / (Math.max(1, axle.wheelCount()) * invNormalMass);
        wheel.telem.effMass = invNormalMass > 0.0 ? 1.0 / invNormalMass : Double.POSITIVE_INFINITY;
        if (invNormalMass <= 1.0e-9) {
            return null;
        }
        double rateMass = axle.sprungMassPerWheel();

        double hardpointVel = in.hardpointVel();
        wheel.telem.sampleHardpointVel(hardpointVel, in.gameTime());
        double diffVel = 0.0;
        if (Double.isFinite(wheel.prevRigidLength) && dt > 0.0) {
            double delta = rigidLength - wheel.prevRigidLength;
            if (Math.abs(delta) <= axle.maxTravel()) {
                diffVel = delta / dt;
            }
        }
        wheel.prevRigidLength = rigidLength;
        wheel.telem.velWorld = hardpointVel;
        wheel.telem.velBody = hardpointVel;
        wheel.telem.velDiff = diffVel;

        double compression;
        double springForce;
        if (Config.TIRE_COMPLIANCE.get()) {
            springForce = stepUnsprung(axle, modes, wheel, rateMass, rigidLength, hardpointVel, responseMass,
                    in.rollResponseMass(), in.tireRate(), in.tireDampingRatio(), in.wheelMass(), dt);
            compression = Math.max(0.0, axle.restLength() - wheel.springLength);
        } else {
            wheel.springLength = Mth.clamp(rigidLength, axle.restLength() - axle.maxTravel(), axle.restLength() + axle.maxDroop());
            wheel.tireDeflection = 0.0;
            wheel.unsprungVel = 0.0;
            wheel.telem.tireRate = 0.0;
            compression = Math.max(0.0, axle.restLength() - wheel.springLength);
            double scale = axle.suspensionScale();
            double frequency = SuspensionScale.frequency(axle.setup().naturalFreqHz(), scale);
            springForce = SpringDamper.suspensionForce(rateMass, frequency,
                    axle.setup().dampingRatio(), compression * scale, hardpointVel * scale,
                    axle.setup().damper(), responseMass, dt);
            double nominalC = SpringDamper.springDamping(rateMass, frequency,
                    axle.setup().dampingRatio(), hardpointVel > 0.0);
            wheel.telem.dampFraction = nominalC > 0.0
                    ? SpringDamper.effectiveDamping(nominalC, hardpointVel * scale, axle.setup().damper(), dt, responseMass)
                            / nominalC
                    : 1.0;
        }

        wheel.telem.hardpointVel = hardpointVel;
        wheel.telem.springForce = springForce;
        wheel.telem.velNormal = in.velNormal();

        if (springForce > 0.0) {
            springForce *= Mth.clamp(1.0 / Math.max(0.5, in.normalY()), 1.0, 2.0);
        }
        double forceCap = Config.MAX_CORNERING_G.getAsDouble() * rateMass * axle.gravity();
        springForce = Mth.clamp(springForce, -forceCap, forceCap);

        double airspeed = in.airspeed();
        double downforce = Config.AERO_DOWNFORCE.getAsDouble() * airspeed * airspeed;


        double normalForce = springForce;
        if (Config.TIRE_COMPLIANCE.get()) {
            normalForce = Math.min(wheel.contactLoad,
                    Config.MAX_CORNERING_G.getAsDouble() * rateMass * axle.gravity());
        }
        double vLon = in.vLon();
        double vLat = in.vLat();


        wheel.telem.lockSlip = Abs.lockSlip(wheel.omega, radius, vLon);

        double absOmega = 0.0;
        if (brakeTorque > 0.0 && brakes.abs() && Math.abs(vLon) > brakes.absMinSpeed()) {
            brakeTorque *= Abs.step(wheel.abs, wheel.omega, radius, vLon, brakes.absSlipThreshold(),
                    brakes.absDumpRate(), brakes.absBuildRate(), brakes.absFloor(), dt);
            absOmega = Abs.holdOmega(radius, vLon, brakes.absSlipThreshold());
        } else {
            wheel.abs.reset();
        }

        TireSpec tireSpec = in.tireSpec();
        float designLoad = in.designLoad();
        double gripMult = TireModel.loadSensitivity(normalForce, designLoad, tireSpec);

        double tempFactor = Config.TIRE_THERMAL_MODEL.get()
                ? TireThermal.gripFactor(wheel.tireTemp, tireSpec.thermal().optimalTempC(),
                        Config.TIRE_AMBIENT_TEMP.getAsDouble(), tireSpec.thermal().coldGripFactor())
                : 1.0;
        wheel.telem.tempFactor = tempFactor;
        SurfaceSpec surface = wheel.surface.scaledBy(tireSpec.surfaces().of(wheel.surface.category()));
        double surfaceMu = wheel.surface == SurfaceSpec.DRY_DEFAULT ? wheel.surfaceMu : surface.peakMu();

        double effectiveMu = surfaceMu * gripMult * tempFactor;
        wheel.telem.gripMult = gripMult;
        wheel.peakMu = effectiveMu * tireSpec.grip();

        boolean powered = Math.max(-axle.driveMin(), axle.driveMax()) > 1.0e-3 || brakeTorque > 1.0e-3;
        double forwardImpulse;
        double sideImpulse;

        double gripForce = normalForce * effectiveMu * tireSpec.grip();

        brakeTorque = brakeLimiter.limit(brakeTorque, radius, wheelInertia, wheel.omega,
                axle.driveTorque(), wheel.prevLongForce, vLon, normalForce, gripForce, dt);
        Tire model = TireModels.of(tireSpec.model());
        wheel.peakSlipAngleRad = model.peakSlipAngle(tireSpec, normalForce, designLoad, surface, effectiveMu);
        Tire.Contact contact = wheel.contact;
        contact.omega = wheel.omega;
        contact.deflAlpha = wheel.deflAlpha;
        contact.prevLongForce = wheel.prevLongForce;
        contact.latBristle = wheel.latBristle;
        contact.lonBristle = wheel.lonBristle;
        contact.latRelax = wheel.latRelax;
        Tire.Forces forces = model.solve(new Tire.Input(
                vLon, vLat, normalForce, effectiveMu, gripForce, radius, dt, powered,
                axle.driveTorque(), axle.driveStiffness(), axle.driveMin(), axle.driveMax(), brakeTorque, absOmega,
                wheelInertia,
                in.invAxleMass(), designLoad,
                axle.setup().camberDeg(), axle.setup().camberStiffness(), CAMBER_FADE_SPEED,
                camberSign,
                tireSpec, surface), contact);
        wheel.telem.driveTorque = powered ? TireModel.drive(axle.driveTorque(), axle.driveStiffness(),
                axle.driveMin(), axle.driveMax(), wheel.omega, contact.omega) : 0.0;
        if (powered && !(brakeTorque > 0.0 && contact.omega == 0.0)) {
            wheel.spinCoupling = TireModel.spinCoupling(contact.curveLongForce, radius, contact.omega, vLon);
        }
        wheel.omega = contact.omega;
        wheel.deflAlpha = contact.deflAlpha;
        wheel.prevLongForce = contact.prevLongForce;
        wheel.latBristle = contact.latBristle;
        wheel.lonBristle = contact.lonBristle;
        wheel.latRelax = contact.latRelax;
        boolean modelSlip = contact.telemModelSlip;
        if (modelSlip) {
            wheel.peakForce = contact.telemPeakForce;
            wheel.telem.slipCombined = contact.telemSlipCombined;
            wheel.telem.slipAtPeak = contact.telemSlipAtPeak;
            wheel.telem.curveForce = contact.telemCurveForce;
            wheel.slipRatio = contact.telemSlipRatio;
            wheel.slipAngleRad = contact.telemSlipAngleRad;
        } else {
            wheel.peakForce = 0.0;
            wheel.telem.slipCombined = 0.0;
            wheel.telem.slipAtPeak = 0.0;
            wheel.telem.curveForce = 0.0;
        }
        contact.telemModelSlip = false;
        forwardImpulse = forces.forward();
        sideImpulse = forces.side();



        double gripCircle = gripForce * dt;
        double demand = Math.hypot(forwardImpulse, sideImpulse);
        wheel.telem.gripUse = gripCircle > 1.0e-9 ? demand / gripCircle : 0.0;
        wheel.telem.gripLon = gripCircle > 1.0e-9 ? forwardImpulse / gripCircle : 0.0;
        wheel.telem.gripLat = gripCircle > 1.0e-9 ? sideImpulse / gripCircle : 0.0;
        wheel.latUse = gripCircle > 1.0e-9 ? Math.abs(sideImpulse) / gripCircle : 0.0;

        wheel.telem.load = normalForce;
        wheel.vLon = vLon;
        wheel.vLat = vLat;
        wheel.telem.vLat = vLat;
        if (!modelSlip) {
            wheel.slipRatio = (wheel.omega * radius - vLon) / Math.max(Math.abs(vLon), 2.0);
            wheel.slipAngleRad = Math.atan2(vLat, Math.max(Math.abs(vLon), 0.05));
        }
        wheel.longForce = forwardImpulse / dt;
        wheel.telem.latForce = sideImpulse / dt;
        wheel.telem.wheelSpeed = wheel.omega * radius;
        wheel.telem.compression = compression;
        wheel.telem.brakeTorque = brakeTorque;

        if (Config.TIRE_THERMAL_MODEL.get()) {
            double ambient = Config.TIRE_AMBIENT_TEMP.getAsDouble();
            if (!wheel.thermalStarted) {
                wheel.tireTemp = TireThermal.startTemp(ambient);
                wheel.thermalStarted = true;
            }
            double work = TireThermal.frictionWork(forwardImpulse / dt, sideImpulse / dt,
                    wheel.omega * radius - vLon, vLat, dt);
            wheel.tireTemp = TireThermal.step(wheel.tireTemp, work, vLon, ambient,
                    tireSpec.thermal().heatingRate(), tireSpec.thermal().coolingRate(), dt);
        }

        return new CornerOutput(springForce, downforce, forwardImpulse, sideImpulse);
    }

    private static double stepUnsprung(Axle axle, Modes modes, CornerState wheel, double rateMass, double rigidLength,
                                double hardpointVel, double responseMass, double rollResponseMass,
                                double tireRate, double tireDampingRatio, double wheelMass, double dt) {
        UnsprungSolver.Input in = new UnsprungSolver.Input(
                rateMass, rigidLength, hardpointVel, responseMass, rollResponseMass, dt,
                axle.restLength(), axle.maxTravel(), axle.maxDroop(),
                wheelMass, axle.gravity(),
                axle.setup().naturalFreqHz(), axle.setup().dampingRatio(), axle.setup().damper(),
                tireRate, tireDampingRatio,
                MAX_BODY_MODE_OMEGA_DT, UNSPRUNG_MAX_STEP);

        UnsprungSolver.State s = wheel.unsprung;
        s.springLength = wheel.springLength;
        s.unsprungVel = wheel.unsprungVel;
        double force = SuspensionScale.solve(in, s, axle.suspensionScale());
        wheel.springLength = s.springLength;
        wheel.unsprungVel = s.unsprungVel;
        wheel.tireDeflection = s.tireDeflection;
        wheel.telem.rigidLength = s.groundLength;
        wheel.contactLoad = s.contactLoad;
        wheel.telem.dampFraction = s.dampFraction;
        modes.bodyOmegaDt = s.bodyModeOmegaDt;
        modes.rollOmegaDt = s.rollModeOmegaDt;
        wheel.telem.tireRate = in.tireRate();
        return force;
    }
}
