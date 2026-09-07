package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; override the design vehicle mass for engine at (pos)
// Creative or op only for now
public record SetEngineDesignMassPacket(BlockPos pos, double designMass) implements CustomPacketPayload {
    public static final Type<SetEngineDesignMassPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "set_engine_design_mass"));

    public static final StreamCodec<FriendlyByteBuf, SetEngineDesignMassPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetEngineDesignMassPacket::pos,
            ByteBufCodecs.DOUBLE, SetEngineDesignMassPacket::designMass,
            SetEngineDesignMassPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static boolean mayEdit(Player player) {
        return player != null && (player.isCreative() || player.hasPermissions(2));
    }

    public static void handle(SetEngineDesignMassPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!mayEdit(player)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos()) instanceof EngineBlockEntity engine) {
                engine.setDesignMassOverride(packet.designMass());
            }
        });
    }
}
