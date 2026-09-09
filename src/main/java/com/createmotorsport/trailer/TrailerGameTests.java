package com.createmotorsport.trailer;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder("createmotorsport")
@PrefixGameTestTemplate(false)
public final class TrailerGameTests {
    private record Pair(ServerSubLevel first,ServerSubLevel second,EquipmentBlockEntity hitch,EquipmentBlockEntity head) {}
    private static Pair pair(GameTestHelper helper,boolean fifth) {
        var p=helper.absolutePos(new BlockPos(5,6,5));
        Vector3d position=new Vector3d(p.getX(),p.getY(),p.getZ());
        var a=TrailerTestRig.body(helper.getLevel(),position);
        var b=TrailerTestRig.body(helper.getLevel(),new Vector3d(position).add(0,fifth?1.0/32:1.0/16,fifth?0:43.0/64));
        var hitch=TrailerTestRig.equipment(a,BlockPos.ZERO,fifth?TrailerRegistry.FIFTH_WHEEL.get():TrailerRegistry.TOW_BALL.get());
        var head=TrailerTestRig.equipment(b,BlockPos.ZERO,fifth?TrailerRegistry.KINGPIN.get():TrailerRegistry.COUPLING_HEAD.get());
        TrailerTestRig.finishPlacement(a,position);
        TrailerTestRig.finishPlacement(b,new Vector3d(position).add(0,fifth?1.0/32:1.0/16,fifth?0:43.0/64));
        return new Pair(a,b,hitch,head);
    }
    @GameTest(template="trailer_empty",timeoutTicks=100)
    public static void towBallOpposingPlacementAllDirections(GameTestHelper helper) {
        boolean wasForced=helper.getLevel().getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(16,16));
        helper.getLevel().setChunkForced(16,16,true);
        helper.getLevel().getChunk(16,16);
        java.util.List<Pair> pairs=new java.util.ArrayList<>();
        int index=0;
        for(var heading:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var p=new BlockPos(260+index++*3,80,260);
            var position=new Vector3d(p.getX(),p.getY(),p.getZ());
            var rear=heading.getOpposite();
            var first=TrailerTestRig.body(helper.getLevel(),position);
            var secondPosition=new Vector3d(position).add(rear.getStepX(),0,rear.getStepZ());
            var second=TrailerTestRig.body(helper.getLevel(),secondPosition);
            var ballFacing=EquipmentBlock.placementFacing(CouplingRules.Kind.TOW_BALL,heading,rear);
            var headFacing=EquipmentBlock.placementFacing(CouplingRules.Kind.COUPLING_HEAD,rear,heading);
            TrailerTestRig.place(first,BlockPos.ZERO,TrailerRegistry.TOW_BALL.get().defaultBlockState().setValue(EquipmentBlock.FACING,ballFacing));
            TrailerTestRig.place(second,BlockPos.ZERO,TrailerRegistry.COUPLING_HEAD.get().defaultBlockState().setValue(EquipmentBlock.FACING,headFacing));
            var ball=(EquipmentBlockEntity)helper.getLevel().getBlockEntity(first.getPlot().getCenterBlock());
            var head=(EquipmentBlockEntity)helper.getLevel().getBlockEntity(second.getPlot().getCenterBlock());
            TrailerTestRig.finishPlacement(first,position);TrailerTestRig.finishPlacement(second,secondPosition);
            pairs.add(new Pair(first,second,ball,head));
        }
        helper.startSequence().thenExecuteAfter(2,()->{
            for(var pair:pairs) {pair.hitch.capture(null);locked(helper,pair);}
        }).thenExecuteAfter(8,()->{
            for(var pair:pairs) {locked(helper,pair);remove(helper,pair.first,pair.second);}
            if(!wasForced)helper.getLevel().setChunkForced(16,16,false);
        }).thenSucceed();
    }
    private static void remove(GameTestHelper helper,ServerSubLevel... bodies) {
        var container=SubLevelContainer.getContainer(helper.getLevel());
        for(var body:bodies)if(!body.isRemoved())container.removeSubLevel(body,SubLevelRemovalReason.REMOVED);
    }
    private static void stop(ServerSubLevel body) {
        var handle=RigidBodyHandle.of(body);
        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),handle.getAngularVelocity(new Vector3d()).negate());
    }
    private static void locked(GameTestHelper helper,Pair p) {
        helper.assertTrue(p.hitch.connected() && p.head.connected(),"Both endpoints must report a physical joint: "+p.hitch.status()+" / "+p.head.status()
                +"; check="+p.hitch.captureCheck(p.head,false)+"; anchors="+SableCoupling.point(p.hitch)+" / "+SableCoupling.point(p.head)
                +"; delta="+SableCoupling.point(p.head).sub(SableCoupling.point(p.hitch))+"; facing="+p.hitch.facing()+" / "+p.head.facing()+"; actors="+p.hitch.equipmentOn(p.first).size()+" / "+p.hitch.equipmentOn(p.second).size());
        helper.assertTrue(!p.first.getUniqueId().equals(p.second.getUniqueId()),"Coupling must retain two independent bodies");
        helper.assertTrue(SableCoupling.point(p.hitch).distance(SableCoupling.point(p.head))<.35,"Joint anchors separated");
    }
    @GameTest(template="trailer_empty",timeoutTicks=160)
    public static void fifthWheelCaptureRestoreAndBreak(GameTestHelper helper) {
        var p=pair(helper,true);
        helper.startSequence().thenExecuteAfter(12,()->{
            locked(helper,p);
            p.hitch.capture(null);p.head.capture(null);
            locked(helper,p);
            var a=new CompoundTag();var b=new CompoundTag();
            p.hitch.write(a,helper.getLevel().registryAccess(),false);p.head.write(b,helper.getLevel().registryAccess(),false);
            helper.assertTrue(!a.getBoolean("Locked") && a.contains("Peer"),"Save logical metadata rather than a runtime lock");
            p.hitch.read(a,helper.getLevel().registryAccess(),false);p.head.read(b,helper.getLevel().registryAccess(),false);
            helper.assertTrue(p.hitch.pending() && !p.hitch.connected(),"Restored relationship must await a physical joint");
        }).thenExecuteAfter(12,()->{
            locked(helper,p);
            helper.getLevel().setBlock(p.hitch.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
            helper.assertTrue(!p.head.connected() && !p.head.pending(),"Breaking a hitch must clear the opposite endpoint");
            remove(helper,p.first,p.second);
        }).thenSucceed();
    }
    @GameTest(template="trailer_empty",timeoutTicks=120)
    public static void towBallReleaseCooldownAndVelocity(GameTestHelper helper) {
        var p=pair(helper,false);
        helper.startSequence().thenExecuteAfter(2,()->{
            helper.assertTrue(!p.hitch.connected(),"Tow ball must not capture automatically by default");
            p.hitch.capture(null);locked(helper,p);
            p.hitch.release(null,true);
            helper.assertTrue(p.hitch.connected() && p.hitch.status()==CouplingRules.Status.REMOTE_DISABLED,"Remote release is opt-in");
            stop(p.first);stop(p.second);
            var before=RigidBodyHandle.of(p.first).getLinearVelocity(new Vector3d());
            p.hitch.release(null,false);
            helper.assertTrue(!p.hitch.connected() && !p.head.connected(),"Direct release must remove the joint");
            helper.assertTrue(before.distance(RigidBodyHandle.of(p.first).getLinearVelocity(new Vector3d()))<1e-8,"Release must preserve velocity");
            p.head.capture(null);
            helper.assertTrue(!p.hitch.connected() && !p.head.connected(),"Cooldown must prevent immediate recapture");
            remove(helper,p.first,p.second);
        }).thenSucceed();
    }
    @GameTest(template="trailer_empty",timeoutTicks=280)
    public static void missingEndpointTimesOut(GameTestHelper helper) {
        var p=pair(helper,false);
        helper.startSequence().thenExecuteAfter(2,()->{
            p.hitch.capture(null);locked(helper,p);
            var tag=new CompoundTag();p.hitch.write(tag,helper.getLevel().registryAccess(),false);
            tag.getCompound("Peer").putUUID("Endpoint",java.util.UUID.randomUUID());
            p.hitch.read(tag,helper.getLevel().registryAccess(),false);
        }).thenExecuteAfter(210,()->{
            helper.assertTrue(!p.hitch.connected() && !p.hitch.pending(),"Missing endpoint must clear pending metadata");
            helper.assertTrue(!p.head.connected() && !p.head.pending(),"Reciprocal endpoint must reject a stale relationship");
            remove(helper,p.first,p.second);
        }).thenSucceed();
    }
    @GameTest(template="trailer_empty",timeoutTicks=200)
    public static void landingLegSupportAndTerrainLoss(GameTestHelper helper) {
        for(int x=2;x<=10;x++)for(int z=2;z<=10;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var p=helper.absolutePos(new BlockPos(6,2,6));
        var body=TrailerTestRig.body(helper.getLevel(),new Vector3d(p.getX(),p.getY(),p.getZ()));
        var legs=TrailerTestRig.equipment(body,BlockPos.ZERO,TrailerRegistry.LANDING_LEGS.get());
        TrailerTestRig.finishPlacement(body,new Vector3d(p.getX(),p.getY(),p.getZ()));
        var state=new CompoundTag();state.putBoolean("LegDeploy",true);state.putDouble("LegLeft",1.275);state.putDouble("LegRight",1.275);
        legs.legs.read(state,false);
        helper.startSequence().thenExecuteAfter(70,()->{
            helper.assertTrue(legs.legs.supported(),"Both feet must support a freely moving body: "+legs.status());
            helper.assertTrue(SableCoupling.point(legs).y>p.getY()-.5,"Support must keep the body above its own collision box on the ground");
            legs.legs.toggle(null);
            helper.assertTrue(legs.legs.deployed(),"Uncoupled retraction must be refused");
            for(int x=2;x<=10;x++)for(int z=2;z<=10;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.AIR);
        }).thenExecuteAfter(6,()->{
            helper.assertTrue(!legs.legs.supported(),"Removed terrain must invalidate support immediately");
            remove(helper,body);
        }).thenSucceed();
    }
}
