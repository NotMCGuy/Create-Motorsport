package com.createmotorsport.motorcycle.client;

import com.createmotorsport.motorcycle.MotorcycleLimbPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

final class MotorcyclePlayerRenderer extends PlayerRenderer {
    MotorcyclePlayerRenderer(EntityRendererProvider.Context context,boolean slim){
        super(context,slim);model=new RiderModel(mesh(slim),slim);
    }
    private static ModelPart mesh(boolean slim){
        var mesh=PlayerModel.createMesh(CubeDeformation.NONE,slim);var root=mesh.getRoot();
        limb(root,"left_arm",32,48,-1,slim?3:4,5,slim?2.5f:2,-2,4,0);
        limb(root,"right_arm",40,16,slim?-2:-3,slim?3:4,-5,slim?2.5f:2,-2,4,0);
        limb(root,"left_sleeve",48,48,-1,slim?3:4,5,slim?2.5f:2,-2,4,.25f);
        limb(root,"right_sleeve",40,32,slim?-2:-3,slim?3:4,-5,slim?2.5f:2,-2,4,.25f);
        limb(root,"left_leg",16,48,-2,4,1.9f,12,0,6,0);limb(root,"right_leg",0,16,-2,4,-1.9f,12,0,6,0);
        limb(root,"left_pants",0,48,-2,4,1.9f,12,0,6,.25f);limb(root,"right_pants",0,32,-2,4,-1.9f,12,0,6,.25f);
        return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    private static void limb(PartDefinition root,String name,int u,int v,float x,float width,float originX,float originY,float top,float joint,float inflate){
        var part=root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,top,-2,width,6,4,new CubeDeformation(inflate)),PartPose.offset(originX,originY,0));
        part.addOrReplaceChild("lower",CubeListBuilder.create().texOffs(u,v+6).addBox(x,0,-2,width,6,4,new CubeDeformation(inflate)),PartPose.offset(0,joint,0));
    }
    private static final class RiderModel extends PlayerModel<AbstractClientPlayer> {
        private final List<ModelPart> all;
        private final MotorcycleLimbPose solver=new MotorcycleLimbPose();
        private final Vector3f origin=new Vector3f(),pole=new Vector3f(),joint=new Vector3f(),end=new Vector3f(),direction=new Vector3f();
        private final Quaternionf inverse=new Quaternionf();
        RiderModel(ModelPart root,boolean slim){super(root,slim);all=root.getAllParts().toList();}
        @Override public void setupAnim(AbstractClientPlayer player,float walk,float amount,float age,float yaw,float pitch){
            for(var part:all)part.resetPose();
            super.setupAnim(player,walk,amount,age,yaw,pitch);
            float partial=Math.clamp(age-player.tickCount,0,1);var state=MotorcycleRiderState.get(player,partial);if(state==null)return;
            float blend=MotorcycleLimbPose.ease(Mth.lerp(partial,state.previous,state.weight));
            float hip=(float)state.definition.hipAngle;float lean=state.lean+hip;
            float shoulderY=12-10*(float)Math.cos(lean),shoulderZ=-10*(float)Math.sin(lean);
            body.xRot=Mth.lerp(blend,body.xRot,lean);body.y=Mth.lerp(blend,body.y,12-12*(float)Math.cos(lean));body.z=Mth.lerp(blend,body.z,-12*(float)Math.sin(lean));body.zRot=Mth.lerp(blend,body.zRot,state.bank);
            head.y=Mth.lerp(blend,head.y,12-12*(float)Math.cos(lean));head.z=Mth.lerp(blend,head.z,-12*(float)Math.sin(lean));head.xRot+=blend*lean*(1-(float)state.definition.headCompensation);
            pose(leftArm,state.leftHand,5,shoulderY,shoulderZ,4,6,8,8,0,blend);
            pose(rightArm,state.rightHand,-5,shoulderY,shoulderZ,4,6,-8,8,0,blend);
            pose(leftLeg,state.leftFoot,1.9f,12,0,6,6,6,14,-8,blend);
            pose(rightLeg,state.rightFoot,-1.9f,12,0,6,6,-6,14,-8,blend);
            hat.copyFrom(head);jacket.copyFrom(body);
            copy(leftSleeve,leftArm);copy(rightSleeve,rightArm);copy(leftPants,leftLeg);copy(rightPants,rightLeg);
        }
        private void pose(ModelPart part,Vector3f target,float x,float y,float z,float upper,float lower,float px,float py,float pz,float blend){
            origin.set(x,y,z);pole.set(px,py,pz);solver.solve(origin,target,pole,upper,lower,joint,end);
            direction.set(joint).sub(origin).normalize();float rx=(float)Math.asin(Math.clamp(direction.z,-1,1));float rz=(float)Math.atan2(-direction.x,direction.y);
            part.x=Mth.lerp(blend,part.x,x);part.y=Mth.lerp(blend,part.y,y);part.z=Mth.lerp(blend,part.z,z);
            part.xRot=rotLerp(blend,part.xRot,rx);part.yRot=rotLerp(blend,part.yRot,0);part.zRot=rotLerp(blend,part.zRot,rz);
            inverse.rotationZYX(rz,0,rx).invert();direction.set(end).sub(joint).normalize().rotate(inverse);
            var child=part.getChild("lower");child.xRot=blend*(float)Math.asin(Math.clamp(direction.z,-1,1));child.zRot=blend*(float)Math.atan2(-direction.x,direction.y);
        }
        private static float rotLerp(float t,float a,float b){return a+t*(float)Math.atan2(Math.sin(b-a),Math.cos(b-a));}
        private void copy(ModelPart outer,ModelPart inner){outer.copyFrom(inner);outer.getChild("lower").copyFrom(inner.getChild("lower"));}
    }
}
