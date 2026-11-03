package com.createmotorsport.telemetry;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.network.TelemetryLinePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


// Writes CSV lines streamed from the server into game directory (/motorsports folder)
// HEADER opens file, ROW appends, END closes and chats the path to the client
public final class TelemetryCsvWriter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static BufferedWriter writer;
    private static Path path;
    private static int rows;

    private TelemetryCsvWriter() {
    }

    public static void accept(int kind, String line) {
        switch (kind) {
            case TelemetryLinePacket.KIND_HEADER -> open(line);
            case TelemetryLinePacket.KIND_ROW -> append(line);
            case TelemetryLinePacket.KIND_END -> close();
            default -> {
            }
        }
    }

    private static void open(String header) {
        close(); // finish any previous log
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("motorsports");
            Files.createDirectories(dir);
            path = dir.resolve("drive-" + LocalDateTime.now().format(STAMP) + ".csv");
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            writer.write(header);
            writer.newLine();
            rows = 0;
            message("§a[Motorsports] Logging to " + path.getFileName());
        } catch (IOException e) {
            CreateMotorsport.LOGGER.error("Failed to open telemetry CSV", e);
            writer = null;
            path = null;
        }
    }

    private static void append(String row) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(row);
            writer.newLine();
            rows++;
        } catch (IOException e) {
            CreateMotorsport.LOGGER.error("Failed to write telemetry row", e);
        }
    }

    private static void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
            message("§a[Motorsports] Saved " + rows + " samples to motorsports/" + path.getFileName());
        } catch (IOException e) {
            CreateMotorsport.LOGGER.error("Failed to close telemetry CSV", e);
        } finally {
            writer = null;
            path = null;
            rows = 0;
        }
    }

    private static void message(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text), false);
        }
    }
}
