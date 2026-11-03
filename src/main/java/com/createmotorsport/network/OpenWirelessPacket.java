package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.menu.PartMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server
public record OpenWirelessPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<OpenWirelessPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "open_wireless"));

    public static final StreamCodec<FriendlyByteBuf, OpenWirelessPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenWirelessPacket::pos, OpenWirelessPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWirelessPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PartMenus.openWireless(player, packet.pos());
            }
        });
    }
}
