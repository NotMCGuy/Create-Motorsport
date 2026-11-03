package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; flip one of the suspension's part screen toggles at (pos)
public record ToggleAxlePacket(BlockPos pos, int setting) implements CustomPacketPayload {

    public static final int END = 0;
    public static final int STEERING = 1;
    public static final int DRIVEN = 2;

    public static final Type<ToggleAxlePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "toggle_axle"));

    public static final StreamCodec<FriendlyByteBuf, ToggleAxlePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ToggleAxlePacket::pos,
            ByteBufCodecs.VAR_INT, ToggleAxlePacket::setting,
            ToggleAxlePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleAxlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level().getBlockEntity(packet.pos()) instanceof SuspensionBlockEntity suspension)) {
                return;
            }
            switch (packet.setting()) {
                case END -> suspension.toggleAxleEnd();
                case STEERING -> suspension.toggleSteering();
                case DRIVEN -> {
                    if (TunePacket.mayTune(context.player())) {
                        suspension.toggleDriven();
                    }
                }
                default -> { }
            }
        });
    }
}
