package com.createmotorsport.motorcycle;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import com.createmotorsport.trailer.TrailerTestRig;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder("createmotorsport")
@PrefixGameTestTemplate(false)
public final class MotorcycleBalanceGameTests {
    @GameTest(template = "trailer_empty", timeoutTicks = 180)
    public static void motorcycleRecoversFromRollDisturbance(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean forced = level.getForcedChunks().contains(ChunkPos.asLong(25, 25));
        level.setChunkForced(25, 25, true);
        for (int x = 400; x < 416; x++) for (int z = 400; z < 416; z++) level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
        var position = new Vector3d(408, 81, 408);
        var body = TrailerTestRig.body(level, position);
        TrailerTestRig.place(body, new BlockPos(0, 0, -2), MotorcycleModule.FORKS.get().defaultBlockState());
        TrailerTestRig.place(body, new BlockPos(0, 0, 1), MotorcycleModule.REAR_ARM.get().defaultBlockState());
        TrailerTestRig.place(body, BlockPos.ZERO, MotorcycleModule.GEARBOX.get().defaultBlockState());
        TrailerTestRig.place(body, new BlockPos(0, 0, -1), CreateMotorsport.ENGINE_BLOCK.get().defaultBlockState());
        var fork = (MotorcycleSuspensionBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock().offset(0, 0, -2));
        var rear = (MotorcycleSuspensionBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock().offset(0, 0, 1));
        fork.installTire(WheelSide.LEFT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        rear.installTire(WheelSide.LEFT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        TrailerTestRig.finishPlacement(body, position);
        boolean[] turning = {false};
        helper.onEachTick(() -> {
            if (!turning[0] || body.isRemoved()) return;
            fork.setDriverSteering(5, 0);
            var handle = RigidBodyHandle.of(body);
            var velocity = handle.getLinearVelocity(new Vector3d());
            var forward = body.logicalPose().transformNormal(new Vector3d(0, 0, -1));
            handle.addLinearAndAngularVelocity(new Vector3d(forward).mul(5).sub(velocity.x, 0, velocity.z).setComponent(1, 0), new Vector3d());
        });
        helper.startSequence().thenExecuteAfter(40, () -> {
            var impulse = body.getMassTracker().getInertiaTensor().transform(new Vector3d(0, 0, -.9));
            RigidBodyHandle.of(body).applyAngularImpulse(impulse);
        }).thenExecuteAfter(65, () -> {
            try {
                var up = body.logicalPose().transformNormal(new Vector3d(0, 1, 0));
                helper.assertTrue(up.y > Math.cos(Math.toRadians(10)), "Stopped bike must recover upright after a roll disturbance; up=" + up);
                helper.assertTrue(fork.getTelemetry(WheelSide.LEFT).grounded() || rear.getTelemetry(WheelSide.LEFT).grounded(), "Recovery must keep real tyre contact");
                var localRate = body.logicalPose().transformNormalInverse(RigidBodyHandle.of(body).getAngularVelocity(new Vector3d()));
                helper.assertTrue(Math.abs(localRate.z) < .3, "Balance must settle rather than oscillate; roll rate=" + localRate.z);
                turning[0] = true;
            } catch (Throwable failure) {
                SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                if (!forced) level.setChunkForced(25, 25, false);
                throw failure;
            }
        }).thenExecuteAfter(20, () -> {
            try {
                var forward = body.logicalPose().transformNormal(new Vector3d(0, 0, -1));
                var right = new Vector3d(forward).cross(0, 1, 0).normalize();
                var up = body.logicalPose().transformNormal(new Vector3d(0, 1, 0));
                helper.assertTrue(up.dot(right) < -.02, "Moving bike must lean into left steering; lean=" + up.dot(right));
                helper.assertTrue(up.y > .7, "Cornering assistance must keep lean bounded");
            } finally {
                turning[0] = false;
                SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                if (!forced) level.setChunkForced(25, 25, false);
            }
        }).thenSucceed();
    }
}
