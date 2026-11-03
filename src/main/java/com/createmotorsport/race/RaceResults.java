package com.createmotorsport.race;

import com.createmotorsport.network.TelemetryLinePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RaceResults {

    private RaceResults() {
    }

    // m:ss.sss, or s.sss s under a minute
    public static String formatTime(long ticks) {
        double seconds = ticks / 20.0;
        int minutes = (int) (seconds / 60.0);
        double rest = seconds - minutes * 60.0;
        return minutes > 0
                ? String.format(Locale.ROOT, "%d:%06.3f", minutes, rest)
                : String.format(Locale.ROOT, "%.3fs", rest);
    }

    public static void send(ServerPlayer player, List<Entrant> entrants, String telemetryHeader,
                            List<String> telemetryRows) {
        if (entrants.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[Race] No entrants to log"));
            return;
        }
        int maxSplits = 0;
        for (Entrant e : entrants) {
            maxSplits = Math.max(maxSplits, e.splits.size());
        }
        StringBuilder header = new StringBuilder("grid,name,laps_completed,total_time_s");
        for (int i = 0; i < maxSplits; i++) {
            header.append(",split_").append(i + 1).append("_s");
        }
        PacketDistributor.sendToPlayer(player, new TelemetryLinePacket(TelemetryLinePacket.KIND_HEADER,
                header.toString()));

        List<Entrant> sorted = new ArrayList<>(entrants);
        sorted.sort(java.util.Comparator.comparingInt((Entrant e) -> e.grid));
        for (Entrant e : sorted) {
            StringBuilder row = new StringBuilder();
            row.append(e.grid).append(',').append(e.name.replace(',', ' ')).append(',')
                    .append(e.lap).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", e.lastCrossTick / 20.0));
            for (int i = 0; i < maxSplits; i++) {
                row.append(',');
                if (i < e.splits.size()) {
                    row.append(String.format(Locale.ROOT, "%.3f", e.splits.get(i) / 20.0));
                }
            }
            PacketDistributor.sendToPlayer(player, new TelemetryLinePacket(TelemetryLinePacket.KIND_ROW,
                    row.toString()));
        }

        // Full car telemetry underneath race results, if user chooses
        if (telemetryHeader != null && !telemetryRows.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new TelemetryLinePacket(TelemetryLinePacket.KIND_ROW, ""));
            PacketDistributor.sendToPlayer(player,
                    new TelemetryLinePacket(TelemetryLinePacket.KIND_ROW, telemetryHeader));
            sendBatched(player, telemetryRows);
        }
        PacketDistributor.sendToPlayer(player, new TelemetryLinePacket(TelemetryLinePacket.KIND_END, ""));
    }

    // Sends results in a few packets instead of one huge one sent over a single tick
    private static void sendBatched(ServerPlayer player, List<String> lines) {
        final int maxChars = 24_000;
        StringBuilder batch = new StringBuilder(maxChars + 256);
        for (String line : lines) {
            if (batch.length() > 0 && batch.length() + line.length() + 1 > maxChars) {
                PacketDistributor.sendToPlayer(player,
                        new TelemetryLinePacket(TelemetryLinePacket.KIND_ROW, batch.toString()));
                batch.setLength(0);
            }
            if (batch.length() > 0) {
                batch.append('\n');
            }
            batch.append(line);
        }
        if (batch.length() > 0) {
            PacketDistributor.sendToPlayer(player,
                    new TelemetryLinePacket(TelemetryLinePacket.KIND_ROW, batch.toString()));
        }
    }
}
