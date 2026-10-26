package com.createmotorsport.motorcycle;

public final class MotorcycleHandling {
    public static final double AERO_DRAG_SCALE = .45;
    private static final double MAX_STEERING_LOCK = Math.toRadians(50);
    private MotorcycleHandling() { }
    public static org.joml.Vector3d angularImpulse(org.joml.Matrix3dc inertia, org.joml.Vector3dc acceleration, double dt, org.joml.Vector3d out) {
        return inertia.transform(out.set(acceleration).mul(dt));
    }
    public static double steering(double requested,double speed,double wheelbase,double corneringG) {
        if (!Double.isFinite(requested)||!Double.isFinite(speed)||!Double.isFinite(wheelbase)||wheelbase<=0
                ||!Double.isFinite(corneringG)||corneringG<=0)return 0;
        double limit=Math.min(MAX_STEERING_LOCK,Math.atan(9.81*corneringG*wheelbase/Math.max(1,speed*speed)));
        double input=Math.clamp(requested/Math.toRadians(32),-1,1);
        return limit*Math.tanh(input*1.25)/Math.tanh(1.25);
    }
    public static double yawRate(double speed,double steer,double wheelbase,double corneringG) {
        if(!Double.isFinite(speed)||!Double.isFinite(steer)||!Double.isFinite(wheelbase)||wheelbase<=0||!Double.isFinite(corneringG)||corneringG<=0)return 0;
        double limit=Math.min(MAX_STEERING_LOCK,Math.atan(9.81*corneringG*wheelbase/Math.max(1,speed*speed)));
        double safe=Math.clamp(steer,-limit,limit);
        return Math.clamp(speed*Math.tan(safe)/wheelbase,-1.5,1.5);
    }
    public static double yawAcceleration(double desired,double current,double dt) {
        if(!Double.isFinite(desired)||!Double.isFinite(current)||!Double.isFinite(dt)||dt<=0||dt>.1)return 0;
        return Math.clamp(6*(desired-current)/(1+6*dt),-3.5,3.5);
    }
    public static double headingContactGain(boolean frontGrounded, boolean rearGrounded) {
        return frontGrounded && rearGrounded ? 1 : rearGrounded ? .6 : 0;
    }
    public static double achievedLean(double speed,double yawRate,double requestedLean,double limit) {
        if(!Double.isFinite(speed)||!Double.isFinite(yawRate)||!Double.isFinite(requestedLean)||!Double.isFinite(limit)||limit<=0)return 0;
        double fade=Math.clamp((Math.abs(speed)-.5)/2,0,1);
        double measured=-Math.atan(speed*yawRate/9.81)*fade;
        return Math.clamp(.75*measured+.25*requestedLean,-limit,limit);
    }
    public static double brakeResponse(double current,double requested,double dt) {
        if(!Double.isFinite(current)||!Double.isFinite(requested)||!Double.isFinite(dt)||dt<=0)return 0;
        double target=Math.clamp(requested,0,1), rate=target>current?4:10;
        return Math.clamp(current+Math.clamp(target-current,-rate*dt,rate*dt),0,1);
    }
    public static double brakeTorque(double requested,double radius,double inertia,double omega,
                                     double drive,double tireForce,double speed,double load,double grip,
                                     double bank,double pitch,double pitchRate,double dt) {
        if(!Double.isFinite(requested+radius+inertia+omega+drive+tireForce+speed+load+grip+bank+pitch+pitchRate+dt)
                ||radius<=0||inertia<=0||dt<=0||dt>.1||load<=0||grip<=0)return 0;
        double corner=Math.clamp(1-.65*Math.abs(Math.sin(bank)),.35,1);
        double dive=Math.clamp(1-3*Math.max(0,-pitch-.06)-.7*Math.max(0,-pitchRate),.1,1);
        double limit=Math.min(load*.55,grip*.65)*radius*corner*dive;
        if(Math.abs(speed)>.75) {
            double rolling=speed/radius,slip=omega-rolling;
            double k=Math.abs(slip)>1e-3?Math.max(0,tireForce*radius/slip):0;
            double direction=Math.signum(speed),target=Math.abs(rolling)*.92;
            double spinLimit=drive*direction+inertia*(omega*direction-target)/dt+k*(Math.abs(rolling)-target);
            limit=Math.min(limit,Math.max(0,spinLimit));
        }
        return Math.max(0,Math.min(requested,limit));
    }
    public static double brakingPitchAcceleration(double pitch,double rate,double braking) {
        if(!Double.isFinite(pitch+rate+braking)||braking<=0)return 0;
        return Math.clamp((-16*pitch-6*rate)*Math.clamp(braking,0,1),-8,8);
    }
    public static double pitchAcceleration(double pitch,double rate) {
        if(!Double.isFinite(pitch)||!Double.isFinite(rate))return 0;
        double error=Math.copySign(Math.max(0,Math.abs(pitch)-Math.toRadians(12)),pitch);
        return error==0?0:Math.clamp(-8*error-2*rate,-3,3);
    }
}
