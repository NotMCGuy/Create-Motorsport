package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.menu.PartPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: an open part screen asking for its status lines
public record PartStatusRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<PartStatusRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "part_status_request"));

    public static final StreamCodec<FriendlyByteBuf, PartStatusRequestPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PartStatusRequestPacket::pos, PartStatusRequestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PartStatusRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(packet.pos()) instanceof PartPanel panel) {
                PacketDistributor.sendToPlayer(player, new PartStatusPacket(packet.pos(), panel.statusLines()));
            }
        });
    }
}
