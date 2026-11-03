package com.createmotorsport.telemetry;

import com.createmotorsport.Config;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.network.TelemetryLinePacket;
import com.createmotorsport.physics.world.Gravity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class TelemetryLog {

    private final UUID recipient;
    private final boolean extra;
    private final int sampleEveryTicks;
    private final long startGameTime;
    private int ticksRemaining;

    private TelemetryLog(UUID recipient, boolean extra, int seconds, int samplesPerSec, long startGameTime) {
        this.recipient = recipient;
        this.extra = extra;
        this.ticksRemaining = seconds * 20;
        this.sampleEveryTicks = Math.max(1, 20 / Math.max(1, Math.min(20, samplesPerSec)));
        this.startGameTime = startGameTime;
    }

    // samplesPerSec 1-20, 20 is what debugging needs
    public static TelemetryLog start(Level level, ServerPlayer player, CarActors car, int seconds, int samplesPerSec,
                                     boolean extra) {
        TelemetryLog log = new TelemetryLog(player.getUUID(), extra, seconds, samplesPerSec, level.getGameTime());
        log.send(level, TelemetryLinePacket.KIND_HEADER, TelemetryFormat.header(car, extra));
        return log;
    }

    public long startGameTime() {
        return startGameTime;
    }

    // the next tick writes the tail and the END marker
    public void stop() {
        ticksRemaining = 1;
    }

    /**
     * @return false once the log has finished and sent its tail
     */
    public boolean tick(Level level, BlockPos at, Supplier<CarActors> car, boolean cruiseOn, double cruiseTargetMs,
                        String terrainAssist, DriverInputs inputs) {
        if (ticksRemaining % sampleEveryTicks == 0) {
            CarActors actors = car.get();
            if (actors != null) {
                send(level, TelemetryLinePacket.KIND_ROW, TelemetryFormat.row(level, at, actors, startGameTime,
                        cruiseOn, cruiseTargetMs, extra, inputs));
            }
        }
        if (--ticksRemaining > 0) {
            return true;
        }
        for (String line : tail(level, car.get(), terrainAssist)) {
            send(level, TelemetryLinePacket.KIND_ROW, line);
        }
        send(level, TelemetryLinePacket.KIND_END, "");
        return false;
    }

    public static List<String> tail(Level level, @Nullable CarActors car, String terrainAssist) {
        List<String> out = new ArrayList<>();
        out.add("");
        out.add("config_option,value");
        out.add("world.gravity," + Gravity.of(level));
        out.add("car.terrainAssist," + terrainAssist);
        if (car != null) {
            if (!car.suspensions().isEmpty()) {
                SuspensionBlockEntity any = car.suspensions().get(0);
                out.add("car.mass," + any.getCachedCarMass());
                out.add("car.wheelMassScaled," + any.getScaledWheelMass());
                out.add("car.sprungMassPerWheel," + any.getSprungMassPerWheel());
            }
            for (int a = 0; a < car.suspensions().size(); a++) {
                SuspensionBlockEntity axle = car.suspensions().get(a);
                BlockPos ap = axle.getBlockPos();
                out.add("axle" + a + ".springPreset,\"" + axle.getSetupName() + "\"");
                out.add("axle" + a + ".pos,\"" + (ap.getX() + 0.5) + " " + (ap.getY() + 0.5) + " "
                        + (ap.getZ() + 0.5) + "\"");
                out.add("axle" + a + ".facing,\"" + axle.getFacing() + (axle.isFrontAxle() ? " front" : " rear")
                        + "\"");
                out.add("axle" + a + ".bodyModeOmegaDt," + axle.getBodyModeOmegaDt());
            }
        }
        out.addAll(Config.dumpForLog());
        if (car != null) {
            out.addAll(SpecDump.lines(level.registryAccess(), car));
        }
        return out;
    }

    private void send(Level level, int kind, String line) {
        if (level.getServer() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(recipient);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new TelemetryLinePacket(kind, line));
        }
    }
}
