package com.createmotorsport.physics.vehicle;


public final class CornerTelemetry {

    public double load;
    public double vLat;
    public double slipCombined;
    public double slipAtPeak;
    public double curveForce;
    public double latForce;
    public double wheelSpeed;
    public double compression;
    public double brakeTorque;
    public double driveTorque; // what the wheel received this substep, the clutch's stiffness included
    public double lockSlip;
    public double gripMult = 1.0;
    public double tempFactor = 1.0;
    public double rigidLength = Double.NaN;
    public double effMass = Double.NaN;
    public double hardpointVel = Double.NaN;
    public double springForce = Double.NaN;
    public double velWorld = Double.NaN;
    public double velBody = Double.NaN;
    public double velDiff = Double.NaN;
    public double velNormal = Double.NaN;
    public double castLift = Double.NaN;
    public double dampFraction = Double.NaN;
    public double assistLift = Double.NaN;
    public double hardpointY = Double.NaN;
    public double rigidRaw = Double.NaN;
    public double assistTarget;
    public double assistRise;
    public double assistProbeD;
    public int assistWhy;
    public double gripUse;
    public double gripLon;
    public double gripLat;
    public double tireRate;

    private double hpvMin = Double.NaN;
    private double hpvMax = Double.NaN;
    private long hpvStamp = Long.MIN_VALUE;

    public void sampleHardpointVel(double v, long gameTime) {
        if (this.hpvStamp != gameTime) {
            this.hpvStamp = gameTime;
            this.hpvMin = v;
            this.hpvMax = v;
        } else {
            this.hpvMin = Math.min(this.hpvMin, v);
            this.hpvMax = Math.max(this.hpvMax, v);
        }
    }

    public double hpvSpan() {
        return this.hpvMax - this.hpvMin;
    }
}
