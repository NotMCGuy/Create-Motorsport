package com.createmotorsport.trailer;

import com.createmotorsport.fuel.FuelCoordinates;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import static com.createmotorsport.trailer.CouplingRules.*;

public final class LandingSupport {
    private final EquipmentBlockEntity be;
    private boolean deploying;
    private boolean blocked;
    private final double[] extension = new double[2];
    private final double[] visual = new double[2];
    private final float[] client = new float[2];
    private final float[] previous = new float[2];
    private final boolean[] contact = new boolean[2];
    private int supportedTicks;
    private long lastContactTick = Long.MIN_VALUE;
    private long nextAction;
    private record Ground(double distance, Vec3 global, Vec3 normal, ServerSubLevel body, boolean blocked) {}
    public LandingSupport(EquipmentBlockEntity be) { this.be=be; }
    public boolean obstructed() { return blocked; }
    public boolean moving() { return be.status()==Status.DEPLOYING || be.status()==Status.RETRACTING; }
    public boolean supported() { return deploying && supportedTicks>=10 && lastContactTick>=be.getLevel().getGameTime()-2; }
    public boolean deployed() { return deploying; }
    public double length(int side,float partial) { return net.minecraft.util.Mth.lerp(partial,previous[side],client[side]); }
    public int progress() {
        if (supported()) return 100;
        return (int)Math.clamp((extension[0]+extension[1])*50/TrailerConfig.LEG_REACH.get(),0,100);
    }
    public void clientTick() {
        for(int i=0;i<2;i++) {previous[i]=client[i];client[i]+=(float)(visual[i]-client[i])*.45f;}
    }
    public Vec3 mount(int side) {
        return Vec3.atLowerCornerOf(be.getBlockPos()).add(FuelCoordinates.rotate(new Vec3(side==0?-.15:1.15,.2,.5),be.facing()));
    }
    private Ground ground(ServerSubLevel body,int side) {
        var level=be.getLevel();
        Vec3 local=mount(side);
        double reach=TrailerConfig.LEG_REACH.get();
        for(double d=.15;d<=reach;d+=.2) {
            BlockPos pos=BlockPos.containing(local.add(0,-d,0));
            if(!pos.equals(be.getBlockPos()) && !level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()) {
                return new Ground(d,Vec3.ZERO,Vec3.ZERO,null,true);
            }
        }
        Vec3 start=body.logicalPose().transformPosition(local);
        Vec3 end=body.logicalPose().transformPosition(local.add(0,-reach-.2,0));
        ClipContext context=new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty());
        ((ClipContextExtension)context).sable$setIgnoredSubLevel(body);
        var hit=level.clip(context);
        if(hit.getType()==HitResult.Type.MISS) return new Ground(reach+.2,end,Vec3.ZERO,null,false);
        var hitBody=Sable.HELPER.getContaining(level,hit.getBlockPos());
        Vec3 world=hitBody==null?hit.getLocation():hitBody.logicalPose().transformPosition(hit.getLocation());
        Vec3 inLocal=body.logicalPose().transformPositionInverse(world);
        Vector3d normal=new Vector3d(hit.getDirection().getStepX(),hit.getDirection().getStepY(),hit.getDirection().getStepZ());
        if(hitBody!=null)hitBody.logicalPose().transformNormal(normal);
        return new Ground(local.y-inLocal.y,world,new Vec3(normal.x,normal.y,normal.z),
                hitBody instanceof ServerSubLevel server?server:null,false);
    }
    public void toggle(Player player) {
        var level=be.getLevel();
        if(level.getGameTime()<nextAction) {be.feedback(player,Status.COOLDOWN);return;}
        nextAction=level.getGameTime()+10;
        var body=SableCoupling.body(be);
        if(body==null) {be.setStatus(Status.INVALID);be.feedback(player,Status.INVALID);return;}
        if(SableCoupling.velocity(body,SableCoupling.point(be)).length()>TrailerConfig.SAFE_SPEED.get()) {
            be.setStatus(Status.SPEED);be.feedback(player,Status.SPEED);return;
        }
        boolean coupled=be.equipmentOn(body).stream().anyMatch(e->e.kind().hitch() && e.connected());
        if(deploying && !coupled && !TrailerConfig.UNSAFE_RETRACT.get()) {
            be.setStatus(Status.UNSUPPORTED);be.feedback(player,Status.UNSUPPORTED);return;
        }
        deploying=!deploying;blocked=false;clearContact();
        be.setStatus(deploying?Status.DEPLOYING:Status.RETRACTING);be.notifyUpdate();
    }
    public void tick() {
        var body=SableCoupling.body(be);
        if(body==null) {clearContact();if(deploying)be.setStatus(Status.INVALID);return;}
        boolean changed=false;
        boolean travel=false;
        blocked=false;
        double step=TrailerConfig.LEG_REACH.get()/TrailerConfig.LEG_TICKS.get();
        for(int i=0;i<2;i++) {
            Ground ground=ground(body,i);
            blocked|=ground.blocked;
            double target=deploying?Math.min(TrailerConfig.LEG_REACH.get(),Math.max(0,ground.distance+.075)):0;
            if(deploying && contact[i] && lastContactTick>=be.getLevel().getGameTime()-2)target=extension[i];
            if(ground.blocked && deploying)target=Math.max(0,ground.distance-.15);
            double old=extension[i];
            double oldVisual=visual[i];
            extension[i]+=Math.clamp(target-extension[i],-step,step);
            visual[i]=Math.min(extension[i],Math.max(0,ground.distance));
            travel|=Math.abs(extension[i]-target)>.005;
            changed|=Math.abs(extension[i]-old)>1e-5 || Math.abs(visual[i]-oldVisual)>1e-4;
        }
        if(lastContactTick>=be.getLevel().getGameTime()-2 && contact[0] && contact[1])supportedTicks++;
        else supportedTicks=0;
        Status next=blocked?Status.OBSTRUCTED:deploying?(supported()?Status.DEPLOYED:travel?Status.DEPLOYING:Status.UNSUPPORTED)
                :travel?Status.RETRACTING:Status.RETRACTED;
        if(next!=be.status() && (next==Status.DEPLOYED || next==Status.RETRACTED))sound(true);
        be.setStatus(next);
        if(changed && be.getLevel().getGameTime()%4==0)be.notifyUpdate();
        if(changed && be.getLevel().getGameTime()%20==0)sound(false);
    }
    private void sound(boolean done) {
        var p=SableCoupling.point(be);
        be.getLevel().playSound(null,p.x,p.y,p.z,done?SoundEvents.IRON_TRAPDOOR_CLOSE:SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,.4f,done?.7f:.6f);
    }
    public void physics(ServerSubLevel body,RigidBodyHandle handle,double dt) {
        lastContactTick=be.getLevel().getGameTime();
        double mass=body.getMassTracker().getMass();
        if(!Double.isFinite(mass) || mass<=0) {clearContact();return;}
        Vector3d up=body.logicalPose().transformNormal(new Vector3d(0,1,0));
        if(up.y<.4) {clearContact();return;}
        for(int i=0;i<2;i++) {
            contact[i]=false;
            if(extension[i]<=0)continue;
            Ground ground=ground(body,i);
            if(ground.blocked || ground.distance<0 || ground.distance>extension[i] || ground.normal.dot(new Vec3(up.x,up.y,up.z))<.4)continue;
            Vector3d world=new Vector3d(ground.global.x,ground.global.y,ground.global.z);
            Vector3d velocity=SableCoupling.velocity(body,world).sub(SableCoupling.velocity(ground.body,world));
            double omega=2*Math.PI*TrailerConfig.LEG_HZ.get();
            double effectiveMass=mass*.5;
            double compression=Math.min(.15,extension[i]-ground.distance);
            double force=effectiveMass*omega*omega*compression
                    -2*TrailerConfig.LEG_DAMPING.get()*effectiveMass*omega*velocity.dot(up);
            force=Math.clamp(force,0,mass*9.81*2);
            if(force<=0)continue;
            Vec3 local=mount(i).add(0,-ground.distance,0);
            Vector3d impulse=new Vector3d(up).mul(force*dt);
            Vector3d localImpulse=body.logicalPose().transformNormalInverse(new Vector3d(impulse));
            handle.applyImpulseAtPoint(new Vector3d(local.x,local.y,local.z),localImpulse);
            if(ground.body!=null && ground.body!=body) {
                var other=RigidBodyHandle.of(ground.body);
                if(other!=null)other.applyImpulseAtPoint(ground.body.logicalPose().transformPositionInverse(new Vector3d(world)),
                        ground.body.logicalPose().transformNormalInverse(new Vector3d(impulse).negate()));
            }
            contact[i]=true;
        }
    }
    public void clearContact() {contact[0]=contact[1]=false;supportedTicks=0;lastContactTick=Long.MIN_VALUE;}
    public void write(CompoundTag tag) {
        tag.putBoolean("LegDeploy",deploying);tag.putDouble("LegLeft",extension[0]);tag.putDouble("LegRight",extension[1]);
        tag.putDouble("LegVisualLeft",visual[0]);tag.putDouble("LegVisualRight",visual[1]);
        tag.putBoolean("LegSupported",supportedTicks>=10);tag.putBoolean("LegBlocked",blocked);
    }
    public void read(CompoundTag tag,boolean clientPacket) {
        deploying=tag.getBoolean("LegDeploy");blocked=tag.getBoolean("LegBlocked");
        extension[0]=Math.clamp(tag.getDouble("LegLeft"),0,5);extension[1]=Math.clamp(tag.getDouble("LegRight"),0,5);
        visual[0]=Math.clamp(tag.getDouble("LegVisualLeft"),0,5);visual[1]=Math.clamp(tag.getDouble("LegVisualRight"),0,5);
        if(!clientPacket)clearContact();
    }
}
