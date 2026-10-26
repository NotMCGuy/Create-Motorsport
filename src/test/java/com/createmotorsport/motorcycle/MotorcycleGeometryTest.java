package com.createmotorsport.motorcycle;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class MotorcycleGeometryTest {
    @Test void steeringAndForkTravelCompose(){
        var d=MotorcycleDefinition.parse("{\"rake_degrees\":15}");var pose=new MotorcycleDefinition.Pose();var rest=new Vector3f();var moved=new Vector3f();
        float length=(float)com.createmotorsport.block.entity.SuspensionBlockEntity.REST_LENGTH;
        pose.update(d,.4f,length);pose.point(d,"front_axle",rest);
        pose.update(d,.4f,length-.15f);pose.point(d,"front_axle",moved);
        assertEquals(.15,rest.distance(moved),1e-5);
        var grip=new Vector3f();pose.point(d,"grip_right",grip);pose.update(d,.4f,length);assertEquals(0,grip.distance(pose.point(d,"grip_right",moved)),1e-5);
    }
    @Test void swingarmKeepsItsLengthAndShockEndpointsFinite(){
        var d=MotorcycleDefinition.DEFAULT;var pose=new MotorcycleDefinition.Pose();var pivot=new Vector3f();var axle=new Vector3f();var upper=new Vector3f();var lower=new Vector3f();
        double rest=d.socket("swingarm_pivot").position().distanceTo(d.socket("rear_axle").position());
        for(float spring:new float[]{.02f,.12f,.22f,.42f}){
            pose.update(d,0,spring);pose.point(d,"swingarm_pivot",pivot);pose.point(d,"rear_axle",axle);
            assertEquals(rest,pivot.distance(axle),1e-5);
            pose.point(d,"shock_upper",upper);pose.point(d,"shock_lower",lower);assertTrue(upper.isFinite()&&lower.isFinite());assertTrue(upper.distance(lower)>.1);
        }
    }
    @Test void definitionsAreIndependentAndInvalidDataFallsBack(){
        var standard=MotorcycleDefinition.DEFAULT;var wide=MotorcycleDefinition.parse("{\"fork_half_width\":0.7,\"final_drive\":\"shaft\"}");
        assertEquals(.48,standard.forkWidth);assertEquals(.7,wide.forkWidth);assertEquals(.7,wide.socket("fork_right").position().x);
        assertSame(standard,MotorcycleDefinition.safe("{\"fork_half_width\":99}"));assertSame(standard,MotorcycleDefinition.safe("bad json"));
        assertThrows(IllegalArgumentException.class,()->MotorcycleDefinition.parse("{\"sockets\":{\"unknown\":{}}}"));
    }
    @Test void limbsReachTargetsAndClampWithoutStretching(){
        var solver=new MotorcycleLimbPose();var origin=new Vector3f(2,12,0);var pole=new Vector3f(6,14,-8);var joint=new Vector3f();var end=new Vector3f();
        for(var target:new Vector3f[]{new Vector3f(6.8f,22,1.4f),new Vector3f(200,0,0),new Vector3f(origin)}){
            solver.solve(origin,target,pole,6,6,joint,end);assertEquals(6,origin.distance(joint),.001);assertEquals(6,joint.distance(end),.001);assertTrue(joint.isFinite()&&end.isFinite());
            if(origin.distance(target)>1&&origin.distance(target)<12)assertEquals(0,end.distance(target),.001);
        }
        assertEquals(0,MotorcycleLimbPose.ease(-1));assertEquals(1,MotorcycleLimbPose.ease(2));assertEquals(.5,MotorcycleLimbPose.ease(.5f));
    }
}
