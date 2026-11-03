package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; starts telemetry csv log for (seconds) at (samplesPerSec) rows per second,
// Its starting to get way too long so, I added 'extra' flag. Will rearrange it all more later
public record StartTelemetryLogPacket(int seconds, int samplesPerSec, boolean extra) implements CustomPacketPayload {
    public static final Type<StartTelemetryLogPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "start_telemetry_log"));

    public static final StreamCodec<FriendlyByteBuf, StartTelemetryLogPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StartTelemetryLogPacket::seconds,
            ByteBufCodecs.VAR_INT, StartTelemetryLogPacket::samplesPerSec,
            ByteBufCodecs.BOOL, StartTelemetryLogPacket::extra,
            StartTelemetryLogPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartTelemetryLogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            SteeringWheelBlockEntity wheel = SteeringWheelBlockEntity.findDrivenBy(player);
            if (wheel == null) {
                player.displayClientMessage(Component.literal(
                        "§c[Motorsports] You have to be driving to start a log"), false);
                return;
            }
            if (packet.seconds() <= 0) {
                wheel.stopTelemetryLog(player);
                return;
            }
            wheel.startTelemetryLog(player, Math.max(1, Math.min(600, packet.seconds())),
                    Math.max(1, Math.min(20, packet.samplesPerSec())), packet.extra());
        });
    }
}
