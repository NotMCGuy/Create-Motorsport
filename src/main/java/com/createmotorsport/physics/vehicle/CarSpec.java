package com.createmotorsport.physics.vehicle;

import java.util.List;

public record CarSpec(List<Axle> axles) {

    /**
     * @param key   block position
     * @param tires 1 or 2
     * @param along the axle's centre along the car, wheel offset included, from the rear {@link CarGeometry.Axis#along}
     */
    public record Axle(long key, boolean front, boolean driven, int tires, double along) {
    }

    public int size() {
        return this.axles.size();
    }

    public Axle axle(int index) {
        return this.axles.get(index);
    }
}
