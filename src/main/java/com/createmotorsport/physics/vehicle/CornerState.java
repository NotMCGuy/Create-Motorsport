package com.createmotorsport.physics.vehicle;

import com.createmotorsport.physics.Tire;
import com.createmotorsport.physics.spec.SurfaceSpec;
import com.createmotorsport.physics.suspension.UnsprungSolver;
import com.createmotorsport.physics.terrain.TerrainAssist;
import net.minecraft.util.Mth;

/**
 * One wheel's state that Corner.step carries between substeps, plus its outputs for other code and telemetry
 * SuspensionBlockEntity.WheelState extends it for client interpolation, smoke, skidmarks
 */
public class CornerState {

    public double omega;
    public double springLength;
    public boolean grounded;
    public double surfaceMu = 1.0;

    public double unsprungVel;
    public final com.createmotorsport.physics.control.Abs.State abs =
            new com.createmotorsport.physics.control.Abs.State();
    public double brakePressure; // 0 to 1, builds toward the driver's brake at the setup's apply_time
    public double tireDeflection;
    public double contactLoad;

    // carried between substeps
    public double prevRigidLength = Double.NaN;
    public double latUse;
    public SurfaceSpec surface = SurfaceSpec.DRY_DEFAULT;
    public final Tire.Contact contact = new Tire.Contact();
    public final UnsprungSolver.State unsprung = new UnsprungSolver.State();
    public final TerrainAssist.State assist = new TerrainAssist.State();

    public double prevLongForce; // last longitudinal tire force, for the relaxation low-pass
    public double deflAlpha;     // lateral contact-patch deflection in (m), fiala lateral relaxation state
    public double latBristle;  // lateral bristle deflection in (m), TMeasy dahl stand-still model
    public double lonBristle;  // longitudinal bristle deflection in (m), TMeasy stand-still model
    public double latRelax;    // lateral carcass deflection y_e in (m), Rill 3.4.2
    public double tireTemp = 20.0; // deg C, see TireThermal. Reset to ambient the first step the model runs
    public boolean thermalStarted;

    // The last outputs which other stuff reads, so the clutch solve (longForce, vLon), traction control (vLon, vLat)
    // the HUD (peakMu), the understeer gradient (peakForce) and the smoke (slipRatio, slipAngleRad)
    public double longForce;
    public double vLon;
    public double vLat;
    public double peakMu = 1.0;
    public double peakForce;
    public double slipRatio;
    public double slipAngleRad;
    public double peakSlipAngleRad = Double.NaN; // where lateral force of this tyre peaks now; NaN airborne
    public double spinFrom; // last spin step for the drive split
    public double spinCoupling = Double.NaN; // NaN when the wheel didnt step against the drive, so held by brake, powered, or just otherwise not stepped

    public final CornerTelemetry telem = new CornerTelemetry();

    public CornerState(double restLength) {
        this.springLength = restLength;
    }

    // Longitudinal force left on the friction ellipse after the lateral force, so TC can cap the drive with it
    public double remainingTraction() {
        if (!grounded) {
            return 0.0;
        }
        double lat = Mth.clamp(latUse, 0.0, 1.0);
        return contactLoad * peakMu * Math.sqrt(Math.max(0.0, 1.0 - lat * lat));
    }
}
