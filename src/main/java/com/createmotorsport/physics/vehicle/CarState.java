package com.createmotorsport.physics.vehicle;

import com.createmotorsport.physics.control.Cruise;
import com.createmotorsport.physics.control.TractionControl;
import com.createmotorsport.physics.terrain.TerrainAssist;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CarState is for everything that changes while the car drives, and that which belongs to the car rather than an axle
 * So driver controls and aids, per-body mass
 */
public final class CarState {

    public record Body(double mass, double speed, int wheels) {
    }
    private final Map<UUID, Body> bodies = new HashMap<>();
    private final Map<Long, AxleDrive> axleDrives = new HashMap<>();

    private double wheelbase;

    // Driver aid settings, saved by the steering wheel and copied by the car every tick. None would be a car without a steering wheel
    public record Aids(TerrainAssist.Level terrainAssist, boolean steerAssistOff, boolean driftDiff,
                       boolean tractionControl) {
        public static final Aids NONE = new Aids(TerrainAssist.Level.NORMAL, false, false, false);
    }


    public record Controls(float throttle01, boolean clutch, boolean shiftUp, boolean shiftDown,
                           boolean overtake, boolean modeUp, boolean modeDown,
                           double steer, double brake, boolean ignition) {
    }

    private Aids aids = Aids.NONE;
    private Controls controls;
    private long controlsTime = Long.MIN_VALUE;

    private long refreshedSerial = Long.MIN_VALUE;

    private TractionControl.Result traction = TractionControl.Result.OFF;

    public Body body(UUID id) {
        return this.bodies.get(id);
    }

    public void putBody(UUID id, Body body) {
        this.bodies.put(id, body);
    }

    public AxleDrive axleDrive(long axleKey) {
        return this.axleDrives.computeIfAbsent(axleKey, k -> new AxleDrive());
    }

    public void retainAxles(Set<Long> axleKeys) {
        this.axleDrives.keySet().retainAll(axleKeys);
    }

    public Aids aids() {
        return this.aids;
    }

    public void setAids(Aids aids) {
        this.aids = aids;
    }

    public void setControls(Controls controls, long gameTime) {
        this.controls = controls;
        this.controlsTime = gameTime;
    }
    public Controls freshControls(long gameTime) {
        return this.controlsTime == gameTime ? this.controls : null;
    }

    private final Cruise.Controller cruise = new Cruise.Controller();

    public Cruise.Controller cruise() {
        return this.cruise;
    }

    private double dragThrottle; // engine drag control

    public double dragThrottle() {
        return this.dragThrottle;
    }

    public void setDragThrottle(double throttle) {
        this.dragThrottle = throttle;
    }

    public TractionControl.Result traction() {
        return this.traction;
    }

    public void setTraction(TractionControl.Result traction) {
        this.traction = traction;
    }

    public double wheelbase() {
        return this.wheelbase;
    }

    public void setWheelbase(double wheelbase) {
        this.wheelbase = wheelbase;
    }

    public boolean beginRefresh(long serial) {
        if (this.refreshedSerial == serial) {
            return false;
        }
        this.refreshedSerial = serial;
        return true;
    }
}
