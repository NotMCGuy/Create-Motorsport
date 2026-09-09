package com.createmotorsport.trailer;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.fuel.FuelCoordinates;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static com.createmotorsport.trailer.CouplingRules.*;

public class EquipmentBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
    private UUID identity = UUID.randomUUID();
    private UUID owner;
    private String compatibility = "standard";
    private EquipmentRef peer;
    private EquipmentRef ownConnectionRef;
    private GenericConstraintHandle joint;
    private ServerSubLevel jointOther;
    private long lastTick = Long.MIN_VALUE;
    private long cooldownUntil;
    private long nextInteraction;
    private int restoredCooldown = -1;
    private int pendingTicks;
    private int retryTicks;
    private final Edge redstone = new Edge();
    private final Edge legRedstone = new Edge();
    private Status status = Status.OPEN;
    private float jaw;
    private float previousJaw;
    private boolean locked;
    private EquipmentRef hitchLink;
    private EquipmentRef legsLink;
    private int brokenLinkTicks;
    public final LandingSupport legs = new LandingSupport(this);

    public EquipmentBlockEntity(BlockPos pos, BlockState state) { super(TrailerRegistry.EQUIPMENT_ENTITY.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    public UUID identity() { return identity; }
    public Kind kind() { return ((EquipmentBlock) getBlockState().getBlock()).kind; }
    public Direction facing() { return getBlockState().getValue(EquipmentBlock.FACING); }
    public Status status() { return status; }
    public boolean connected() { return locked; }
    public boolean pending() { return peer != null && !locked; }
    public float jaw(float partial) { return net.minecraft.util.Mth.lerp(partial, previousJaw, jaw); }
    public Vec3 localAttachment() {
        double y = kind() == Kind.FIFTH_WHEEL ? .5 : kind() == Kind.KINGPIN ? 7.5/16 : kind() == Kind.TOW_BALL ? 10.0/16 : kind() == Kind.COUPLING_HEAD ? 9.0/16 : .6;
        double z = kind() == Kind.TOW_BALL ? 13.75/16 : kind() == Kind.COUPLING_HEAD ? 3.0/16 : .5;
        var block=(EquipmentBlock)getBlockState().getBlock();
        return Vec3.atLowerCornerOf(worldPosition).add(FuelCoordinates.rotate(
                new Vec3(block.horizontalScale*.5, y, z+(block.depthScale()-1)*.5), facing()));
    }
    public void setOwner(UUID owner) { this.owner = owner; notifyUpdate(); }
    public boolean permitted(Player player) { return player != null && player.mayBuild() && (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)); }
    public boolean canUse(Player player) {
        boolean allowed = permitted(player) && player.level() == level && level.hasChunkAt(worldPosition)
                && FuelCoordinates.inRange(FuelCoordinates.global(level, player.getEyePosition()),
                FuelCoordinates.global(level, Vec3.atCenterOf(worldPosition)), 6);
        if (!allowed && player != null) feedback(player, Status.PERMISSION);
        return allowed;
    }
    public void feedback(Player player, Status value) {
        if (player != null) player.displayClientMessage(Component.translatable("trailer.createmotorsport." + value.name().toLowerCase(java.util.Locale.ROOT)), true);
    }
    public void setStatus(Status value) {
        if (status != value) { status = value; notifyUpdate(); if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); }
    }
    private void sound(boolean connected) {
        var p = SableCoupling.point(this);
        level.playSound(null, p.x, p.y, p.z, connected ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, .8f, connected ? .7f : .9f);
    }
    @Override public void tick() {
        super.tick();
        if (level == null) return;
        if (lastTick == level.getGameTime()) return;
        lastTick = level.getGameTime();
        previousJaw = jaw;
        jaw = (float) CouplingRules.progress(jaw, locked ? 1 : 0, .15);
        legs.clientTick();
        if (level.isClientSide) return;
        if(restoredCooldown>=0) {cooldownUntil=level.getGameTime()+restoredCooldown;restoredCooldown=-1;}
        if (kind() == Kind.LANDING_LEGS) legs.tick();
        if (kind().hitch()) connectionTick();
        if (kind() == Kind.CONTROL_PANEL) panelTick();
        boolean high = level.getBestNeighborSignal(worldPosition) > 0;
        boolean previousHitchSignal=redstone.high(),previousLegSignal=legRedstone.high();
        if (kind() == Kind.CONTROL_PANEL) {
            boolean legHigh = level.getSignal(worldPosition.below(), Direction.DOWN) > 0;
            boolean hitchHigh = false;
            for (Direction d : Direction.values()) if (d != Direction.DOWN) hitchHigh |= level.getSignal(worldPosition.relative(d), d) > 0;
            if (redstone.sample(hitchHigh)) panelAction(null, 0);
            if (legRedstone.sample(legHigh)) panelAction(null, 1);
        } else if (redstone.sample(high)) operate(null, true);
        if(previousHitchSignal!=redstone.high() || previousLegSignal!=legRedstone.high())setChanged();
    }
    @Override public void sable$tick(ServerSubLevel sublevel) { tick(); }
    public void operate(Player player, boolean remote) {
        if (level == null || level.isClientSide || player != null && !canUse(player) && !remote) return;
        if (player != null && !permitted(player)) { feedback(player, Status.PERMISSION); return; }
        if (level.getGameTime() < cooldownUntil) { feedback(player, Status.COOLDOWN); return; }
        if (level.getGameTime() < nextInteraction) return;
        nextInteraction=level.getGameTime()+6;
        if (kind() == Kind.LANDING_LEGS) { legs.toggle(player); return; }
        if (!kind().hitch()) return;
        if (peer != null) release(player, remote);
        else capture(player);
    }
    public List<EquipmentBlockEntity> equipmentOn(ServerSubLevel body) {
        if (body == null) return List.of();
        List<EquipmentBlockEntity> result = new ArrayList<>();
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof EquipmentBlockEntity be && !be.isRemoved()) result.add(be);
            if (result.size() >= 256) break;
        }
        return result;
    }
    private List<EquipmentBlockEntity> nearby() {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        var p = SableCoupling.point(this);
        List<EquipmentBlockEntity> result = new ArrayList<>();
        double range = 3;
        for (var sub : container.queryIntersecting(new BoundingBox3d(p.x-range,p.y-range,p.z-range,p.x+range,p.y+range,p.z+range))) {
            if (!(sub instanceof ServerSubLevel body)) continue;
            for (var be : equipmentOn(body)) if (be != this && be.kind().hitch() && CouplingRules.near(p, SableCoupling.point(be), range)) result.add(be);
            if (result.size() >= 256) break;
        }
        result.sort(java.util.Comparator.comparingDouble(be -> p.distanceSquared(SableCoupling.point(be))));
        return result;
    }
    public Status captureCheck(EquipmentBlockEntity other, boolean restoring) {
        if (!kind().compatible(other.kind()) || !compatibility.equals(other.compatibility)) return Status.WRONG_TYPE;
        var a = SableCoupling.body(this); var b = SableCoupling.body(other);
        if (a == null || b == null || a == b || level != other.level) return Status.INVALID;
        if (!restoring && (peer != null || other.peer != null)) return Status.BUSY;
        if (!owners(owner, other.owner, TrailerConfig.SHARED.get())) return Status.PERMISSION;
        if (level.getGameTime() < cooldownUntil || level.getGameTime() < other.cooldownUntil) return Status.COOLDOWN;
        var p = SableCoupling.point(this); var q = SableCoupling.point(other);
        if (restoring ? !near(p,q,TrailerConfig.CAPTURE.get()*2) : SableCoupling.captureDistance(this,other)>TrailerConfig.CAPTURE.get()) return Status.DISTANCE;
        if (!restoring && !aligned(SableCoupling.orientation(this), SableCoupling.orientation(other), TrailerConfig.ALIGNMENT.get())) return Status.ALIGNMENT;
        double relative = SableCoupling.velocity(a, p).sub(SableCoupling.velocity(b, q)).length();
        if (!speed(relative, TrailerConfig.CAPTURE_SPEED.get())) return Status.SPEED;
        if (kind().fifth()) {
            var trailer = kind() == Kind.KINGPIN ? a : b;
            for (var be : equipmentOn(trailer)) if (be.kind() == Kind.LANDING_LEGS && be.legs.obstructed()) return Status.OBSTRUCTED;
        }
        return Status.OPEN;
    }
    public void capture(Player player) {
        if(level==null || level.isClientSide) return;
        if (SableCoupling.body(this) == null) { setStatus(Status.INVALID); feedback(player, status); return; }
        var nearby = nearby();
        var compatible = nearby.stream().filter(be -> kind().compatible(be.kind()) && SableCoupling.body(be)!=SableCoupling.body(this)).toList();
        EquipmentBlockEntity candidate = compatible.stream().filter(be -> captureCheck(be,false)==Status.OPEN)
                .findFirst().orElse(compatible.isEmpty()?null:compatible.getFirst());
        if (candidate == null) { setStatus(nearby.isEmpty() ? Status.INVALID : Status.WRONG_TYPE); feedback(player, status);
            if(player!=null && nearby.isEmpty())player.displayClientMessage(Component.translatable("trailer.createmotorsport.no_endpoint"),true);return; }
        if (!kind().provider()) { candidate.connect(this, player, false); return; }
        connect(candidate, player, false);
    }
    private void connect(EquipmentBlockEntity other, Player player, boolean restoring) {
        Status check = captureCheck(other, restoring);
        if (check != Status.OPEN) {
            setStatus(check);feedback(player,check);
            if(check==Status.DISTANCE && player!=null && level instanceof net.minecraft.server.level.ServerLevel server) {
                var a=SableCoupling.point(this);var b=SableCoupling.point(other);
                player.displayClientMessage(Component.translatable("trailer.createmotorsport.capture_gap",
                        String.format(java.util.Locale.ROOT,"%.2f",SableCoupling.captureDistance(this,other))),true);
                for(var point:java.util.List.of(a,b))server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        point.x,point.y,point.z,8,.02,.02,.02,0);
            }
            return;
        }
        if (player != null && (!permitted(player) || !other.permitted(player))) { setStatus(Status.PERMISSION); feedback(player,status); return; }
        if (joint != null && joint.isValid()) return;
        try { joint = SableCoupling.create(this, other); }
        catch (RuntimeException failure) {
            CreateMotorsport.LOGGER.error("Could not create trailer constraint at {}", worldPosition, failure);
            joint = null;
        }
        if (joint == null || !joint.isValid()) { setStatus(Status.JOINT_FAILED); feedback(player,status); cooldownUntil=level.getGameTime()+40; return; }
        ownConnectionRef = EquipmentRef.of(this);
        other.ownConnectionRef = EquipmentRef.of(other);
        peer = other.ownConnectionRef;
        other.peer = ownConnectionRef;
        jointOther = SableCoupling.body(other);
        other.jointOther = SableCoupling.body(this);
        locked = other.locked = true;
        pendingTicks = other.pendingTicks = 0;
        setStatus(Status.LOCKED); other.setStatus(Status.LOCKED);
        notifyUpdate(); other.notifyUpdate(); sound(true); feedback(player, Status.LOCKED);
    }
    private void connectionTick() {
        if (peer != null) {
            if (ownConnectionRef == null || !ownConnectionRef.equals(EquipmentRef.of(this))) { clear(Status.INVALID, true); return; }
            var other = peer.resolve(level);
            if (other == null) {
                dropJoint(); locked=false; setStatus(Status.PENDING);
                if (++pendingTicks > 200) clear(Status.INVALID, true);
                return;
            }
            if (other.peer == null || !reciprocal(identity,other.identity,other.peer.endpoint(),peer.endpoint())
                    || !other.peer.equals(EquipmentRef.of(this)) || !peer.equals(EquipmentRef.of(other))) { clear(Status.INVALID,true); return; }
            if (SableCoupling.body(this) == null || SableCoupling.body(other) == null) { clear(Status.INVALID,true); return; }
            if (kind().provider()) {
                if (joint == null) {
                    locked=false;
                    if (++pendingTicks > 200) { clear(Status.INVALID,true); return; }
                    if (level.getGameTime() % 10 == 0) connect(other,null,true);
                } else if (!joint.isValid()) clear(Status.BROKEN,true);
                else { locked=true; other.locked=true; pendingTicks=0; }
            } else {
                locked=other.joint != null && other.joint.isValid();
                setStatus(locked ? Status.LOCKED : Status.PENDING);
            }
            return;
        }
        if (status == Status.RELEASING && level.getGameTime() >= cooldownUntil) setStatus(Status.OPEN);
        if (kind().provider() && (kind()==Kind.FIFTH_WHEEL || TrailerConfig.BALL_AUTO.get())
                && level.getGameTime() >= cooldownUntil && ++retryTicks >= 10) {
            retryTicks=0;
            var candidates=nearby();
            for (var other : candidates) {
                if (!kind().compatible(other.kind()) || other.peer != null) continue;
                Status check=captureCheck(other,false);
                if (check==Status.OPEN) { setStatus(Status.ALIGNING); connect(other,null,false); break; }
                setStatus(check);
            }
        }
    }
    public boolean supportedTrailer(EquipmentBlockEntity other) {
        var trailer = SableCoupling.body(kind()==Kind.KINGPIN ? this : other);
        for (var be : equipmentOn(trailer)) if (be.kind()==Kind.LANDING_LEGS && be.legs.supported()) return true;
        return false;
    }
    public void release(Player player, boolean remote) {
        if(level==null || level.isClientSide) return;
        var other = peer == null ? null : peer.resolve(level);
        if (!kind().provider() && other != null) { other.release(player,remote); return; }
        if (player != null && (other==null || !permitted(player) || !other.permitted(player))) { feedback(player,Status.PERMISSION); return; }
        var a=SableCoupling.body(this); var b=other==null?null:SableCoupling.body(other);
        var p=SableCoupling.point(this); var q=other==null?p:SableCoupling.point(other);
        var va=SableCoupling.velocity(a,p); var vb=SableCoupling.velocity(b,q);
        Status check=CouplingRules.release(remote,TrailerConfig.REMOTE_RELEASE.get(),a!=null && b!=null && joint!=null && joint.isValid(),
                Math.max(va.length(),vb.length()),va.distance(vb),TrailerConfig.SAFE_SPEED.get(),kind().fifth(),
                other!=null && supportedTrailer(other),TrailerConfig.UNSAFE_RELEASE.get());
        if(check!=Status.OPEN) {setStatus(check);feedback(player,check);return;}
        clear(Status.RELEASING,true);sound(false);feedback(player,Status.RELEASING);
    }
    private void dropJoint() {
        if(joint!=null && joint.isValid()) joint.remove();
        joint=null; jointOther=null;
    }
    private void clear(Status reason, boolean otherToo) {
        var other=peer==null || level==null?null:peer.resolve(level);
        dropJoint(); locked=false; peer=null;ownConnectionRef=null;pendingTicks=0;
        cooldownUntil=level==null?0:level.getGameTime()+TrailerConfig.COOLDOWN.get();
        setStatus(reason);notifyUpdate();
        if(otherToo && other!=null && other.peer!=null && other.peer.endpoint().equals(identity)) other.clear(reason,false);
    }
    public void destroyEquipment() { if(level!=null && !level.isClientSide) { clear(Status.INVALID,true); legs.clearContact(); } }
    @Override public void remove() { destroyEquipment();super.remove(); }
    @Override public void onChunkUnloaded() {
        if(level!=null && !level.isClientSide) { dropJoint();locked=false;legs.clearContact(); }
        super.onChunkUnloaded();
    }
    @Override public Iterable<SubLevel> sable$getLoadingDependencies() { return jointOther==null ? List.of() : List.of(jointOther); }
    @Override public void sable$physicsTick(ServerSubLevel body, RigidBodyHandle handle, double dt) {
        if(kind()==Kind.LANDING_LEGS) legs.physics(body,handle,dt);
        if(joint!=null && joint.isValid() && !TrailerConfig.UNBREAKABLE.get()) {
            Vector3d linear=new Vector3d(),angular=new Vector3d();joint.getJointImpulses(linear,angular);
            if(linear.length()/dt > TrailerConfig.BREAK_FORCE.get() || angular.length()/dt > TrailerConfig.BREAK_TORQUE.get()) {
                clear(Status.BROKEN,true);sound(false);
                var p=SableCoupling.point(this);
                for(Player player:level.players()) if(player.distanceToSqr(p.x,p.y,p.z)<1024) feedback(player,Status.BROKEN);
            }
        }
    }
    public int comparator() {
        if(status.fault()) return 15;
        if(kind()==Kind.CONTROL_PANEL) return hitchLink==null && legsLink==null ? 0 : 3;
        if(locked || kind()==Kind.LANDING_LEGS && legs.supported()) return 14;
        if(pending() || kind()==Kind.LANDING_LEGS && legs.moving()) return 8;
        return 3;
    }
    public boolean link(EquipmentBlockEntity target, Player player) {
        if(kind()!=Kind.CONTROL_PANEL || target==this || !permitted(player) || !target.permitted(player)
                || !near(SableCoupling.point(this),SableCoupling.point(target),TrailerConfig.LINK_RANGE.get())) return false;
        if(target.kind().hitch()) hitchLink=EquipmentRef.of(target);
        else if(target.kind()==Kind.LANDING_LEGS) legsLink=EquipmentRef.of(target);
        else return false;
        brokenLinkTicks=0;setStatus(Status.OPEN);notifyUpdate();return true;
    }
    public EquipmentBlockEntity linked(boolean leg) {
        var ref=leg?legsLink:hitchLink;
        var target=ref==null?null:ref.resolve(level);
        return target!=null && near(SableCoupling.point(this),SableCoupling.point(target),TrailerConfig.LINK_RANGE.get()) ? target:null;
    }
    public void panelAction(Player player,int action) {
        if(level==null || level.isClientSide || kind()!=Kind.CONTROL_PANEL || action<0 || action>2) return;
        if(player!=null && !canUse(player)) return;
        if(level.getGameTime()<cooldownUntil) return;
        cooldownUntil=level.getGameTime()+10;
        if(action==2) {hitchLink=null;legsLink=null;setStatus(Status.UNLINKED);notifyUpdate();return;}
        var target=linked(action==1);
        if(target==null) {setStatus(Status.UNLINKED);feedback(player,status);return;}
        if(player!=null && !target.permitted(player)) {setStatus(Status.PERMISSION);feedback(player,status);return;}
        target.operate(player,true);setStatus(target.status());
    }
    private void panelTick() {
        if(hitchLink==null && legsLink==null) return;
        if(hitchLink!=null && hitchLink.resolve(level)==null || legsLink!=null && legsLink.resolve(level)==null) {
            if(++brokenLinkTicks>200) {
                if(hitchLink!=null && hitchLink.resolve(level)==null) hitchLink=null;
                if(legsLink!=null && legsLink.resolve(level)==null) legsLink=null;
                setStatus(Status.UNLINKED);notifyUpdate();
            }
        } else brokenLinkTicks=0;
    }
    @Override protected net.minecraft.world.phys.AABB createRenderBoundingBox() { return new net.minecraft.world.phys.AABB(worldPosition).inflate(6); }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket) {
        super.write(tag,registries,clientPacket);
        tag.putUUID("Identity",identity);if(owner!=null) tag.putUUID("Owner",owner);
        tag.putString("Compatibility",compatibility);
        if(peer!=null) tag.put("Peer",peer.save());if(ownConnectionRef!=null) tag.put("ConnectionSelf",ownConnectionRef.save());
        if(hitchLink!=null) tag.put("HitchLink",hitchLink.save());if(legsLink!=null) tag.put("LegsLink",legsLink.save());
        tag.putBoolean("Redstone",redstone.high());tag.putBoolean("LegRedstone",legRedstone.high());
        tag.putInt("Status",status.ordinal());tag.putBoolean("Locked",clientPacket && locked);
        tag.putInt("Cooldown",level==null?0:(int)Math.clamp(cooldownUntil-level.getGameTime(),0,1200));
        legs.write(tag);
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket) {
        super.read(tag,registries,clientPacket);
        if(tag.hasUUID("Identity")) identity=tag.getUUID("Identity");
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        compatibility=tag.contains("Compatibility")?tag.getString("Compatibility"):"standard";
        peer=EquipmentRef.load(tag.getCompound("Peer"));ownConnectionRef=EquipmentRef.load(tag.getCompound("ConnectionSelf"));
        hitchLink=EquipmentRef.load(tag.getCompound("HitchLink"));legsLink=EquipmentRef.load(tag.getCompound("LegsLink"));
        redstone.restore(tag.getBoolean("Redstone"));legRedstone.restore(tag.getBoolean("LegRedstone"));
        locked=clientPacket && tag.getBoolean("Locked");
        status=Status.values()[Math.clamp(tag.getInt("Status"),0,Status.values().length-1)];
        if(!clientPacket) {dropJoint();pendingTicks=0;restoredCooldown=Math.clamp(tag.getInt("Cooldown"),0,1200);cooldownUntil=(level==null?0:level.getGameTime())+restoredCooldown;if(peer!=null)status=Status.PENDING;}
        legs.read(tag,clientPacket);
    }
}
