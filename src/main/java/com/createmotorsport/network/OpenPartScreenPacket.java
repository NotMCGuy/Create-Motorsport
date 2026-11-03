package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.client.screen.part.PartScreens;
import com.createmotorsport.menu.PartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Server -> client
public record OpenPartScreenPacket(BlockPos pos, PartKind kind, boolean canTune) implements CustomPacketPayload {

    public static final Type<OpenPartScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "open_part_screen"));

    private static final PartKind[] KINDS = PartKind.values();

    public static final StreamCodec<FriendlyByteBuf, OpenPartScreenPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenPartScreenPacket::pos,
            ByteBufCodecs.VAR_INT.map(i -> KINDS[Math.floorMod(i, KINDS.length)], Enum::ordinal),
            OpenPartScreenPacket::kind,
            ByteBufCodecs.BOOL, OpenPartScreenPacket::canTune,
            OpenPartScreenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPartScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> PartScreens.open(packet.pos(), packet.kind(), packet.canTune()));
    }
}
