package com.createmotorsport.client;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.item.LapGateBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;


// Trying to do a particle effect when placing the lap gates,
// based on how Create does an animation when you place the belts between two shafts

public final class LapGateLinkPreview {
    private static final Vector3f VALID = new Vector3f(0.3F, 0.9F, 0.5F);
    private static final Vector3f INVALID = new Vector3f(0.9F, 0.3F, 0.5F);
    private static final float STEP = 0.0625F; // 1/16 block, same as Create
    private static final double SPAWN_CHANCE = 0.1; // sparse so the line shimmers instead of solid

    private LapGateLinkPreview() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ItemStack stack = heldGate(player);
        if (stack == null) {
            return;
        }
        BlockPos first = LapGateBlockItem.getArmed(stack);
        if (first == null) {
            return;
        }

        Vec3 start = Vec3.atCenterOf(first);
        Vec3 end;
        boolean valid;
        if (mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hovered = hit.getBlockPos();
            end = Vec3.atCenterOf(hovered);
            valid = !hovered.equals(first) && first.closerThan(hovered, LapGateBlockItem.MAX_GATE_LENGTH);
        } else {
            end = player.getEyePosition().add(player.getLookAngle().scale(6.0));
            valid = false;
        }

        DustParticleOptions dust = new DustParticleOptions(valid ? VALID : INVALID, 1.0F);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0e-3) {
            return;
        }
        Vec3 step = delta.scale(1.0 / length);
        for (float travelled = 0.0F; travelled < length; travelled += STEP) {
            if (player.getRandom().nextDouble() > SPAWN_CHANCE) {
                continue;
            }
            Vec3 at = start.add(step.scale(travelled));
            mc.level.addParticle(dust, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
    }

    private static ItemStack heldGate(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(CreateMotorsport.LAP_GATE_ITEM.get())) {
                return stack;
            }
        }
        return null;
    }
}
