package com.createmotorsport.client;

import com.createmotorsport.client.input.SteeringInputHandler;
import com.createmotorsport.network.StartTelemetryLogPacket;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;


// Client commands: /motorsports log [seconds] [samples per second] [extra], and /motorsports log stop
// extra is for every column
public final class MotorsportCommands {
    private MotorsportCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("motorsports")
                        .then(Commands.literal("log")
                                .then(Commands.literal("stop").executes(ctx -> stopLog(ctx.getSource())))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> startLog(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds"), 2, false))
                                        .then(Commands.literal("extra").executes(ctx -> startLog(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds"), 2, true)))
                                        .then(Commands.argument("samplesPerSec", IntegerArgumentType.integer(1, 20))
                                                .executes(ctx -> startLog(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"),
                                                        IntegerArgumentType.getInteger(ctx, "samplesPerSec"), false))
                                                .then(Commands.literal("extra").executes(ctx -> startLog(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"),
                                                        IntegerArgumentType.getInteger(ctx, "samplesPerSec"), true)))))));
    }

    private static int stopLog(net.minecraft.commands.CommandSourceStack source) {
        if (!SteeringInputHandler.isDriving()) {
            source.sendFailure(Component.literal("You must be driving a car to stop a log"));
            return 0;
        }
        PacketDistributor.sendToServer(new StartTelemetryLogPacket(0, 1, false));
        source.sendSystemMessage(Component.literal(
                "§e[Motorsports] Stopped and saving log"));
        return 1;
    }

    private static int startLog(net.minecraft.commands.CommandSourceStack source, int seconds,
                                int samplesPerSec, boolean extra) {
        if (!SteeringInputHandler.isDriving()) {
            source.sendFailure(Component.literal("You must be driving a car to log data"));
            return 0;
        }
        PacketDistributor.sendToServer(new StartTelemetryLogPacket(seconds, samplesPerSec, extra));
        source.sendSystemMessage(Component.literal(
                "§e[Motorsports] Requested a " + seconds + "s telemetry log ("
                        + samplesPerSec + " samples/s, " + (extra ? "all columns" : "core columns") + ")"));
        return 1;
    }
}
