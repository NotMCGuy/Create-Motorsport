package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server: one of the steering wheel screen's aid buttons
public record SetCarAidPacket(BlockPos pos, SteeringWheelBlockEntity.Aid aid) implements CustomPacketPayload {
    public static final Type<SetCarAidPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "set_car_aid"));

    private static final SteeringWheelBlockEntity.Aid[] AIDS = SteeringWheelBlockEntity.Aid.values();

    public static final StreamCodec<FriendlyByteBuf, SetCarAidPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetCarAidPacket::pos,
            ByteBufCodecs.VAR_INT.map(i -> AIDS[Math.floorMod(i, AIDS.length)], Enum::ordinal), SetCarAidPacket::aid,
            SetCarAidPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetCarAidPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(packet.pos()) instanceof SteeringWheelBlockEntity wheel) {
                wheel.toggle(packet.aid());
            }
        });
    }
}
