package com.createmotorsport.physics.vehicle;

import java.util.List;

public record CarSpec(List<Axle> axles) {

    public record Axle(long key, boolean front, boolean driven, int tires, double along) {
    }

    public int size() {
        return this.axles.size();
    }

    public Axle axle(int index) {
        return this.axles.get(index);
    }
}
