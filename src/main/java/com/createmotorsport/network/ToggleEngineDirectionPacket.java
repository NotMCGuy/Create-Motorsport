package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; flip the engine's crank rotation direction (+1 / -1) at (pos)
public record ToggleEngineDirectionPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ToggleEngineDirectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "toggle_engine_direction"));

    public static final StreamCodec<FriendlyByteBuf, ToggleEngineDirectionPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ToggleEngineDirectionPacket::pos,
            ToggleEngineDirectionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleEngineDirectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(packet.pos()) instanceof EngineBlockEntity engine) {
                engine.toggleRotationDirection();
            }
        });
    }
}
