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


// Client -> server; set whether the sending player is driving the steering wheel at (pos)
// The client starts input capture immediately but this packet just tells the server to
// register/clear the driver so it accepts the input steam and broadcasts the control links
public record SetDrivingPacket(BlockPos pos, boolean driving) implements CustomPacketPayload {
    public static final Type<SetDrivingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "set_driving"));

    public static final StreamCodec<FriendlyByteBuf, SetDrivingPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetDrivingPacket::pos,
            ByteBufCodecs.BOOL, SetDrivingPacket::driving,
            SetDrivingPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetDrivingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(packet.pos()) instanceof SteeringWheelBlockEntity wheel) {
                wheel.setDriving(context.player(), packet.driving());
            }
        });
    }
}
