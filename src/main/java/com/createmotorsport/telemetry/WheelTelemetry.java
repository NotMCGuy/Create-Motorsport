package com.createmotorsport.telemetry;

import com.createmotorsport.physics.vehicle.CornerState;

// One wheel's last physics step as the CSV log and the dashboard read it
public record WheelTelemetry(boolean grounded, double loadN, double slipRatio, double slipAngleDeg,
                             double vLonMs, double vLatMs, double longForceN, double latForceN,
                             double omega, double wheelSpeedMs, double springLenM, double compressionM,
                             double effMu, double peakMu,
                             double brakeTorqueNm, double gripMult, double driveTorqueNm,
                             double tireTempC, double rigidLenM, double tireDeflM, double unsprungVMs,
                             double effMassKg, double hardpointVMs, double hpvSpanMs, double springForceN, double tireRateN,
                             double velWorld, double velBody, double velDiff, double velNormal,
                             double castLift, double dampFraction, double assistLift,
                             double gripUse, double gripLon, double gripLat,
                             double peakForceN, double slipCombined, double slipAtPeak,
                             double curveForceN,
                             double surfaceMu,
                             double hardpointY, double rigidRawM, double assistTargetM,
                             double assistRiseM, double assistProbeM, int assistWhy,
                             double absFactor,
                             double lockSlip) {

    public static WheelTelemetry of(CornerState w) {
        return new WheelTelemetry(w.grounded, w.telem.load, w.slipRatio,
                Math.toDegrees(w.slipAngleRad), w.vLon, w.telem.vLat, w.longForce,
                w.telem.latForce, w.omega, w.telem.wheelSpeed, w.springLength, w.telem.compression,
                w.surfaceMu * w.telem.gripMult * w.telem.tempFactor, w.peakMu,
                w.telem.brakeTorque, w.telem.gripMult, w.telem.driveTorque,
                w.tireTemp, w.telem.rigidLength, w.tireDeflection, w.unsprungVel, w.telem.effMass,
                w.telem.hardpointVel, w.telem.hpvSpan(),
                w.telem.springForce, w.telem.tireRate,
                w.telem.velWorld, w.telem.velBody, w.telem.velDiff, w.telem.velNormal, w.telem.castLift,
                w.telem.dampFraction, w.telem.assistLift, w.telem.gripUse,
                w.telem.gripLon, w.telem.gripLat,
                w.peakForce, w.telem.slipCombined, w.telem.slipAtPeak, w.telem.curveForce,
                w.surfaceMu,
                w.telem.hardpointY, w.telem.rigidRaw, w.telem.assistTarget,
                w.telem.assistRise, w.telem.assistProbeD, w.telem.assistWhy,
                w.abs.factor, w.telem.lockSlip);
    }
}
