package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; the driver's controls at (pos)
//  mask -> one bit per SteeringWheelBlockEntity.SteeringControl, for the on/off controls
//             (clutch, shifts, engine aids) and for the action-bar readout
//  throttle -> analog 0 to 100 (keypress = 100 when held, analog controller)
//  brake    -> analog 0 to 100
//  steer  ->  analog -100 to 100, positive = left (keyboard left/right = +/-100; stick is analog)
public record SteeringInputPacket(BlockPos pos, int mask, int throttle, int brake, int steer)
        implements CustomPacketPayload {
    public static final Type<SteeringInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "steering_input"));

    public static final StreamCodec<FriendlyByteBuf, SteeringInputPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SteeringInputPacket::pos,
            ByteBufCodecs.VAR_INT, SteeringInputPacket::mask,
            ByteBufCodecs.VAR_INT, SteeringInputPacket::throttle,
            ByteBufCodecs.VAR_INT, SteeringInputPacket::brake,
            ByteBufCodecs.VAR_INT, SteeringInputPacket::steer,
            SteeringInputPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SteeringInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level().getBlockEntity(packet.pos()) instanceof SteeringWheelBlockEntity wheel)) {
                return;
            }
            wheel.setInput(context.player(), packet.mask(), packet.throttle(), packet.brake(), packet.steer());
        });
    }
}
