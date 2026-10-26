package com.createmotorsport.motorcycle.client;

import com.createmotorsport.motorcycle.*;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import java.util.WeakHashMap;

final class MotorcycleRiderState {
    static final class State {
        float previous,weight,lean,bank,throttle,braking;
        int speed;
        MotorcycleDefinition definition=MotorcycleDefinition.DEFAULT;
        final Vector3f leftHand=new Vector3f(),rightHand=new Vector3f(),leftFoot=new Vector3f(),rightFoot=new Vector3f();
        final MotorcycleDefinition.Pose pose=new MotorcycleDefinition.Pose();
    }
    private static final WeakHashMap<AbstractClientPlayer,State> STATES=new WeakHashMap<>();
    private static Level lastLevel;
    static void clear(){STATES.clear();lastLevel=null;MotorcycleLinks.clear();}
    static void tick(){
        var level=Minecraft.getInstance().level;if(level!=lastLevel){clear();lastLevel=level;}if(level==null)return;
        for(var player:level.players())if(player.getVehicle() instanceof MotorcycleSeatEntity)STATES.computeIfAbsent(player,p->new State());
        STATES.entrySet().removeIf(entry->{
            var player=entry.getKey();var state=entry.getValue();
            if(player.isRemoved()||player.level()!=level)return true;
            state.previous=state.weight;
            boolean mounted=player.getVehicle() instanceof MotorcycleSeatEntity seat&&!seat.isRemoved();
            state.weight=Math.clamp(state.weight+(mounted?.125f:-.125f),0,1);
            if(mounted)targets(player,state,1);
            return !mounted&&state.previous==0&&state.weight==0;
        });
    }
    static State get(AbstractClientPlayer player,float partial){
        var state=STATES.get(player);
        if(state==null&&player.getVehicle() instanceof MotorcycleSeatEntity){state=new State();STATES.put(player,state);}
        if(state!=null&&player.getVehicle() instanceof MotorcycleSeatEntity)targets(player,state,partial);
        return state;
    }
    private static void targets(AbstractClientPlayer player,State state,float partial){
        if(!(player.getVehicle() instanceof MotorcycleSeatEntity seat))return;
        var d=seat.definition();state.definition=d;
        toModel(d.socket("foot_left").position(),d,state.leftFoot);toModel(d.socket("foot_right").position(),d,state.rightFoot);
        state.leftHand.set(6,4,-7);state.rightHand.set(-6,4,-7);
        MotorcycleHandlebarBlockEntity bars=null;double distance=9;
        for(var candidate:MotorcycleHandlebarBlockEntity.clientLoaded()){
            if(!(candidate instanceof MotorcycleHandlebarBlockEntity handle)||handle.isRemoved()||handle.getLevel()!=seat.level()||Sable.HELPER.getContaining(handle)!=Sable.HELPER.getContaining(seat))continue;
            double dist=handle.getBlockPos().distToCenterSqr(seat.position());if(dist<distance){distance=dist;bars=handle;}
        }
        state.bank=0;state.throttle=0;
        if(bars!=null){
            var fork=MotorcycleLinks.fork(bars);var geometry=fork==null?bars.appearance().definition:fork.appearance().definition;
            float steering=fork==null?0:fork.getLerpedSteer(WheelSide.LEFT,partial);
            state.pose.update(geometry,steering,(float)com.createmotorsport.block.entity.SuspensionBlockEntity.REST_LENGTH);
            var origin=fork==null?bars.getBlockPos():fork.getBlockPos();
            var facing=bars.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            for(int i=0;i<2;i++){
                var out=i==0?state.leftHand:state.rightHand;
                state.pose.point(geometry,i==0?"grip_left":"grip_right",out);
                out.rotateY((float)Math.toRadians(180-facing.toYRot()));
                out.add((float)(origin.getX()+.5-seat.getX()),(float)(origin.getY()-seat.getY()),(float)(origin.getZ()+.5-seat.getZ()));
                out.rotateY((float)Math.toRadians(seat.getYRot()-180));
                var root=d.socket("seat_root").position();out.set((float)(-(out.x-root.x)*16),(float)(12-(out.y-root.y)*16),(float)((out.z-root.z)*16));
            }
            state.bank=steering*.06f;state.throttle=bars.dashboard().throttlePct()/100f;
            if(partial==1){state.braking=state.braking*.8f+Math.clamp((state.speed-bars.dashboard().speedKmh())*.2f,0,1)*.2f;state.speed=bars.dashboard().speedKmh();}
        }
        state.lean=(float)d.torsoLean+state.braking*.06f-state.throttle*.015f;
    }
    private static void toModel(net.minecraft.world.phys.Vec3 point,MotorcycleDefinition d,Vector3f out){
        var root=d.socket("seat_root").position();out.set((float)(-(point.x-root.x)*16),(float)(12-(point.y-root.y)*16),(float)((point.z-root.z)*16));
    }
}
