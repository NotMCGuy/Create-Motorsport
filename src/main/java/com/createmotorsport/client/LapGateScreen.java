package com.createmotorsport.client;

import com.createmotorsport.block.entity.LapGateBlockEntity;
import com.createmotorsport.client.widget.FlatButton;
import com.createmotorsport.client.widget.NumberField;
import com.createmotorsport.menu.LapGateMenu;
import com.createmotorsport.network.RaceControlPacket;
import com.createmotorsport.network.SetEntrantPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LapGateScreen extends AbstractContainerScreen<LapGateMenu> {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 264;
    private static final int MAX_ROWS = 8;

    private static final int PANEL = 0xF0141414;
    private static final int BORDER = 0xFF404040;
    private static final int RULE = 0xFF2E2E2E;
    private static final int ROW = 0xFF1E1E1E;
    private static final int ROW_ALT = 0xFF232323;
    private static final int LABEL = 0xFF9A9A9A;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int WARN = 0xFFCB6A4A;

    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int ROW_H = 18;
    private static final int SET_ROW_1 = 22;
    private static final int SET_ROW_2 = 60;
    private static final int SET_ROW_3 = 100;
    private static final int SET_ROW_4 = 140;
    private static final int CTRL_ON_TEXT = -3;


    private EditBox delayBox;
    private NumberField lapsBox;
    private NumberField markerBox;
    private final List<EditBox> nameBoxes = new ArrayList<>();
    private final List<EditBox> gridBoxes = new ArrayList<>();
    private final List<String> builtNames = new ArrayList<>();
    private final List<Integer> builtGrids = new ArrayList<>();
    private final List<EditBox> allBoxes = new ArrayList<>();
    private FlatButton telemetryButton;
    private FlatButton directionButton;
    private FlatButton ghostButton;
    private FlatButton startButton;

    private boolean showSettings;

    public LapGateScreen(LapGateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        nameBoxes.clear();
        gridBoxes.clear();
        builtNames.clear();
        builtGrids.clear();
        allBoxes.clear();
        telemetryButton = null;
        directionButton = null;
        ghostButton = null;
        startButton = null;

        LapGateBlockEntity gate = menu.getGate();
        int x = leftPos + PAD;
        int right = leftPos + PANEL_W - PAD;

        addRenderableWidget(new FlatButton(right - 42, topPos + 5, 42, 14,
                Component.literal(showSettings ? "Back" : "Setup"), FlatButton.NEUTRAL, b -> {
            showSettings = !showSettings;
            rebuild();
        }));

        int y = topPos + HEADER_H + (isLinked(gate) ? 4 : 16);
        if (showSettings) {
            buildSettings(gate, x, right, y);
        } else {
            buildGrid(gate, x, right, y);
        }
    }

    // -------------------------------------------------------------------------------
    // GRID / default menu area

    private void buildGrid(LapGateBlockEntity gate, int x, int right, int y) {
        addRenderableWidget(new FlatButton(right - 56, y, 56, 14, Component.literal("Rescan"),
                FlatButton.NEUTRAL, b -> {
            pushEntrants();
            send(RaceControlPacket.ACTION_RESCAN, 0);
            rebuild();
        }));

        y += 20;

        List<LapGateBlockEntity.Entrant> entrants = entrants(gate);
        for (int i = 0; i < entrants.size() && i < MAX_ROWS; i++) {
            LapGateBlockEntity.Entrant e = entrants.get(i);

            EditBox gridBox = box(x + 4, y + 2, 20, 14, String.valueOf(e.grid), 2);
            gridBoxes.add(gridBox);
            EditBox nameBox = box(x + 30, y + 2, 150, 14, e.name, 24);
            nameBoxes.add(nameBox);
            builtNames.add(e.name);
            builtGrids.add(e.grid);
            y += ROW_H;
        }

        int footY = topPos + PANEL_H - 46;

        lapsBox = new NumberField(font, x + 38, footY, 26, 14, Component.empty(), 1, 999,
                laps -> send(RaceControlPacket.ACTION_SET_LAPS, (int) laps));
        lapsBox.setMaxLength(3);
        lapsBox.showValue(gate != null ? gate.getTotalLaps() : 3);
        addRenderableWidget(lapsBox);
        allBoxes.add(lapsBox);
        delayBox = box(x + 116, footY, 26, 14, "10", 3);

        boolean running = gate != null && gate.getState() != LapGateBlockEntity.RaceState.IDLE;
        startButton = new FlatButton(x, footY + 22, right - x, 18,
                Component.literal(running ? "Stop race" : "Start race"),
                running ? FlatButton.STOP : FlatButton.GO, b -> {
            if (running) {
                send(RaceControlPacket.ACTION_STOP, 0);
            } else {
                send(RaceControlPacket.ACTION_SET_LAPS, parse(lapsBox, 3));
                pushEntrants();
                send(RaceControlPacket.ACTION_START, parse(delayBox, 10));
            }
        });
        startButton.active = running || (gate != null && isLinked(gate) && !entrants.isEmpty());
        addRenderableWidget(startButton);
    }

    // ----------------------------------------------------------------------------------------
    // SETUP menu area

    private void buildSettings(LapGateBlockEntity gate, int x, int right, int y) {
        int r1 = y + SET_ROW_1 + CTRL_ON_TEXT;
        markerBox = new NumberField(font, x + 96, r1, 30, 14, Component.empty(), 0, 99,
                m -> send(RaceControlPacket.ACTION_SET_MARKER, (int) m));
        markerBox.setMaxLength(2);
        markerBox.showValue(gate != null ? gate.getMarker() : 0);
        addRenderableWidget(markerBox);
        allBoxes.add(markerBox);
        addRenderableWidget(new FlatButton(x + 132, r1, 44, 14, Component.literal("Apply"),
                FlatButton.NEUTRAL, b -> send(RaceControlPacket.ACTION_SET_MARKER, parse(markerBox, 0))));

        int r2 = y + SET_ROW_2 + CTRL_ON_TEXT;
        int r4 = y + SET_ROW_4 + CTRL_ON_TEXT;
        directionButton = new FlatButton(x + 96, r4, 84, 14, directionLabel(gate), FlatButton.NEUTRAL,
                b -> send(RaceControlPacket.ACTION_TOGGLE_DIRECTION, 0));
        addRenderableWidget(directionButton);

        ghostButton = new FlatButton(x + 96, r2, 56, 14, ghostLabel(gate), FlatButton.NEUTRAL,
                b -> send(RaceControlPacket.ACTION_TOGGLE_GHOST, 0));
        addRenderableWidget(ghostButton);
        addRenderableWidget(new FlatButton(x + 156, r2, 48, 14, Component.literal("Clear"),
                FlatButton.NEUTRAL, b -> {
            send(RaceControlPacket.ACTION_CLEAR_GHOST, 0);
            rebuild();
        }));

        int r3 = y + SET_ROW_3 + CTRL_ON_TEXT;
        telemetryButton = new FlatButton(x + 96, r3, 56, 14, telemetryLabel(gate), FlatButton.NEUTRAL,
                b -> send(RaceControlPacket.ACTION_TOGGLE_TELEMETRY, 0));
        addRenderableWidget(telemetryButton);
    }

    private EditBox box(int x, int y, int w, int h, String value, int maxLength) {
        EditBox b = new EditBox(font, x, y, w, h, Component.empty());
        b.setMaxLength(maxLength);
        b.setValue(value);
        addRenderableWidget(b);
        allBoxes.add(b);
        return b;
    }

    private void rebuild() {
        pushEntrants();
        clearWidgets();
        init();
    }

    @Override
    public void removed() {
        pushEntrants();
        super.removed();
    }

    private static boolean isLinked(LapGateBlockEntity gate) {
        return gate != null && gate.isLinked();
    }

    private List<LapGateBlockEntity.Entrant> entrants(LapGateBlockEntity gate) {
        return gate == null ? Collections.emptyList() : gate.getEntrants();
    }

    private static Component telemetryLabel(LapGateBlockEntity gate) {
        return Component.literal(gate != null && gate.isLogFullTelemetry() ? "On" : "Off");
    }

    private static Component directionLabel(LapGateBlockEntity gate) {
        return Component.literal(gate != null && gate.isReversed() ? "B to A" : "A to B");
    }

    private static Component ghostLabel(LapGateBlockEntity gate) {
        return Component.literal(gate != null && gate.isGhostEnabled() ? "On" : "Off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        LapGateBlockEntity gate = menu.getGate();
        if (telemetryButton != null) {
            telemetryButton.setMessage(telemetryLabel(gate));
            telemetryButton.muted(gate == null || !gate.isLogFullTelemetry());
        }
        if (directionButton != null) {
            directionButton.setMessage(directionLabel(gate));
        }
        if (ghostButton != null) {
            ghostButton.setMessage(ghostLabel(gate));
            ghostButton.muted(gate == null || !gate.isGhostEnabled());
        }
        if (startButton != null && !showSettings) {
            boolean running = gate != null && gate.getState() != LapGateBlockEntity.RaceState.IDLE;
            startButton.setMessage(Component.literal(running ? "Stop race" : "Start race"));
            startButton.color(running ? FlatButton.STOP : FlatButton.GO);
            startButton.active = running || (isLinked(gate) && !entrants(gate).isEmpty());
        }
    }

    private void pushEntrants() {
        for (int i = 0; i < nameBoxes.size(); i++) {
            String name = nameBoxes.get(i).getValue();
            int grid = parse(gridBoxes.get(i), i + 1);
            if (name.equals(builtNames.get(i)) && grid == builtGrids.get(i)) {
                continue;
            }
            builtNames.set(i, name);
            builtGrids.set(i, grid);
            PacketDistributor.sendToServer(new SetEntrantPacket(menu.getGatePos(), i, name, grid));
        }
    }

    private void send(int action, int value) {
        PacketDistributor.sendToServer(new RaceControlPacket(menu.getGatePos(), action, value));
    }

    private static int parse(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------------------------


    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, PANEL);
        g.renderOutline(leftPos, topPos, PANEL_W, PANEL_H, BORDER);
        g.fill(leftPos + 1, topPos + HEADER_H - 1, leftPos + PANEL_W - 1, topPos + HEADER_H, RULE);

        if (showSettings) {
            return;
        }
        LapGateBlockEntity gate = menu.getGate();
        int rows = Math.min(MAX_ROWS, Math.max(3, entrants(gate).size()));
        int y = topPos + HEADER_H + (isLinked(gate) ? 4 : 16) + 20;
        for (int i = 0; i < rows; i++) {
            g.fill(leftPos + PAD, y, leftPos + PANEL_W - PAD, y + ROW_H - 2, i % 2 == 0 ? ROW : ROW_ALT);
            y += ROW_H;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        LapGateBlockEntity gate = menu.getGate();
        g.drawString(font, "LAP GATE", PAD, 8, TEXT, false);
        drawStatusPill(g, gate);

        int y = HEADER_H + 4;
        if (!isLinked(gate)) {
            g.drawString(font, "Not linked yet", PAD, y, WARN, false);
            y += 12;
        }

        if (showSettings) {
            renderSettingsLabels(g, gate, y);
            return;
        }

        g.drawString(font, "GRID", PAD, y, LABEL, false);
        y += 20;

        List<LapGateBlockEntity.Entrant> live = entrants(gate);
        if (live.isEmpty()) {
            g.drawString(font, "No cars found nearby", PAD + 6, y + 6, LABEL, false);
            g.drawString(font, "Place a car within 512 blocks and press Rescan", PAD + 6, y + 18, LABEL,
                    false);
        } else {
            boolean running = gate.getState() == LapGateBlockEntity.RaceState.RUNNING;
            for (int i = 0; i < live.size() && i < MAX_ROWS; i++) {
                LapGateBlockEntity.Entrant e = live.get(i);
                if (running) {
                    String lap = e.finished ? "FIN" : "L" + e.lap + "/" + gate.getTotalLaps();
                    int lw = font.width(lap);
                    g.drawString(font, lap, PANEL_W - PAD - 6 - lw, y + 6,
                            e.finished ? 0xFF6ECB7A : TEXT, false);
                }
                y += ROW_H;
            }
        }

        int footY = PANEL_H - 46;
        g.drawString(font, "Laps", PAD + 4, footY + 4, LABEL, false);
        g.drawString(font, "Start in", PAD + 70, footY + 4, LABEL, false);
        g.drawString(font, "s", PAD + 134, footY + 4, LABEL, false);
    }

    private void renderSettingsLabels(GuiGraphics g, LapGateBlockEntity gate, int y) {
        g.drawString(font, "SETUP", PAD, y, LABEL, false);

        g.drawString(font, "Marker", PAD + 4, y + SET_ROW_1, TEXT, false);
        g.drawString(font, "0 is start/finish and runs the race,", PAD + 4, y + SET_ROW_1 + 12, LABEL,
                false);
        g.drawString(font, "Higher numbers are for sector gates", PAD + 4, y + SET_ROW_1 + 22, LABEL, false);

        g.drawString(font, "Ghost", PAD + 4, y + SET_ROW_2, TEXT, false);
        String best = gate != null && gate.hasGhost()
                ? "Best " + LapGateBlockEntity.formatTime(gate.getGhostLapTicks()) + " by "
                        + gate.getGhostName()
                : "No lap recorded yet";
        g.drawString(font, best, PAD + 4, y + SET_ROW_2 + 12, LABEL, false);

        g.drawString(font, "Full telemetry", PAD + 4, y + SET_ROW_3, TEXT, false);
        g.drawString(font, "Writes every car's physics to the CSV result file", PAD + 4, y + SET_ROW_3 + 12,
                LABEL, false);

        g.drawString(font, "Direction", PAD + 4, y + SET_ROW_4, TEXT, false);
        g.drawString(font, "Sets the direction of travel for a crossing", PAD + 4, y + SET_ROW_4 + 12,
                LABEL, false);
    }

    private void drawStatusPill(GuiGraphics g, LapGateBlockEntity gate) {
        String text;
        int color;
        if (gate == null) {
            text = "?";
            color = 0xFF4A4A4A;
        } else {
            switch (gate.getState()) {
                case COUNTDOWN -> {
                    text = "COUNTDOWN";
                    color = 0xFF8A6A2A;
                }
                case RUNNING -> {
                    text = "RUNNING";
                    color = 0xFF2F6B3A;
                }
                default -> {
                    text = "IDLE";
                    color = 0xFF3A3A3A;
                }
            }
        }
        int w = font.width(text) + 10;
        int x = PANEL_W - PAD - 42 - 6 - w;
        g.fill(x, 5, x + w, 19, color);
        g.drawString(font, text, x + 5, 8, TEXT, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : allBoxes) {
            if (box.isFocused() && keyCode != 256) {
                return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
