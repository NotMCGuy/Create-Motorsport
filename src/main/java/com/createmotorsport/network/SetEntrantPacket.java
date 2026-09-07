package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; rename entrant and set its starting grid position on the lap gate at (pos)
public record SetEntrantPacket(BlockPos pos, int index, String name, int grid) implements CustomPacketPayload {
    public static final Type<SetEntrantPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "set_entrant"));

    public static final StreamCodec<FriendlyByteBuf, SetEntrantPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetEntrantPacket::pos,
            ByteBufCodecs.VAR_INT, SetEntrantPacket::index,
            ByteBufCodecs.STRING_UTF8, SetEntrantPacket::name,
            ByteBufCodecs.VAR_INT, SetEntrantPacket::grid,
            SetEntrantPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetEntrantPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Same reach guarding RaceControlPacket
            LapGateBlockEntity gate =
                    LapGateBlockEntity.controllableBy(context.player(), packet.pos());
            if (gate == null) {
                return;
            }
            String name = packet.name();
            if (name.length() > 24) {
                name = name.substring(0, 24);
            }
            gate.setEntrantName(packet.index(), name);
            gate.setEntrantGrid(packet.index(), packet.grid());
        });
    }
}
