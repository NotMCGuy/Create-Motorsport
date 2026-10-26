package com.createmotorsport.motorcycle;

import org.joml.Vector3f;

public final class MotorcycleLimbPose {
    private final Vector3f axis=new Vector3f(),bend=new Vector3f();
    public void solve(Vector3f origin,Vector3f target,Vector3f pole,float upper,float lower,Vector3f joint,Vector3f end){
        axis.set(target).sub(origin);float requested=axis.length();
        if(requested<.00001f)axis.set(0,1,0);else axis.div(requested);
        float distance=Math.clamp(requested,Math.abs(upper-lower)+.001f,upper+lower-.001f);
        bend.set(pole).sub(origin);bend.fma(-bend.dot(axis),axis);
        if(bend.lengthSquared()<.00001f){bend.set(1,0,0);bend.fma(-bend.dot(axis),axis);if(bend.lengthSquared()<.00001f)bend.set(0,0,1);}
        bend.normalize();float along=(upper*upper-lower*lower+distance*distance)/(2*distance);
        float height=(float)Math.sqrt(Math.max(0,upper*upper-along*along));
        joint.set(origin).fma(along,axis).fma(height,bend);end.set(origin).fma(distance,axis);
    }
    public static float ease(float value){float t=Math.clamp(value,0,1);return t*t*(3-2*t);}
}
