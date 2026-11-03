package com.createmotorsport.physics.vehicle;

/**
 * The car's axis from the rear axles' centroid to the front
 * AssembledCar feeds each axle to a Builder
 * Positions are one body's plot coordinates, not defined yet for a car of two bodies
 */
public final class CarGeometry {

    private CarGeometry() {
    }

    public record Axis(double rearX, double rearZ, double dirX, double dirZ, double wheelbase) {

        // from the rear axis centroid, larger is further forward
        public double along(double x, double z) {
            return (x - this.rearX) * this.dirX + (z - this.rearZ) * this.dirZ;
        }
    }

    // build() is null without an axle at each end
    public static final class Builder {
        private double frontX, frontZ, rearX, rearZ;
        private int front, rear;

        public void add(double x, double z, boolean isFront) {
            if (isFront) {
                this.frontX += x;
                this.frontZ += z;
                this.front++;
            } else {
                this.rearX += x;
                this.rearZ += z;
                this.rear++;
            }
        }

        public Axis build() {
            if (this.front == 0 || this.rear == 0) {
                return null;
            }
            double rx = this.rearX / this.rear;
            double rz = this.rearZ / this.rear;
            double dx = this.frontX / this.front - rx;
            double dz = this.frontZ / this.front - rz;
            double wheelbase = Math.hypot(dx, dz);
            if (wheelbase < 1.0e-9) {
                return new Axis(rx, rz, 0.0, 0.0, 0.0);
            }
            return new Axis(rx, rz, dx / wheelbase, dz / wheelbase, wheelbase);
        }
    }
}
