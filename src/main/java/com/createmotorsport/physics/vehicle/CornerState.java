package com.createmotorsport.physics.vehicle;

import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.suspension.UnsprungSolver;
import com.createmotorsport.physics.terrain.TerrainAssist;
import net.minecraft.util.Mth;

public class CornerState {

    public double omega;
    public double springLength;
    public boolean grounded;
    public double surfaceMu = 1.0;

    public double unsprungVel;
    public final com.createmotorsport.physics.control.Abs.State abs =
            new com.createmotorsport.physics.control.Abs.State();
    public double brakePressure;
    public double tireDeflection;
    public double contactLoad;

    public double prevRigidLength = Double.NaN;
    public double latUse;
    public SurfaceSpec surface = SurfaceSpec.DRY_DEFAULT;
    public final Tire.Contact contact = new Tire.Contact();
    public final UnsprungSolver.State unsprung = new UnsprungSolver.State();
    public final TerrainAssist.State assist = new TerrainAssist.State();

    public double prevLongForce;
    public double deflAlpha;
    public double latBristle;
    public double lonBristle;
    public double latRelax;
    public double tireTemp = 20.0;
    public boolean thermalStarted;

    public double longForce;
    public double vLon;
    public double vLat;
    public double peakMu = 1.0;
    public double peakForce;
    public double slipRatio;
    public double slipAngleRad;
    public double peakSlipAngleRad = Double.NaN;
    public double spinFrom;
    public double spinCoupling = Double.NaN;

    public final CornerTelemetry telem = new CornerTelemetry();

    public CornerState(double restLength) {
        this.springLength = restLength;
    }

    public double remainingTraction() {
        if (!grounded) {
            return 0.0;
        }
        double lat = Mth.clamp(latUse, 0.0, 1.0);
        return contactLoad * peakMu * Math.sqrt(Math.max(0.0, 1.0 - lat * lat));
    }
}
