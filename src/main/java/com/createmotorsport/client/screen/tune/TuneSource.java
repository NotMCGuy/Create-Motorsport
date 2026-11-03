package com.createmotorsport.client.screen.tune;

import com.createmotorsport.item.HeldTire;
import com.createmotorsport.network.TuneHeldPacket;
import com.createmotorsport.network.TunePacket;
import com.createmotorsport.tune.Tunable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// either a tire in hand or a placed car part is a TuneSource
public interface TuneSource {

    @Nullable
    List<Tunable.Section<?>> sections();

    void send(String section, int action, String path, String text);

    static TuneSource block(BlockPos pos) {
        return new TuneSource() {
            @Override
            @Nullable
            public List<Tunable.Section<?>> sections() {
                var level = Minecraft.getInstance().level;
                return level != null && level.getBlockEntity(pos) instanceof Tunable part ? part.tuneSections()
                        : null;
            }

            @Override
            public void send(String section, int action, String path, String text) {
                PacketDistributor.sendToServer(new TunePacket(pos, section, action, path, text));
            }
        };
    }

    static TuneSource held(InteractionHand hand) {
        return new TuneSource() {
            @Override
            @Nullable
            public List<Tunable.Section<?>> sections() {
                var player = Minecraft.getInstance().player;
                ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
                return HeldTire.tunable(stack) ? new HeldTire(stack, player.level().registryAccess()).tuneSections()
                        : null;
            }

            @Override
            public void send(String section, int action, String path, String text) {
                PacketDistributor.sendToServer(
                        new TuneHeldPacket(hand == InteractionHand.MAIN_HAND, section, action, path, text));
            }
        };
    }
}
