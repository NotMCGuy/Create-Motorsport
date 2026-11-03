package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.client.screen.part.PartScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

// Server -> client: a part's status lines for its open part screen
public record PartStatusPacket(BlockPos pos, List<Component> lines) implements CustomPacketPayload {

    public static final Type<PartStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "part_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartStatusPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PartStatusPacket::pos,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(16)), PartStatusPacket::lines,
            PartStatusPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> PartScreens.status(packet.pos(), packet.lines()));
    }
}
