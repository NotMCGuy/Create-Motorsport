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


// Client -> server; bind (keyCode), which is a GLFW code, or -1 to clear
// Binds (control) on the steering wheel at (pos), from SteeringKeysScreen's key capture
public record SetSteeringKeyPacket(BlockPos pos, int control, int keyCode) implements CustomPacketPayload {
    public static final Type<SetSteeringKeyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "set_steering_key"));

    public static final StreamCodec<FriendlyByteBuf, SetSteeringKeyPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetSteeringKeyPacket::pos,
            ByteBufCodecs.VAR_INT, SetSteeringKeyPacket::control,
            ByteBufCodecs.INT, SetSteeringKeyPacket::keyCode,
            SetSteeringKeyPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetSteeringKeyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(packet.pos()) instanceof SteeringWheelBlockEntity wheel) {
                wheel.setKeyCode(packet.control(), packet.keyCode());
            }
        });
    }
}
