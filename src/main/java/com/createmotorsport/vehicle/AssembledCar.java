package com.createmotorsport.vehicle;

import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.physics.control.DragControl;
import com.createmotorsport.physics.control.TractionControl;
import com.createmotorsport.physics.differential.DriveSplit;
import com.createmotorsport.physics.drivetrain.DrivetrainSim;
import com.createmotorsport.physics.spec.DriveInputSpec;
import com.createmotorsport.physics.spec.TractionControlSpec;
import com.createmotorsport.physics.vehicle.AxleDrive;
import com.createmotorsport.physics.vehicle.CarGeometry;
import com.createmotorsport.physics.world.BodyDrag;
import com.createmotorsport.physics.vehicle.CarSpec;
import com.createmotorsport.physics.vehicle.CarState;
import com.createmotorsport.physics.vehicle.CornerState;
import com.createmotorsport.physics.vehicle.Driveline;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AssembledCar {


    private static final double TICK_SECONDS = 1.0 / 20.0;

    private record Drive(EngineBlockEntity engine, Driveline line, int[] sign) {
    }


    private List<SubLevel> bodies = List.of();
    private List<EngineBlockEntity> engines = List.of();
    private List<SuspensionBlockEntity> suspensions = List.of();
    private List<SteeringWheelBlockEntity> steeringWheels = List.of();
    private Map<SuspensionBlockEntity, SubLevel> bodyOf = Map.of();
    long builtGeneration = Long.MIN_VALUE;

    private final CarState state = new CarState();
    private long steppedGameTime = Long.MIN_VALUE;

    private List<SuspensionBlockEntity> driveAxles = List.of();
    @Nullable
    private CarSpec driveSpec;
    private List<Drive> drives = List.of();
    private boolean substepped;
    private List<SuspensionBlockEntity> drivenAxles = List.of();
    private double stepDemand;
    private final List<SuspensionBlockEntity> splitAxles = new ArrayList<>();
    private final List<DriveSplit.Wheel> splitWheels = new ArrayList<>();
    private double splitMean;

    private final Set<SuspensionBlockEntity> queuedForces = new LinkedHashSet<>();
    private final ForceTotal dragForces = new ForceTotal();


    AssembledCar() {
    }


    void rebuild(Level level, SubLevel root, long generation) {
        List<SubLevel> foundBodies = new ArrayList<>();
        List<EngineBlockEntity> foundEngines = new ArrayList<>();
        List<SuspensionBlockEntity> foundSuspensions = new ArrayList<>();
        List<SteeringWheelBlockEntity> foundWheels = new ArrayList<>();
        Map<SuspensionBlockEntity, SubLevel> foundBodyOf = new IdentityHashMap<>();

        Set<SubLevel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<SubLevel> queue = new ArrayDeque<>();
        queue.add(root);
        seen.add(root);
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        while (!queue.isEmpty()) {
            SubLevel body = queue.poll();
            foundBodies.add(body);
            for (var actor : body.getPlot().getBlockEntityActors()) {
                if (actor instanceof EngineBlockEntity e) {
                    foundEngines.add(e);
                } else if (actor instanceof SuspensionBlockEntity s && !s.isRemoved()) {
                    foundSuspensions.add(s);
                    foundBodyOf.put(s, body);
                } else if (actor instanceof SteeringWheelBlockEntity w && !w.isRemoved()) {
                    foundWheels.add(w);
                }
                if (actor instanceof SubLevelBridge bridge && container != null) {
                    for (UUID id : bridge.bridgedSubLevels()) {
                        SubLevel other = container.getSubLevel(id);
                        if (other != null && !other.isRemoved() && seen.add(other)) {
                            queue.add(other);
                        }
                    }
                }
            }
        }

        this.bodies = List.copyOf(foundBodies);
        this.engines = List.copyOf(foundEngines);
        this.suspensions = List.copyOf(foundSuspensions);
        this.steeringWheels = List.copyOf(foundWheels);
        this.bodyOf = foundBodyOf;
        this.builtGeneration = generation;

        Set<Long> axleKeys = new HashSet<>();
        for (SuspensionBlockEntity s : foundSuspensions) {
            axleKeys.add(s.getBlockPos().asLong());
        }
        this.state.retainAxles(axleKeys);
    }

    public boolean steps(EngineBlockEntity engine) {
        return this.engines.contains(engine);
    }

    public boolean steps(SuspensionBlockEntity suspension) {
        return this.suspensions.contains(suspension);
    }

    public boolean steps(SteeringWheelBlockEntity wheel) {
        return this.steeringWheels.contains(wheel);
    }

    boolean isAbandoned() {
        for (EngineBlockEntity e : this.engines) {
            if (!e.isRemoved()) {
                return false;
            }
        }
        for (SuspensionBlockEntity s : this.suspensions) {
            if (!s.isRemoved()) {
                return false;
            }
        }
        for (SteeringWheelBlockEntity w : this.steeringWheels) {
            if (!w.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    boolean hasRemovedMember() {
        for (SuspensionBlockEntity s : this.suspensions) {
            if (s.isRemoved()) {
                return true;
            }
        }
        return false;
    }


    public void gameTick(Level level) {
        long now = level.getGameTime();
        if (now == this.steppedGameTime) {
            return;
        }
        this.steppedGameTime = now;

        CarState.Aids aids = CarState.Aids.NONE;
        for (SteeringWheelBlockEntity wheel : this.steeringWheels) {
            if (!wheel.isRemoved()) {
                wheel.driverStep();
                aids = wheel.aids();
            }
        }
        this.state.setAids(aids);

        CarState.Controls controls = this.state.freshControls(now);
        if (!this.steeringWheels.isEmpty()) {
            for (EngineBlockEntity engine : this.engines) {
                if (!engine.isRemoved()) {
                    engine.markKeyed();
                }
            }
        }
        if (controls != null) {
            for (EngineBlockEntity engine : this.engines) {
                if (!engine.isRemoved()) {
                    engine.setDriverControls(controls.throttle01(), controls.clutch(), controls.shiftUp(),
                            controls.shiftDown());
                    engine.setDriverAids(controls.overtake(), controls.modeUp(), controls.modeDown());
                    engine.setDriverIgnition(controls.ignition());
                }
            }
            for (SuspensionBlockEntity axle : this.suspensions) {
                if (!axle.isRemoved()) {
                    double steer = !axle.isSteered() ? 0.0 : axle.isFrontAxle() ? controls.steer() : -controls.steer();
                    axle.setDriverSteering(steer, controls.brake());
                }
            }
        }
        for (SuspensionBlockEntity axle : this.suspensions) {
            if (!axle.isRemoved()) {
                axle.gameStep();
            }
        }
        driveStep(now);
    }


    private void driveStep(long now) {
        List<SuspensionBlockEntity> fitted = fittedAxles();
        CarGeometry.Axis axis = axis(fitted);
        List<CarSpec.Axle> specAxles = new ArrayList<>();
        for (SuspensionBlockEntity axle : fitted) {
            Vec3 centre = axle.axleCentre();
            double along = axis == null ? Double.NaN : axis.along(centre.x, centre.z);
            specAxles.add(new CarSpec.Axle(axle.getBlockPos().asLong(), axle.isFrontAxle(), axle.isDriven(), tires(axle), along));
        }
        CarSpec spec = new CarSpec(List.copyOf(specAxles));

        for (EngineBlockEntity engine : this.engines) {
            if (!engine.isRemoved()) {
                engine.beginStep();
            }
        }
        List<Drive> next = new ArrayList<>();
        for (EngineBlockEntity engine : this.engines) {
            if (engine.isRemoved()) {
                continue;
            }
            Driveline line = DrivelineSynthesizer.synthesize(spec,
                    engine.centerDiff(), engine.interAxleDiff());
            int[] sign = new int[spec.size()];
            for (int i : line.axles()) {
                sign[i] = engine.driveSign(fitted.get(i).getFacing());
            }
            Drive drive = new Drive(engine, line, sign);
            engine.gameStep(meanOmega(drive, fitted, spec, new double[spec.size()]), drivenWheels(line, spec));
            next.add(drive);
        }
        this.driveAxles = List.copyOf(fitted);
        this.driveSpec = spec;
        this.drives = List.copyOf(next);

        if (!this.substepped) {
            stepDrive(TICK_SECONDS);
        }
        this.substepped = false;
    }

    private void stepDrive(double dt) {
        CarSpec spec = this.driveSpec;
        if (spec == null) {
            return;
        }
        int n = spec.size();
        double[] torque = new double[n];
        double[] stiffness = new double[n];
        double[] limit = new double[n];
        boolean[] driven = new boolean[n];
        double[] still = new double[n];
        for (Drive drive : this.drives) {
            if (drive.engine().isRemoved()) {
                continue;
            }
            double[] omega = new double[n];
            double mean = meanOmega(drive, this.driveAxles, spec, omega);
            DrivetrainSim.Coupling coupling = drive.engine().coupling(mean, dt, this.state.dragThrottle());
            if (drivenWheels(drive.line(), spec) == 0) {
                continue;
            }
            double[] perWheel = drive.line().distribute(coupling.torque(), spec, omega);
            double[] share = drive.line().distribute(1.0, spec, still);
            for (int i : drive.line().axles()) {
                torque[i] += perWheel[i] * drive.sign()[i];
                stiffness[i] += share[i] * coupling.stiffness();
                limit[i] += share[i] * coupling.limit();
                driven[i] = true;
            }
        }

        this.stepDemand = demand(torque, limit, driven);

        List<SuspensionBlockEntity> drivenList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SuspensionBlockEntity axle = this.driveAxles.get(i);
            if (driven[i] && !axle.isRemoved()) {
                drivenList.add(axle);
                AxleDrive ramp = this.state.axleDrive(spec.axle(i).key());
                ramp.took(axle.getDriveTorquePerWheel());
                DriveInputSpec input = axle.getDifferential().input();
                axle.setDrive(torque[i], stiffness[i], ramp.min(input, limit[i], dt), ramp.max(input, limit[i], dt));
            }
        }
        this.drivenAxles = List.copyOf(drivenList);
        int wheels = 0;
        for (SuspensionBlockEntity axle : drivenList) {
            wheels += axle.fittedCorners().size();
        }
        for (SuspensionBlockEntity axle : drivenList) {
            axle.setDriveSplit(wheels == 0 ? 0.0 : 1.0 / wheels, this.splitMean);
        }
    }

    private double demand(double[] torque, double[] limit, boolean[] driven) {
        double demand = 0.0;
        for (int i = 0; i < torque.length; i++) {
            SuspensionBlockEntity axle = this.driveAxles.get(i);
            if (driven[i] && !axle.isRemoved()) {
                if (Math.abs(torque[i]) <= Math.abs(limit[i])) {
                    return 0.0;
                }
                demand += Math.abs(limit[i]) * tires(axle);
            }
        }
        return demand;
    }

    public void queueSplit(SuspensionBlockEntity axle, List<DriveSplit.Wheel> wheels) {
        this.splitAxles.add(axle);
        this.splitWheels.addAll(wheels);
    }

    void finishSplit(double dt) {
        if (this.splitAxles.isEmpty()) {
            return;
        }
        DriveSplit.solve(this.splitWheels, dt);
        for (SuspensionBlockEntity axle : this.splitAxles) {
            axle.finishDrive(dt);
        }
        double mean = 0.0;
        for (DriveSplit.Wheel wheel : this.splitWheels) {
            mean += wheel.change / this.splitWheels.size();
        }
        this.splitMean = mean;
        this.splitAxles.clear();
        this.splitWheels.clear();
    }

    private static double meanOmega(Drive drive, List<SuspensionBlockEntity> axles, CarSpec spec, double[] omega) {
        double sum = 0.0;
        int wheels = 0;
        for (int i : drive.line().axles()) {
            SuspensionBlockEntity axle = axles.get(i);
            omega[i] = axle.isRemoved() ? 0.0 : meanSpin(axle.fittedCorners()) * drive.sign()[i];
            int t = spec.axle(i).tires();
            sum += omega[i] * t;
            wheels += t;
        }
        return wheels == 0 ? 0.0 : sum / wheels;
    }

    private static double meanSpin(List<CornerState> corners) {
        double sum = 0.0;
        for (CornerState corner : corners) {
            sum += corner.omega;
        }
        return corners.isEmpty() ? 0.0 : sum / corners.size();
    }

    private static int drivenWheels(Driveline line, CarSpec spec) {
        int wheels = 0;
        for (int i : line.axles()) {
            wheels += spec.axle(i).tires();
        }
        return wheels;
    }

    private List<SuspensionBlockEntity> fittedAxles() {
        List<SuspensionBlockEntity> fitted = new ArrayList<>();
        for (SuspensionBlockEntity axle : this.suspensions) {
            if (!axle.isRemoved() && axle.hasAnyTire()) {
                fitted.add(axle);
            }
        }
        return fitted;
    }

    @Nullable
    private static CarGeometry.Axis axis(List<SuspensionBlockEntity> axles) {
        CarGeometry.Builder geometry = new CarGeometry.Builder();
        for (SuspensionBlockEntity axle : axles) {
            Vec3 centre = axle.axleCentre();
            geometry.add(centre.x, centre.z, axle.isFrontAxle());
        }
        return geometry.build();
    }

    private static int tires(SuspensionBlockEntity axle) {
        return (axle.hasTire(SuspensionBlockEntity.WheelSide.LEFT) ? 1 : 0)
                + (axle.hasTire(SuspensionBlockEntity.WheelSide.RIGHT) ? 1 : 0);
    }


    void refresh(long serial, double dt) {
        if (!this.state.beginRefresh(serial)) {
            return;
        }

        for (SubLevel body : this.bodies) {
            if (!(body instanceof ServerSubLevel server)) {
                continue;
            }
            MassData massData = server.getMassTracker();
            if (massData.isInvalid()) {
                continue;
            }

            RigidBodyHandle handle = RigidBodyHandle.of(server);
            Vector3d v = handle != null ? handle.getLinearVelocity(new Vector3d())
                    : new Vector3d(server.latestLinearVelocity);
            double vx = v.x;
            double vz = v.z;
            this.state.putBody(body.getUniqueId(), new CarState.Body(massData.getMass(),
                    Math.sqrt(vx * vx + vz * vz), wheelsOn(body)));
        }

        refreshSteerGeometry();
        this.substepped = true;
        stepDrive(dt);
        stepTraction(dt);
        recapTraction();
    }

    private int wheelsOn(SubLevel body) {
        int count = 0;
        for (SuspensionBlockEntity s : this.suspensions) {
            if (s.isRemoved() || this.bodyOf.get(s) != body) {
                continue;
            }
            count += tires(s);
        }
        return count;
    }

    private void refreshSteerGeometry() {
        CarGeometry.Axis axis = axis(fittedAxles());
        if (axis == null) {
            return;
        }
        this.state.setWheelbase(axis.wheelbase());
    }

    public void queueForces(SuspensionBlockEntity axle) {
        this.queuedForces.add(axle);
    }

    void flushForces(ServerLevel level, double dt) {
        Set<ServerSubLevel> dragged = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SuspensionBlockEntity axle : this.queuedForces) {
            if (axle.isRemoved()) {
                continue;
            }
            SubLevel subLevel = Sable.HELPER.getContaining(axle);
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
                continue;
            }
            RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
            if (handle != null) {
                if (dragged.add(serverSubLevel)) {
                    BodyDrag.apply(level, serverSubLevel, axle.forceTotal(), dt);
                }
                com.createmotorsport.physics.world.PocketSizeCompat.applyForces(serverSubLevel, handle, axle.forceTotal());
            }
        }
        this.queuedForces.clear();

        for (SubLevel body : this.bodies) {
            if (body instanceof ServerSubLevel server && server.getLevel() == level && !server.isRemoved()
                    && dragged.add(server)) {
                RigidBodyHandle handle = RigidBodyHandle.of(server);
                if (handle != null) {
                    BodyDrag.apply(level, server, this.dragForces, dt);
                    com.createmotorsport.physics.world.PocketSizeCompat.applyForces(server, handle, this.dragForces);
                }
            }
        }
    }


    private void stepTraction(double dt) {
        EngineBlockEntity rep = engine();
        TractionControlSpec spec = rep == null || rep.isRemoved() ? null : rep.tractionControlSpec();
        double throttle = throttle();
        if (spec == null || !this.state.aids().tractionControl()) {
            this.state.setTraction(TractionControl.Result.off(spec == null ? 0.0 : spec.targetSlip()));
            this.state.setDragThrottle(0.0);
            return;
        }
        stepDragControl(spec, dt);
        if (throttle <= 0.1) {
            this.state.setTraction(TractionControl.Result.off(spec.targetSlip()));
            return;
        }

        double peakOmega = 0.0, peakLatUse = 0.0, tractionForce = 0.0, repRadius = 0.0, demand = 0.0;
        double lon = 0.0, lat = 0.0;
        int grounded = 0;
        for (SuspensionBlockEntity axle : this.drivenAxles) {
            if (axle.isRemoved()) {
                continue;
            }
            if (repRadius <= 0.0) {
                repRadius = axle.getTireRadius();
            }
            for (CornerState corner : axle.fittedCorners()) {
                peakOmega = Math.max(peakOmega, Math.abs(corner.omega));
                peakLatUse = Math.max(peakLatUse, corner.latUse);
                tractionForce += corner.remainingTraction();
                if (corner.grounded) {
                    lon += Math.abs(corner.vLon);
                    lat += Math.abs(corner.vLat);
                    grounded++;
                }
            }
            demand += Math.abs(axle.getDriveTorquePerWheel()) * tires(axle);
        }
        if (repRadius <= 0.0) {
            this.state.setTraction(TractionControl.Result.off(spec.targetSlip()));
            return;
        }
        CarState.Body body = this.state.body(root().getUniqueId());
        double groundSpeed = grounded > 0 ? lon / grounded : body == null ? 0.0 : body.speed();
        double sideSlip = grounded > 0 ? lat / grounded : 0.0;

        this.state.setTraction(TractionControl.step(this.state.traction().factor(),
                this.state.traction().slipRatio(), peakOmega * repRadius,
                groundSpeed, sideSlip, peakLatUse, spec.targetSlip(), spec.cutRate(), spec.buildRate(),
                spec.minThrottle(), tractionForce, repRadius, demand, dt));
    }

    private void stepDragControl(TractionControlSpec spec, double dt) {
        double slowest = Double.MAX_VALUE, lon = 0.0;
        int grounded = 0;
        for (SuspensionBlockEntity axle : this.drivenAxles) {
            if (axle.isRemoved()) {
                continue;
            }
            for (CornerState corner : axle.fittedCorners()) {
                if (corner.grounded) {
                    slowest = Math.min(slowest, Math.abs(corner.omega) * axle.getTireRadius());
                    lon += Math.abs(corner.vLon);
                    grounded++;
                }
            }
        }
        double throttle = grounded == 0 ? 0.0
                : DragControl.step(this.state.dragThrottle(), slowest, lon / grounded, spec.targetSlip(),
                        spec.dragMaxThrottle(), spec.cutRate(), spec.buildRate(), dt);
        this.state.setDragThrottle(throttle);
    }

    private void recapTraction() {
        if (!this.state.aids().tractionControl() || throttle() <= 0.1) {
            return;
        }
        double tractionForce = 0.0, repRadius = 0.0;
        for (SuspensionBlockEntity axle : this.drivenAxles) {
            if (axle.isRemoved()) {
                continue;
            }
            if (repRadius <= 0.0) {
                repRadius = axle.getTireRadius();
            }
            for (CornerState corner : axle.fittedCorners()) {
                tractionForce += corner.remainingTraction();
            }
        }
        this.state.setTraction(TractionControl.recap(this.state.traction(), tractionForce, repRadius,
                this.stepDemand));
    }

    private double throttle() {
        double throttle = 0.0;
        for (EngineBlockEntity engine : this.engines) {
            if (!engine.isRemoved()) {
                throttle = Math.max(throttle, engine.getThrottle());
            }
        }
        return throttle;
    }


    @Nullable
    public EngineBlockEntity engine() {
        return this.engines.isEmpty() ? null : this.engines.get(this.engines.size() - 1);
    }

    public List<EngineBlockEntity> engines() {
        return this.engines;
    }

    public List<SuspensionBlockEntity> suspensions() {
        return this.suspensions;
    }

    public List<SubLevel> bodies() {
        return this.bodies;
    }

    public SubLevel root() {
        return this.bodies.get(0);
    }

    public CarState state() {
        return this.state;
    }

    public double throttleAuthority() {
        double radius = 0.0;
        double mass = 0.0;
        int axles = 0;
        for (SuspensionBlockEntity axle : this.drivenAxles) {
            if (!axle.isRemoved() && axle.meanTireRadius() > 0.0) {
                radius += axle.meanTireRadius();
                mass = axle.getCachedCarMass();
                axles++;
            }
        }
        if (axles == 0 || mass <= 0.0) {
            return 0.0;
        }
        double torque = 0.0;
        for (EngineBlockEntity engine : this.engines) {
            if (!engine.isRemoved()) {
                torque += engine.wheelTorquePerThrottle();
            }
        }
        return torque / (radius / axles) / mass;
    }
}
