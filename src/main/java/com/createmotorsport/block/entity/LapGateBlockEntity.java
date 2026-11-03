package com.createmotorsport.block.entity;

import com.createmotorsport.Config;
import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.race.Entrant;
import com.createmotorsport.race.GateLine;
import com.createmotorsport.race.Ghost;
import com.createmotorsport.race.RaceChunks;
import com.createmotorsport.race.RaceResults;
import com.createmotorsport.race.RaceState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class LapGateBlockEntity extends SmartBlockEntity {

    // ==========================================
    // CONSTANTS
    // ==========================================

    public static final int MARKER_START_FINISH = 0;
    public static final double RACE_SCAN_RADIUS = 512.0;
    private static final double GATE_HEIGHT_BAND = 6.0;
    private static final int CROSS_DEBOUNCE_TICKS = 40;
    private static final int TELEMETRY_SAMPLE_TICKS = 4;
    private static final int TELEMETRY_MAX_ROWS = 40000;

    // ==========================================
    // LOADED GATES
    // ==========================================

    private static final java.util.Set<LapGateBlockEntity> LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static int gateSetVersion;

    // Proximity gate the lap gate since we have to force load it
    public static LapGateBlockEntity controllableBy(net.minecraft.world.entity.player.Player player,
                                                    BlockPos pos) {
        if (player == null || pos == null) {
            return null;
        }
        net.minecraft.world.level.Level level = player.level();
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof LapGateBlockEntity gate ? gate : null;
    }

    // ==========================================
    // STATE
    // ==========================================

    // this post and its pair
    private BlockPos partner;
    private int marker = MARKER_START_FINISH;
    private boolean reversed; // for direction of travel setting

    private List<LapGateBlockEntity> cachedGates;
    private int cachedGatesVersion = -1;

    private RaceState state = RaceState.IDLE;
    private int countdownTicks;
    private int totalLaps = 3;
    private boolean logFullTelemetry;
    private long raceStartGameTime;
    private UUID csvRecipient;
    private final List<Entrant> entrants = new ArrayList<>();
    private int lastAnnouncedSecond = -1;
    private final List<String> telemetryRows = new ArrayList<>();
    private String telemetryHeader;
    private final Ghost ghost = new Ghost();
    private final RaceChunks raceChunks = new RaceChunks();

    // ==========================================
    // CONSTRUCTION / LIFECYCLE
    // ==========================================

    public LapGateBlockEntity(BlockPos pos, BlockState state) {
        super(CreateMotorsport.LAP_GATE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LOADED.add(this);
            gateSetVersion++;
            if (state == RaceState.IDLE && !raceChunks.isEmpty()
                    && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                releaseForcedChunks(serverLevel);
            }
        }
    }

    @Override
    public void remove() {
        if (state != RaceState.IDLE) {
            setRaceChunksForced(false);
        }
        LOADED.remove(this);
        gateSetVersion++;
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        LOADED.remove(this);
        gateSetVersion++;
        super.onChunkUnloaded();
    }

    // ==========================================
    // LINKING THE POSTS
    // ==========================================

    public BlockPos getPartner() {
        return partner;
    }

    public boolean isLinked() {
        return partner != null;
    }

    public int getMarker() {
        return marker;
    }

    public void setMarker(int marker) {
        this.marker = Math.max(0, marker);
        gateSetVersion++;
        setChanged();
        sendData();
        LapGateBlockEntity other = partnerGate();
        if (other != null && other.marker != this.marker) {
            other.marker = this.marker;
            other.setChanged();
            other.sendData();
        }
    }

    @org.jetbrains.annotations.Nullable
    private LapGateBlockEntity partnerGate() {
        if (partner != null && level != null
                && level.getBlockEntity(partner) instanceof LapGateBlockEntity other
                && this.getBlockPos().equals(other.partner)) {
            return other;
        }
        return null;
    }

    public void linkTo(LapGateBlockEntity other) {
        gateSetVersion++;
        this.partner = other.getBlockPos();
        other.partner = this.getBlockPos();
        other.marker = this.marker;
        setChanged();
        other.setChanged();
        sendData();
        other.sendData();
    }

    public void clearLink() {
        if (partner != null && level != null
                && level.getBlockEntity(partner) instanceof LapGateBlockEntity other
                && this.getBlockPos().equals(other.partner)) {
            other.partner = null;
            other.setChanged();
            other.sendData();
        }
        partner = null;
        gateSetVersion++;
        setChanged();
        sendData();
    }

    public Vec3 lineCenter() {
        if (partner == null) {
            return null;
        }
        return Vec3.atCenterOf(worldPosition).add(Vec3.atCenterOf(partner)).scale(0.5);
    }

    public boolean isReversed() {
        return reversed;
    }

    public void setReversed(boolean value) {
        reversed = value;
        setChanged();
        sendData();
        LapGateBlockEntity other = partnerGate();
        if (other != null && other.reversed != value) {
            other.reversed = value;
            other.setChanged();
            other.sendData();
        }
    }

    // ==========================================
    // GATE ORDERING
    // ==========================================

    private List<LapGateBlockEntity> orderedGates() {
        if (cachedGates != null && cachedGatesVersion == gateSetVersion) {
            return cachedGates;
        }
        List<LapGateBlockEntity> built = buildOrderedGates();
        cachedGates = built;
        cachedGatesVersion = gateSetVersion;
        return built;
    }

    private List<LapGateBlockEntity> buildOrderedGates() {
        List<LapGateBlockEntity> gates = new ArrayList<>();
        if (level == null) {
            return gates;
        }
        gates.add(this);
        if (level instanceof net.minecraft.server.level.ServerLevel) {
            List<LapGateBlockEntity> others = new ArrayList<>();
            for (LapGateBlockEntity gate : LOADED) {
                if (gate == this || gate.isRemoved() || gate.getLevel() != level || !gate.isLinked()) {
                    continue;
                }
                if (!gate.getBlockPos().closerThan(worldPosition, RACE_SCAN_RADIUS)) {
                    continue;
                }
                if (gate.marker == MARKER_START_FINISH) {
                    continue;
                }
                others.add(gate);
            }

            others.sort(java.util.Comparator.<LapGateBlockEntity>comparingInt(g -> g.marker)
                    .thenComparingLong(g -> g.getBlockPos().asLong()));
            List<LapGateBlockEntity> deduped = new ArrayList<>();
            for (LapGateBlockEntity g : others) {
                boolean partnerAlready = false;
                for (LapGateBlockEntity kept : deduped) {
                    if (kept.getBlockPos().equals(g.partner)) {
                        partnerAlready = true;
                        break;
                    }
                }
                if (!partnerAlready) {
                    deduped.add(g);
                }
            }
            gates.addAll(deduped);
        }
        return gates;
    }

    // ==========================================
    // RACE SETUP
    // ==========================================

    public RaceState getState() {
        return state;
    }

    public int getTotalLaps() {
        return totalLaps;
    }

    public void setTotalLaps(int laps) {
        this.totalLaps = Math.max(1, laps);
        setChanged();
        sendData();
    }

    public boolean isLogFullTelemetry() {
        return logFullTelemetry;
    }

    public void setLogFullTelemetry(boolean on) {
        this.logFullTelemetry = on;
        setChanged();
        sendData();
    }

    public List<Entrant> getEntrants() {
        return entrants;
    }

    public void scanForCars() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (state != RaceState.IDLE) {
            return;
        }
        List<Entrant> found = new ArrayList<>();
        int n = 0;
        Vec3 gateCenter = Vec3.atCenterOf(worldPosition);
        for (SteeringWheelBlockEntity wheel : SteeringWheelBlockEntity.serverLoaded()) {
            if (wheel.isRemoved()) {
                continue;
            }
            Vec3 carPos = wheel.carWorldPosition();
            if (carPos == null) {
                continue;
            }
            if (carPos.distanceToSqr(gateCenter) > RACE_SCAN_RADIUS * RACE_SCAN_RADIUS) {
                continue;
            }
            n++;
            Entrant existing = findEntrant(wheel.getBlockPos());
            found.add(existing != null ? existing : new Entrant(wheel.getBlockPos(), "Car " + n, n));
        }
        entrants.clear();
        entrants.addAll(found);
        setChanged();
        sendData();
    }

    private Entrant findEntrant(BlockPos wheelPos) {
        for (Entrant e : entrants) {
            if (e.wheelPos.equals(wheelPos)) {
                return e;
            }
        }
        return null;
    }

    public void setEntrantName(int index, String name) {
        if (index >= 0 && index < entrants.size()) {
            entrants.get(index).name = name;
            setChanged();
            sendData();
        }
    }

    public void setEntrantGrid(int index, int grid) {
        if (index >= 0 && index < entrants.size()) {
            entrants.get(index).grid = grid;
            setChanged();
            sendData();
        }
    }

    // ==========================================
    // START / STOP
    // ==========================================

    // Start race begins countdown after delay, and syncs data
    public void startRace(ServerPlayer starter, int delaySeconds) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (marker != MARKER_START_FINISH) {
            starter.sendSystemMessage(Component.literal(
                    "§c[Race] Only the start/finish gate (marker 0) can run a race"));
            return;
        }
        if (!isLinked()) {
            starter.sendSystemMessage(Component.literal(
                    "§c[Race] Click this gate with another gate to link them together and draw a line"));
            return;
        }
        if (entrants.isEmpty()) {
            scanForCars();
        }
        if (entrants.isEmpty()) {
            starter.sendSystemMessage(Component.literal("§c[Race] No assembled cars found nearby"));
            return;
        }

        telemetryRows.clear();
        telemetryHeader = null;
        csvRecipient = starter.getUUID();
        state = RaceState.COUNTDOWN;
        countdownTicks = Math.max(0, delaySeconds) * 20 + 60;
        lastAnnouncedSecond = -1;

        int gateCount = orderedGates().size();
        for (Entrant e : entrants) {
            e.lap = 0;
            e.nextMarker = 0;
            e.finished = false;
            e.splits.clear();
            e.lastCrossTick = 0L;
            e.lapStartTick = 0L;
            e.firstCrossTick = -1L;
            e.recording.clear();
            e.lastSide = new double[gateCount];
            e.lastCrossAt = new long[gateCount];
            for (int i = 0; i < gateCount; i++) {
                e.lastSide[i] = Double.NaN;
            }
        }
        setRaceChunksForced(true);
        broadcast("§e[Race] Starting race on 'Go'...");
        setChanged();
        sendData();
    }

    public void stopRace(boolean early) {
        stopRace(early, null);
    }

    public void stopRace(boolean early, ServerPlayer requester) {
        if (state == RaceState.IDLE) {
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                        "§7[Race] No race is running (it may have already finished)"));
            }
            return;
        }
        state = RaceState.IDLE;
        countdownTicks = 0;
        setRaceChunksForced(false);
        broadcastGhost(0L, false);
        broadcast(early ? "§c[Race] Race stopped early" : "§a[Race] Race finished");
        if (csvRecipient == null && requester != null) {
            csvRecipient = requester.getUUID();
        }
        writeResultsCsv(requester);
        csvRecipient = null;
        setChanged();
        sendData();
    }

    // Gotta chunk load the lap gates. We will consider server balancing later, because I know this could cause issues if we let anyone place a chunk loader.
    private void setRaceChunksForced(boolean forced) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (!forced) {
            releaseForcedChunks(serverLevel);
            return;
        }
        raceChunks.clear();
        for (LapGateBlockEntity gate : orderedGates()) {
            raceChunks.force(serverLevel, gate.getBlockPos());
            if (gate.partner != null) {
                raceChunks.force(serverLevel, gate.partner);
            }
        }
        raceChunks.force(serverLevel, worldPosition);
        if (partner != null) {
            raceChunks.force(serverLevel, partner);
        }
        setChanged();
    }

    private void releaseForcedChunks(net.minecraft.server.level.ServerLevel serverLevel) {
        raceChunks.release(serverLevel);
        setChanged();
    }

    // ==========================================
    // RACE TICK
    // ==========================================

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || marker != MARKER_START_FINISH) {
            return;
        }
        if (state == RaceState.COUNTDOWN) {
            tickCountdown();
        } else if (state == RaceState.RUNNING) {
            tickRace();
        }
    }

    private void tickCountdown() {
        countdownTicks--;
        int secondsLeft = (countdownTicks + 19) / 20;
        if (secondsLeft <= 3 && secondsLeft > 0 && secondsLeft != lastAnnouncedSecond) {
            lastAnnouncedSecond = secondsLeft;
            broadcast("§e[Race] §l" + secondsLeft);
        }
        if (countdownTicks <= 0) {
            state = RaceState.RUNNING;
            raceStartGameTime = level.getGameTime();
            broadcast("§a[Race] §lGO!");
            broadcastGhost(raceStartGameTime + ghost.leadInTicks(), true);
            setChanged();
            sendData();
        }
    }

    private void tickRace() {
        long sinceStart = level.getGameTime() - raceStartGameTime;
        boolean sampleNow = ghost.enabled() && sinceStart % Ghost.SAMPLE_TICKS == 0;
        boolean telemetryNow = logFullTelemetry && sinceStart % TELEMETRY_SAMPLE_TICKS == 0
                && telemetryRows.size() < TELEMETRY_MAX_ROWS;
        List<LapGateBlockEntity> gates = orderedGates();
        for (Entrant e : entrants) {
            if (e.finished) {
                continue;
            }
            SteeringWheelBlockEntity wheel = SteeringWheelBlockEntity.findLoadedAt(e.wheelPos);
            if (wheel == null) {
                continue;
            }
            Vec3 carPos = wheel.carWorldPosition();
            if (carPos == null) {
                continue;
            }
            if (e.lastSide.length != gates.size()) {
                double[] resized = new double[gates.size()];
                java.util.Arrays.fill(resized, Double.NaN);
                System.arraycopy(e.lastSide, 0, resized, 0, Math.min(e.lastSide.length, resized.length));
                e.lastSide = resized;
                long[] resizedAt = new long[gates.size()];
                System.arraycopy(e.lastCrossAt, 0, resizedAt, 0, Math.min(e.lastCrossAt.length, resizedAt.length));
                e.lastCrossAt = resizedAt;
            }

            if (sampleNow && e.recording.size() < Ghost.MAX_SAMPLES) {
                e.recording.add(new float[]{(float) carPos.x, (float) carPos.y, (float) carPos.z});
            }
            if (telemetryNow) {
                if (telemetryHeader == null) {
                    String header = wheel.raceTelemetryHeader();
                    if (header != null) {
                        telemetryHeader = "car," + header;
                    }
                }
                String row = wheel.raceTelemetryRow();
                if (row != null) {
                    telemetryRows.add(e.name.replace(',', ' ') + "," + row);
                }
            }
            for (int i = 0; i < gates.size(); i++) {
                LapGateBlockEntity gate = gates.get(i);
                boolean didCross = gate.crossed(carPos, e, i);
                if (!didCross || i != e.nextMarker) {
                    continue;
                }
                if (level.getGameTime() - e.lastCrossAt[i] < CROSS_DEBOUNCE_TICKS) {
                    continue;
                }
                e.lastCrossAt[i] = level.getGameTime();
                registerCrossing(e, i, gates.size());
            }
        }
        if (allFinished()) {
            stopRace(false);
        }
    }

    // A crossing is detected by watching which side of the segment a car is on, and looking for a sign flip between ticks
    private boolean crossed(Vec3 carPos, Entrant e, int gateIndex) {
        if (partner == null) {
            return false;
        }
        return GateLine.crossed(Vec3.atCenterOf(worldPosition), Vec3.atCenterOf(partner), reversed, GATE_HEIGHT_BAND,
                carPos, e, gateIndex);
    }

    private void registerCrossing(Entrant e, int gateIndex, int gateCount) {
        long now = level.getGameTime() - raceStartGameTime;
        e.splits.add(now);
        e.lastCrossTick = now;
        // Count lap at the 0 marker only
        e.nextMarker = (gateIndex + 1) % Math.max(1, gateCount);
        if (gateIndex != MARKER_START_FINISH) {
            broadcast("§7[Race] " + e.name + " sector " + gateIndex + " at " + formatTime(now));
            return;
        }

        // Theres probably a better way to do this, but this just gives the cars time to cross the grid
        long lapTime = now - e.lapStartTick;
        long minLap = (long) (Config.RACE_MIN_LAP_TIME.getAsDouble() * 20.0);
        boolean lapCompleted = lapTime >= minLap;

        if (lapCompleted) {
            considerGhost(e, lapTime);
        }
        if (e.firstCrossTick < 0L) {
            e.firstCrossTick = now;
        }
        e.lapStartTick = now;
        e.recording.clear();
        if (!lapCompleted) {
            broadcast("§7[Race] " + e.name + " started its lap at " + formatTime(now));
            return;
        }

        e.lap++;
        if (e.lap >= totalLaps) {
            e.finished = true;
            broadcast("§b[Race] §f" + e.name + " §afinished! §7(" + formatTime(now) + ")");
        } else {
            broadcast("§b[Race] §f" + e.name + " §7lap " + e.lap + "/" + totalLaps
                    + " in §f" + formatTime(lapTime) + " §7(total " + formatTime(now) + ")");
        }
    }

    private boolean allFinished() {
        for (Entrant e : entrants) {
            if (!e.finished) {
                return false;
            }
        }
        return !entrants.isEmpty();
    }

    // ==========================================
    // GHOST
    // ==========================================

    public boolean isGhostEnabled() {
        return ghost.enabled();
    }

    public void setGhostEnabled(boolean on) {
        ghost.setEnabled(on);
        setChanged();
        sendData();
    }

    public boolean hasGhost() {
        return ghost.present(level != null && level.isClientSide);
    }

    public long getGhostLapTicks() {
        return ghost.lapTicks();
    }

    public String getGhostName() {
        return ghost.name();
    }

    public void clearGhost() {
        ghost.clear();
        setChanged();
        sendData();
    }

    private void broadcastGhost(long startGameTime, boolean playing) {
        if (!hasGhost() || !ghost.enabled() || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        PacketDistributor.sendToPlayersNear(serverLevel, null,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                RACE_SCAN_RADIUS, ghost.packet(startGameTime, playing));
    }

    // store the ghost if it beats the old one
    private void considerGhost(Entrant e, long lapTicks) {
        if (!ghost.offer(e, lapTicks)) {
            return;
        }
        broadcast("§d[Race] §fBest lap by " + e.name + " §7(" + formatTime(lapTicks) + "), new ghost saved");
        broadcastGhost(raceStartGameTime + ghost.leadInTicks(), true);
        setChanged();
        sendData();
    }

    // ==========================================
    // BROADCAST RESULTS
    // ==========================================

    private void broadcast(String message) {
        if (level == null || level.getServer() == null) {
            return;
        }
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static String formatTime(long ticks) {
        return RaceResults.formatTime(ticks);
    }

    private void writeResultsCsv(ServerPlayer fallback) {
        if (level == null || level.getServer() == null) {
            return;
        }
        ServerPlayer player = csvRecipient == null ? null
                : level.getServer().getPlayerList().getPlayer(csvRecipient);
        if (player == null) {
            player = fallback;
        }
        if (player == null) {
            return;
        }
        RaceResults.send(player, entrants, telemetryHeader, telemetryRows);
        if (!entrants.isEmpty()) {
            telemetryRows.clear();
            telemetryHeader = null;
        }
    }

    // ==========================================
    // NBT
    // ==========================================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (partner != null) {
            tag.putLong("Partner", partner.asLong());
        }
        tag.putInt("Marker", marker);
        tag.putString("State", state.name());
        tag.putInt("TotalLaps", totalLaps);
        tag.putBoolean("LogFull", logFullTelemetry);
        tag.putInt("Countdown", countdownTicks);
        tag.putLong("RaceStart", raceStartGameTime);
        ListTag list = new ListTag();
        for (Entrant e : entrants) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Wheel", e.wheelPos.asLong());
            entry.putString("Name", e.name);
            entry.putInt("Grid", e.grid);
            entry.putInt("Lap", e.lap);
            entry.putBoolean("Finished", e.finished);
            if (clientPacket) {
                list.add(entry);
                continue;
            }
            entry.putInt("NextMarker", e.nextMarker);
            entry.putLong("LapStart", e.lapStartTick);
            entry.putLong("FirstCross", e.firstCrossTick);
            entry.putLong("LastCross", e.lastCrossTick);
            long[] splits = new long[e.splits.size()];
            for (int si = 0; si < splits.length; si++) {
                splits[si] = e.splits.get(si);
            }
            entry.putLongArray("Splits", splits);
            list.add(entry);
        }
        tag.put("Entrants", list);
        tag.putBoolean("Reversed", reversed);
        ghost.write(tag, clientPacket);
        if (clientPacket) {
            return;
        }
        raceChunks.write(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        partner = tag.contains("Partner") ? BlockPos.of(tag.getLong("Partner")) : null;
        marker = tag.getInt("Marker");
        try {
            state = RaceState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException ignored) {
            state = RaceState.IDLE;
        }
        raceStartGameTime = tag.getLong("RaceStart");
        if (!clientPacket && state != RaceState.IDLE && raceStartGameTime <= 0L) {
            state = RaceState.IDLE;
        }
        totalLaps = Math.max(1, tag.getInt("TotalLaps"));
        logFullTelemetry = tag.getBoolean("LogFull");
        countdownTicks = tag.getInt("Countdown");
        entrants.clear();
        ListTag list = tag.getList("Entrants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Entrant e = new Entrant(BlockPos.of(entry.getLong("Wheel")),
                    entry.getString("Name"), entry.getInt("Grid"));
            e.lap = entry.getInt("Lap");
            e.finished = entry.getBoolean("Finished");
            e.nextMarker = Math.max(0, entry.getInt("NextMarker"));
            e.lapStartTick = entry.getLong("LapStart");
            e.firstCrossTick = entry.contains("FirstCross") ? entry.getLong("FirstCross") : -1L;
            e.lastCrossTick = entry.getLong("LastCross");
            for (long split : entry.getLongArray("Splits")) {
                e.splits.add(split);
            }
            entrants.add(e);
        }
        reversed = tag.getBoolean("Reversed");
        ghost.read(tag, clientPacket);
        if (clientPacket) {
            return;
        }
        raceChunks.read(tag);
    }
}
