package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.telemetry.TelemetryCsvWriter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client; one line of a telemetry log. 'Kind' can be
 * Header (open new CSV and write the headers),
 * Row (make a sample), or
 * End (close file and report save location to client)
*/
public record TelemetryLinePacket(int kind, String line) implements CustomPacketPayload {
    public static final int KIND_HEADER = 0;
    public static final int KIND_ROW = 1;
    public static final int KIND_END = 2;

    public static final Type<TelemetryLinePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "telemetry_line"));

    public static final StreamCodec<FriendlyByteBuf, TelemetryLinePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelemetryLinePacket::kind,
            ByteBufCodecs.stringUtf8(64 * 1024), TelemetryLinePacket::line,
            TelemetryLinePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelemetryLinePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TelemetryCsvWriter.accept(packet.kind(), packet.line()));
    }
}
