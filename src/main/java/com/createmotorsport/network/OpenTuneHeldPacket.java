package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.client.screen.TireScreenOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Server -> client
public record OpenTuneHeldPacket(boolean mainHand, boolean canTune) implements CustomPacketPayload {

    public static final Type<OpenTuneHeldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "open_tune_held"));

    public static final StreamCodec<FriendlyByteBuf, OpenTuneHeldPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenTuneHeldPacket::mainHand,
            ByteBufCodecs.BOOL, OpenTuneHeldPacket::canTune,
            OpenTuneHeldPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTuneHeldPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TireScreenOpener.open(
                packet.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, packet.canTune()));
    }
}
