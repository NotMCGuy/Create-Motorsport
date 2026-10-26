package com.createmotorsport.physics.world;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import com.createmotorsport.motorcycle.MotorcycleModule;
import com.createmotorsport.trailer.TrailerTestRig;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import java.util.ArrayList;

@GameTestHolder("createmotorsport")
@PrefixGameTestTemplate(false)
public final class ScaledSuspensionGameTests {
    private record Rig(ServerSubLevel body, SuspensionBlockEntity front, SuspensionBlockEntity rear,
                       double scale, boolean bike) { }

    @GameTest(template = "trailer_empty", timeoutTicks = 240)
    public static void pocketSizedVehiclesSettleAfterShrinking(GameTestHelper helper) {
        if (!PocketSizeCompat.available()) {
            CreateMotorsport.LOGGER.info("Pocket Sized integration test skipped; enable -PpocketCompat to run it");
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var forced = new ArrayList<ChunkPos>();
        for (int x = 32; x <= 34; x++) {
            if (!level.getForcedChunks().contains(ChunkPos.asLong(x, 25))) {
                level.setChunkForced(x, 25, true);
                forced.add(new ChunkPos(x, 25));
            }
        }
        for (int x = 512; x < 560; x++) for (int z = 400; z < 416; z++)
            level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
        var rigs = new ArrayList<Rig>();
        double[] scales = {0.5, 0.25, 0.125, 0.0625, 0.03125};
        for (boolean bike : new boolean[]{false, true}) for (int i = 0; i < scales.length; i++) {
            var position = new Vector3d(516 + i * 8, 81, bike ? 411 : 404);
            var body = TrailerTestRig.body(level, position);
            TrailerTestRig.place(body, BlockPos.ZERO, (bike ? MotorcycleModule.GEARBOX.get() : Blocks.IRON_BLOCK).defaultBlockState());
            TrailerTestRig.place(body, new BlockPos(0,0,-1), CreateMotorsport.ENGINE_BLOCK.get().defaultBlockState());
            var axles = new ArrayList<SuspensionBlockEntity>();
            for (int z : new int[]{-2, 1}) {
                var block = bike ? (z == -2 ? MotorcycleModule.FORKS.get() : MotorcycleModule.REAR_ARM.get()) : CreateMotorsport.SUSPENSION.get();
                TrailerTestRig.place(body, new BlockPos(0,0,z), block.defaultBlockState());
                var axle = (SuspensionBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock().offset(0,0,z));
                if (!bike && z == -2) axle.toggleAxleEnd();
                axle.installTire(WheelSide.LEFT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
                if (!bike) axle.installTire(WheelSide.RIGHT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
                axles.add(axle);
            }
            TrailerTestRig.finishPlacement(body, position);
            rigs.add(new Rig(body, axles.get(0), axles.get(1), scales[i], bike));
        }
        double[] peakUp = new double[rigs.size()];
        boolean[] resizing = {false};
        helper.onEachTick(() -> {
            if (!resizing[0]) return;
            for (int i = 0; i < rigs.size(); i++) {
                var body = rigs.get(i).body();
                if (!body.isRemoved()) peakUp[i] = Math.max(peakUp[i], RigidBodyHandle.of(body).getLinearVelocity(new Vector3d()).y);
            }
        });
        helper.startSequence().thenExecuteAfter(30, () -> {
            resizing[0] = true;
            for (var rig : rigs) resize(rig.body(), rig.scale());
        }).thenExecuteAfter(160, () -> {
            try {
                for (int i = 0; i < rigs.size(); i++) {
                    var rig = rigs.get(i);
                    CreateMotorsport.LOGGER.info("Pocket result bike={} scale={} velocity={} peakUp={} up={} front={} rear={}",
                            rig.bike(), rig.scale(), RigidBodyHandle.of(rig.body()).getLinearVelocity(new Vector3d()), peakUp[i],
                            rig.body().logicalPose().transformNormal(new Vector3d(0,1,0)).normalize(),
                            rig.front().getTelemetry(WheelSide.LEFT).grounded(), rig.rear().getTelemetry(WheelSide.LEFT).grounded());
                }
                for (int i = 0; i < rigs.size(); i++) {
                    var rig = rigs.get(i);
                    var velocity = RigidBodyHandle.of(rig.body()).getLinearVelocity(new Vector3d());
                    var front = rig.front().getTelemetry(WheelSide.LEFT);
                    var rear = rig.rear().getTelemetry(WheelSide.LEFT);
                    String label = (rig.bike() ? "bike" : "car") + " scale=" + rig.scale();
                    CreateMotorsport.LOGGER.info("Pocket test {} velocity={} peakUp={} front={} rear={}", label, velocity, peakUp[i], front.grounded(), rear.grounded());
                    helper.assertTrue(Math.abs(rig.body().logicalPose().scale().y - rig.scale()) < 1e-6, "Resize must complete: " + label);
                    helper.assertTrue(velocity.isFinite() && Math.abs(velocity.y) < .5 && peakUp[i] < 3, "Suspension must settle without launch: " + label + " velocity=" + velocity + " peakUp=" + peakUp[i]);
                    helper.assertTrue(front.grounded() && rear.grounded(), "Both tyres must contact terrain: " + label);
                }
            } finally {
                resizing[0] = false;
                for (var rig : rigs) SubLevelContainer.getContainer(level).removeSubLevel(rig.body(), SubLevelRemovalReason.REMOVED);
                for (var chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
            }
        }).thenSucceed();
    }

    private static void resize(ServerSubLevel body, double scale) {
        try {
            var api = Class.forName("com.misterblusky9.pym.api.Pym");
            Object resizing = api.getMethod("resize").invoke(null);
            Object request = resizing.getClass().getMethod("request", ServerSubLevel.class).invoke(resizing, body);
            request.getClass().getMethod("scaleTo", double.class).invoke(request, scale);
            request.getClass().getMethod("ticks", double.class).invoke(request, 15.0);
            Object result = request.getClass().getMethod("submit").invoke(request);
            if (!(boolean) result.getClass().getMethod("accepted").invoke(result))
                throw new IllegalStateException("Pocket Sized refused test resize: " + result);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot exercise Pym resize API", failure);
        }
    }
}
