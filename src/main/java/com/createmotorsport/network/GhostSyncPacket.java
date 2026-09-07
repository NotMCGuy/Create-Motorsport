package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import com.createmotorsport.client.GhostManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Server -> client
public record GhostSyncPacket(float[] samples, long lapTicks, int sampleTicks, long startGameTime,
                              String name, boolean playing)
        implements CustomPacketPayload {

    public static final Type<GhostSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "ghost_sync"));

    private static final int MAX_SAMPLE_FLOATS = 3 * LapGateBlockEntity.GHOST_MAX_SAMPLES;

    public static final StreamCodec<FriendlyByteBuf, GhostSyncPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.samples().length);
                for (float f : packet.samples()) {
                    buf.writeFloat(f);
                }
                buf.writeVarLong(packet.lapTicks());
                buf.writeVarInt(packet.sampleTicks());
                buf.writeLong(packet.startGameTime());
                buf.writeUtf(packet.name(), 32);
                buf.writeBoolean(packet.playing());
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_SAMPLE_FLOATS || n % 3 != 0) {
                    throw new io.netty.handler.codec.DecoderException(
                            "GhostSyncPacket sample count out of range: " + n);
                }
                float[] samples = new float[n];
                for (int i = 0; i < n; i++) {
                    samples[i] = buf.readFloat();
                }
                return new GhostSyncPacket(samples, buf.readVarLong(), buf.readVarInt(),
                        buf.readLong(), buf.readUtf(32), buf.readBoolean());
            });


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GhostSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> GhostManager.accept(packet));
    }
}
