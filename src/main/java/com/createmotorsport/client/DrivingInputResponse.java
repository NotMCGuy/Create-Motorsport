package com.createmotorsport.client;

public final class DrivingInputResponse {
    private DrivingInputResponse() { }

    public static float steering(float current, float target, float ramp) {
        float step = ramp * (target == 0 || current * target < 0 ? 2 : 1);
        return approach(current, target, step);
    }

    public static float pedal(float current, float target, float ramp, boolean brake) {
        return target < current ? target : approach(current, target, ramp * (brake ? 2 : 1));
    }

    private static float approach(float current, float target, float step) {
        return current + Math.clamp(target - current, -step, step);
    }
}
