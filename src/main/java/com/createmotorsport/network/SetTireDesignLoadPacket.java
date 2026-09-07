package com.createmotorsport.network;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client -> server; set the design load on the tire the player is holding
// midpointKg is the whole car mass, registerTire takes that, the component stores it split 4 ways and in Newtons
public record SetTireDesignLoadPacket(boolean mainHand, double midpointKg) implements CustomPacketPayload {
    public static final Type<SetTireDesignLoadPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "set_tire_design_load"));

    public static final StreamCodec<FriendlyByteBuf, SetTireDesignLoadPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetTireDesignLoadPacket::mainHand,
            ByteBufCodecs.DOUBLE, SetTireDesignLoadPacket::midpointKg,
            SetTireDesignLoadPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTireDesignLoadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!SetEngineDesignMassPacket.mayEdit(player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(packet.mainHand()
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (stack.isEmpty() || stack.get(CreateMotorsport.TIRE_DESIGN_LOAD) == null) {
                return;
            }
            double kg = Math.max(1.0, Math.min(100000.0, packet.midpointKg()));
            stack.set(CreateMotorsport.TIRE_DESIGN_LOAD, (float) (kg * 9.81 / 4.0));
        });
    }
}
