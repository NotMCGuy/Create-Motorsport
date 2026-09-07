package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; one packet for every button on the lap gate screen
// value carries the argument for the action
public record RaceControlPacket(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int ACTION_START = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_SET_LAPS = 2;
    public static final int ACTION_SET_MARKER = 3;
    public static final int ACTION_TOGGLE_TELEMETRY = 4;
    public static final int ACTION_RESCAN = 5;
    public static final int ACTION_TOGGLE_GHOST = 6;
    public static final int ACTION_CLEAR_GHOST = 7;

    public static final Type<RaceControlPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "race_control"));

    public static final StreamCodec<FriendlyByteBuf, RaceControlPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaceControlPacket::pos,
            ByteBufCodecs.VAR_INT, RaceControlPacket::action,
            ByteBufCodecs.VAR_INT, RaceControlPacket::value,
            RaceControlPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RaceControlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Need to gate reach for chunk loaded blocks
            LapGateBlockEntity gate = LapGateBlockEntity.controllableBy(player, packet.pos());
            if (gate == null) {
                return;
            }
            switch (packet.action()) {
                case ACTION_START -> gate.startRace(player, packet.value());
                case ACTION_STOP -> gate.stopRace(true, player);
                case ACTION_SET_LAPS -> gate.setTotalLaps(packet.value());
                case ACTION_SET_MARKER -> gate.setMarker(packet.value());
                case ACTION_TOGGLE_TELEMETRY -> gate.setLogFullTelemetry(!gate.isLogFullTelemetry());
                case ACTION_RESCAN -> gate.scanForCars();
                case ACTION_TOGGLE_GHOST -> gate.setGhostEnabled(!gate.isGhostEnabled());
                case ACTION_CLEAR_GHOST -> gate.clearGhost();
                default -> {
                }
            }
        });
    }
}
