package com.createmotorsport.physics.differential;

import com.createmotorsport.physics.Differential;
import com.createmotorsport.physics.spec.DifferentialSpec;
import com.createmotorsport.physics.vehicle.Corner;
import com.createmotorsport.physics.vehicle.CornerState;

import java.util.ArrayList;
import java.util.List;

/**
 * One axle's drive and differential for a substep, used by SuspensionBlockEntity
 * warmStart() builds each wheel's Corner.Axle, wheels() goes to AssembledCar's DriveSplit,
 * and finish() runs the differential once the car is done (or at once on an axle the car doesn't split)
 */
public final class AxleDiff {

    // state carried into the next substep. (change) for warmStart, (diffTorque) for carried
    private final Differential.Axle diff = new Differential.Axle();
    private final DriveSplit.Wheel left = new DriveSplit.Wheel();
    private final DriveSplit.Wheel right = new DriveSplit.Wheel();

    private double torque;
    private double stiffness; // the clutch's, per wheel: Nm less per rad/s gained over a substep
    private double min;
    private double max;
    private double passed; // per wheel, what the clutch passed last substep before traction control's cut
    private double weight = 0.5;
    private double mean;
    private boolean pending;
    private double lastTraction = 1.0;

    // per wheel for the coming substep, min and max are the window the drive stays in
    public void setDrive(double torquePerWheel, double stiffnessPerWheel, double min, double max) {
        this.torque = torquePerWheel;
        this.stiffness = stiffnessPerWheel;
        this.min = min;
        this.max = max;
    }

    public void clearDrive() {
        this.torque = 0.0;
        this.stiffness = 0.0;
        this.min = 0.0;
        this.max = 0.0;
        this.passed = 0.0;
    }

    public double torque() {
        return this.torque;
    }

    public double stiffness() {
        return this.stiffness;
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double passed() {
        return this.passed;
    }

    public void setSplit(double weight, double mean) {
        this.weight = weight;
        this.mean = mean;
    }

    public double carried(boolean split, boolean pair) {
        return split && pair ? this.diff.diffTorque : 0.0;
    }

    // The clutch's stiffness on this wheel's own change at its weight in the mean, the rest of last substep's mean
    // as an offset, and the differential's last torque shifting the window
    // clamp(x, a, b) + t = clamp(x + t, a + t, b + t), exact when the changes repeat
    public Corner.Axle warmStart(Corner.Axle axle, boolean leftSide, double diffTorque) {
        DriveSplit.Wheel wheel = leftSide ? this.left : this.right;
        double c = axle.driveStiffness();
        return axle.withDrive(axle.driveTorque() - c * (this.mean - this.weight * wheel.change) + diffTorque,
                c * this.weight, axle.driveMin() + diffTorque, axle.driveMax() + diffTorque);
    }

    public void stepped(double traction, double diffTorque) {
        this.lastTraction = traction;
        this.diff.diffTorque = diffTorque;
    }

    public List<DriveSplit.Wheel> wheels(Corner.Axle axle, double diffTorque, CornerState leftState, double leftInertia,
                                         CornerState rightState, double rightInertia) {
        this.pending = true;
        List<DriveSplit.Wheel> out = new ArrayList<>(2);
        addWheel(out, this.left, leftState, leftInertia, axle, diffTorque);
        addWheel(out, this.right, rightState, rightInertia, axle, -diffTorque);
        return out;
    }

    private static void addWheel(List<DriveSplit.Wheel> out, DriveSplit.Wheel w, CornerState state, double inertia,
                                 Corner.Axle axle, double signedDiff) {
        if (state == null) {
            return;
        }
        w.omega = state.omega;
        w.from = state.spinFrom;
        w.inertia = inertia;
        w.coupling = state.spinCoupling;
        w.own = state.telem.driveTorque - signedDiff;
        w.torque = axle.driveTorque();
        w.stiffness = axle.driveStiffness();
        w.min = axle.driveMin();
        w.max = axle.driveMax();
        w.drive = w.own;
        out.add(w);
    }

    public void finish(CornerState leftState, boolean leftFitted, double leftInertia,
                       CornerState rightState, boolean rightFitted, double rightInertia,
                       boolean driven, DifferentialSpec spec, double massScale, boolean driftMode, double dt) {
        boolean split = this.pending;
        this.pending = false;
        if (split && leftFitted) {
            leftState.omega = this.left.omega;
            leftState.telem.driveTorque = this.left.drive;
        }
        if (split && rightFitted) {
            rightState.omega = this.right.omega;
            rightState.telem.driveTorque = this.right.drive;
        }
        if (leftFitted && rightFitted && driven) {
            Differential.Axle axle = this.diff;
            axle.omegaLeft = leftState.omega;
            axle.omegaRight = rightState.omega;
            axle.inertiaLeft = leftInertia
                    + (Double.isNaN(leftState.spinCoupling) ? 0.0 : leftState.spinCoupling * dt);
            axle.inertiaRight = rightInertia
                    + (Double.isNaN(rightState.spinCoupling) ? 0.0 : rightState.spinCoupling * dt);
            double input = leftState.telem.driveTorque + rightState.telem.driveTorque;
            // positive while driving, negative while engine braking
            axle.inputTorque = input * (leftState.omega + rightState.omega) >= 0.0 ? Math.abs(input) : -Math.abs(input);
            Differentials.of(spec.type()).solve(axle, spec, massScale, driftMode, dt);
            leftState.omega = axle.omegaLeft;
            rightState.omega = axle.omegaRight;
        }
        this.left.change = leftState.omega - leftState.spinFrom;
        this.right.change = rightState.omega - rightState.spinFrom;
        if (this.lastTraction > 1.0e-6) {
            double sum = 0.0;
            int wheels = 0;
            if (leftFitted) {
                sum += leftState.telem.driveTorque;
                wheels++;
            }
            if (rightFitted) {
                sum += rightState.telem.driveTorque;
                wheels++;
            }
            this.passed = wheels == 0 ? 0.0 : sum / wheels / this.lastTraction;
        }
    }
}
