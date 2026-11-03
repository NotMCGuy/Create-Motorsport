package com.createmotorsport.network;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.tune.Tunable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

// Client -> server
public record TunePacket(BlockPos pos, String section, int action, String path, String text)
        implements CustomPacketPayload {

    public static final int SET = 0;
    public static final int CLEAR = 1;
    public static final int CYCLE = 2;

    public static final Type<TunePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateMotorsport.MODID, "tune"));

    public static final StreamCodec<FriendlyByteBuf, TunePacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TunePacket::pos,
            ByteBufCodecs.stringUtf8(64), TunePacket::section,
            ByteBufCodecs.VAR_INT, TunePacket::action,
            ByteBufCodecs.stringUtf8(128), TunePacket::path,
            ByteBufCodecs.stringUtf8(256), TunePacket::text,
            TunePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static boolean mayTune(Player player) {
        return Config.ALLOW_PLAYER_TUNING.getAsBoolean() || player.hasPermissions(2);
    }

    public static void handle(TunePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (mayTune(player) && player.level().getBlockEntity(packet.pos()) instanceof Tunable part) {
                apply(part, packet);
            }
        });
    }

    @Nullable
    public static String apply(Tunable part, TunePacket packet) {
        return apply(part, packet.section(), packet.action(), packet.path(), packet.text());
    }

    @Nullable
    public static String apply(Tunable part, String sectionKey, int action, String path, String text) {
        Tunable.Section<?> section = part.tuneSection(sectionKey);
        if (section == null) {
            return "no section " + sectionKey;
        }
        switch (action) {
            case SET -> {
                return section.set(path, text);
            }
            case CLEAR -> section.clear(path);
            case CYCLE -> {
                if (section.cycle() == null) {
                    return sectionKey + " has nothing to cycle";
                }
                section.cycle().accept(text.startsWith("-") ? -1 : 1);
            }
            default -> {
                return "no action " + action;
            }
        }
        return null;
    }
}
