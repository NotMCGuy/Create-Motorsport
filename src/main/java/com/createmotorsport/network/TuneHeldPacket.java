package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.item.HeldTire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// TunePacket for the tire in hand
public record TuneHeldPacket(boolean mainHand, String section, int action, String path, String text)
        implements CustomPacketPayload {

    public static final Type<TuneHeldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "tune_held"));

    public static final StreamCodec<FriendlyByteBuf, TuneHeldPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TuneHeldPacket::mainHand,
            ByteBufCodecs.stringUtf8(64), TuneHeldPacket::section,
            ByteBufCodecs.VAR_INT, TuneHeldPacket::action,
            ByteBufCodecs.stringUtf8(128), TuneHeldPacket::path,
            ByteBufCodecs.stringUtf8(256), TuneHeldPacket::text,
            TuneHeldPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TuneHeldPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack stack = player.getItemInHand(packet.mainHand() ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND);
            if (TunePacket.mayTune(player) && HeldTire.tunable(stack)) {
                TunePacket.apply(new HeldTire(stack, player.level().registryAccess()), packet.section(),
                        packet.action(), packet.path(), packet.text());
            }
        });
    }
}
