package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.client.SkidmarkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// Server -> client
public record SkidmarkPacket(List<float[]> quads) implements CustomPacketPayload {
    public static final int FLOATS_PER_QUAD = 13;

    public static final Type<SkidmarkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "skidmark"));

    public static final StreamCodec<FriendlyByteBuf, SkidmarkPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.quads().size());
                for (float[] q : packet.quads()) {
                    for (int i = 0; i < FLOATS_PER_QUAD; i++) {
                        buf.writeFloat(q[i]);
                    }
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<float[]> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    float[] q = new float[FLOATS_PER_QUAD];
                    for (int j = 0; j < FLOATS_PER_QUAD; j++) {
                        q[j] = buf.readFloat();
                    }
                    list.add(q);
                }
                return new SkidmarkPacket(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkidmarkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            for (float[] q : packet.quads()) {
                SkidmarkManager.add(q);
            }
        });
    }
}
