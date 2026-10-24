package com.createmotorsport.client.screen.tune;

import com.createmotorsport.client.widget.ConfirmDialog;
import com.createmotorsport.client.widget.FlatButton;
import com.createmotorsport.client.widget.HoverTip;
import com.createmotorsport.client.widget.Theme;
import com.createmotorsport.network.TunePacket;
import com.createmotorsport.physics.spec.TireSpec;
import com.createmotorsport.registry.SpecCodecs;
import com.createmotorsport.registry.SpecFields;
import com.createmotorsport.registry.SpecOverrides;
import com.createmotorsport.registry.SpecRegistries;
import com.createmotorsport.tune.Tunable;
import com.mojang.serialization.DataResult;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TuneScreen extends Screen {

    private static final int MAX_WIDTH = 360;
    private static final int MARGIN = 10;
    private static final int PAD = 8;
    private static final int ROW_H = 20;
    private static final int TOP = 24;
    private static final int BOTTOM = 34;
    private static final int BOX_W = 96;
    private static final int SMALL_W = 14;

    private sealed interface Line permits Header, Row {
    }

    private record Header(Tunable.Section<?> section, @Nullable TireSpec.Model model) implements Line {

        String text() {
            return model != null ? "Tyre Physics: " + model.label()
                    : section.title() + ": " + SpecRegistries.displayName(section.specId());
        }
    }

    private record Row(Tunable.Section<?> section, SpecFields.Field field) implements Line {

        String key() {
            return section.key() + "/" + field.path();
        }
    }

    @Nullable
    private final Screen parent;
    private final TuneSource source;
    private final boolean canTune;

    private List<Line> lines = List.of();
    private ConfirmDialog dialog;
    private String signature = "";
    private int page;
    private final Map<String, EditBox> boxes = new HashMap<>();
    private final Map<String, Row> rows = new HashMap<>();
    private final Map<String, String> shown = new HashMap<>();
    @Nullable
    private EditBox lastFocused;
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;

    public TuneScreen(@Nullable Screen parent, Component title, TuneSource source, boolean canTune) {
        super(title);
        this.parent = parent;
        this.source = source;
        this.canTune = canTune;
    }

    @Override
    protected void init() {
        dialog = new ConfirmDialog(font);
        rebuild(true);
    }

    @Override
    public void tick() {
        GuiEventListener focused = getFocused();
        if (lastFocused != null && focused != lastFocused) {
            commit(keyOf(lastFocused));
        }
        lastFocused = focused instanceof EditBox box && boxes.containsValue(box) ? box : null;
        rebuild(false);
    }

    private void rebuild(boolean force) {
        List<Tunable.Section<?>> sections = source.sections();
        if (sections == null) {
            onClose();
            return;
        }
        String next = signature(sections);
        if (!force && next.equals(signature)) {
            return;
        }
        signature = next;
        List<Line> built = new ArrayList<>();
        for (Tunable.Section<?> section : sections) {
            built.add(new Header(section, null));
            if (!(section.pack() instanceof TireSpec)) {
                fields(section).forEach(field -> built.add(new Row(section, field)));
                continue;
            }
            // splitting the compound row with the model specific info
            TireSpec.Model model = ((TireSpec) applied(section)).model();
            Set<String> own = SpecCodecs.modelFields(model);
            List<Line> physics = new ArrayList<>();
            physics.add(new Header(section, model));
            for (SpecFields.Field field : fields(section)) {
                (own.contains(field.path().split("\\.")[0]) ? physics : built).add(new Row(section, field));
            }
            built.addAll(physics);
        }
        lines = built;
        page = Math.min(page, pages() - 1);
        layout();
    }

    private void cycleSection(Header header, int step) {
        Tunable.Section<?> section = header.section();
        confirmIf(overridden(section, null) > 0 || section.overrides().contains("model"),
                () -> source.send(section.key(), TunePacket.CYCLE, "", String.valueOf(step)));
    }

    // see HeldTire.onModelChange
    private void cycleModel(Header header, int step) {
        TireSpec.Model[] models = TireSpec.Model.values();
        TireSpec.Model next = models[Math.floorMod(header.model().ordinal() + step, models.length)];
        Tunable.Section<?> section = header.section();
        confirmIf(overridden(section, header.model()) > 0,
                () -> source.send(section.key(), TunePacket.SET, "model", next.id()));
    }

    // confirms before overwriting user changes
    private void confirmIf(boolean costs, Runnable action) {
        if (!costs) {
            action.run();
            return;
        }
        dialog.open(Component.literal("Switching this setting will reset values to default, okay?"), Component.empty(),
                Component.literal("Reset"), action);
    }

    private int overridden(Tunable.Section<?> section, @Nullable TireSpec.Model model) {
        Set<String> own = model == null ? null : SpecCodecs.modelFields(model);
        int n = 0;
        for (Line line : lines) {
            if (line instanceof Row row && row.section() == section && row.field().overridden()
                    && (own == null || own.contains(row.field().path().split("\\.")[0]))) {
                n++;
            }
        }
        return n;
    }

    private static <T> T applied(Tunable.Section<T> section) {
        return SpecOverrides.apply(section.codec(), section.pack(), section.overrides());
    }

    private static String signature(List<Tunable.Section<?>> sections) {
        StringBuilder sb = new StringBuilder();
        for (Tunable.Section<?> section : sections) {
            sb.append(section.key()).append('=').append(section.specId()).append(section.overrides()).append(';');
        }
        return sb.toString();
    }

    private static <T> List<SpecFields.Field> fields(Tunable.Section<T> section) {
        return SpecFields.of(section.codec(), section.pack(), section.overrides());
    }

    private void cycle(Header header, int step) {
        if (header.model() != null) {
            cycleModel(header, step);
        } else {
            cycleSection(header, step);
        }
    }

    private void layout() {
        String focusedKey = lastFocused == null ? null : keyOf(lastFocused);
        String focusedText = lastFocused == null ? null : lastFocused.getValue();
        clearWidgets();
        dialog.setParentBounds(left(), top(), right() - left(), bottom() - top());
        boxes.clear();
        rows.clear();
        shown.clear();
        lastFocused = null;

        int perPage = rowsPerPage();
        int from = page * perPage;
        int boxX = right() - PAD - SMALL_W - 4 - BOX_W;
        for (int i = from; i < Math.min(lines.size(), from + perPage); i++) {
            int y = rowY(i - from) + 3;
            Line line = lines.get(i);
            if (line instanceof Header header && (header.model() != null || header.section().cycle() != null)) {
                FlatButton previous = new FlatButton(right() - PAD - SMALL_W * 2 - 4, y, SMALL_W, 14,
                        Component.literal("<"), Theme.NEUTRAL, b -> cycle(header, -1));
                FlatButton nextSpec = new FlatButton(right() - PAD - SMALL_W, y, SMALL_W, 14,
                        Component.literal(">"), Theme.NEUTRAL, b -> cycle(header, 1));
                previous.active = canTune;
                nextSpec.active = canTune;
                addRenderableWidget(previous);
                addRenderableWidget(nextSpec);
            } else if (line instanceof Row row) {
                SpecFields.Field field = row.field();
                String key = row.key();
                rows.put(key, row);
                if (field.kind() == SpecFields.Kind.FLAG) {
                    boolean on = Boolean.parseBoolean(field.value());
                    FlatButton toggle = new FlatButton(boxX, y, BOX_W, 14, Component.literal(on ? "On" : "Off"),
                            on ? Theme.GO : Theme.NEUTRAL,
                            b -> source.send(row.section().key(), TunePacket.SET, field.path(), on ? "false" : "true"))
                            .muted(!on);
                    toggle.active = canTune;
                    addRenderableWidget(toggle);
                } else {
                    EditBox box = new EditBox(font, boxX, y, BOX_W, 14, Component.literal(field.label()));
                    box.setMaxLength(256);
                    box.setValue(key.equals(focusedKey) ? focusedText : field.value());
                    box.setEditable(canTune && field.editable());
                    box.setTextColor(field.overridden() ? Theme.TEXT : Theme.TEXT_DIM);
                    box.setTextColorUneditable(Theme.TEXT_DIM);
                    box.moveCursorToStart(false);
                    addRenderableWidget(box);
                    boxes.put(key, box);
                    shown.put(key, field.value());
                    if (key.equals(focusedKey)) {
                        setFocused(box);
                        lastFocused = box;
                    }
                }
                if (field.overridden() && canTune) {
                    addRenderableWidget(new FlatButton(right() - PAD - SMALL_W, y, SMALL_W, 14,
                            Component.literal("x"), Theme.NEUTRAL,
                            b -> source.send(row.section().key(), TunePacket.CLEAR, field.path(), "")));
                }
            }
        }

        if (pages() > 1) {
            int y = bottom() - BOTTOM + 14;
            int cx = (left() + right()) / 2;
            addRenderableWidget(new FlatButton(cx - 40, y, SMALL_W, 14, Component.literal("<"),
                    Theme.NEUTRAL, b -> turn(-1)));
            addRenderableWidget(new FlatButton(cx + 40 - SMALL_W, y, SMALL_W, 14, Component.literal(">"),
                    Theme.NEUTRAL, b -> turn(1)));
        }
    }

    private void turn(int by) {
        commitFocused();
        page = Math.floorMod(page + by, pages());
        lastFocused = null;
        layout();
    }

    private void commitFocused() {
        if (getFocused() instanceof EditBox box && boxes.containsValue(box)) {
            commit(keyOf(box));
        }
    }

    private void commit(@Nullable String key) {
        EditBox box = key == null ? null : boxes.get(key);
        Row row = key == null ? null : rows.get(key);
        if (box == null || row == null || !canTune || !row.field().editable()) {
            return;
        }
        String text = box.getValue().trim();
        if (text.equals(shown.get(key))) {
            return;
        }
        DataResult<CompoundTag> checked = row.section().check(row.field().path(), text);
        if (checked.error().isPresent()) {
            status = Component.literal(row.field().label() + ": " + checked.error().get().message());
            statusColor = Theme.BAD;
            box.setTextColor(Theme.BAD);
            return;
        }
        source.send(row.section().key(), TunePacket.SET, row.field().path(), text);
        shown.put(key, text);
        box.setTextColor(Theme.TEXT); // changes color to tell user its updated
        status = Component.empty();
    }

    @Nullable
    private String keyOf(EditBox box) {
        for (Map.Entry<String, EditBox> entry : boxes.entrySet()) {
            if (entry.getValue() == box) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dialog.keyPressed(keyCode)) {
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && getFocused() instanceof EditBox box && boxes.containsValue(box)) {
            commit(keyOf(box));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return dialog.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return dialog.charTyped() || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int mx = dialog.isOpen() ? -1 : mouseX, my = dialog.isOpen() ? -1 : mouseY;
        super.render(g, mx, my, partialTick);
        Row hovered = dialog.isOpen() ? null : rowAt(mouseX, mouseY);
        Component tip = hovered == null ? null : tip(hovered);
        if (tip != null) {
            HoverTip.draw(g, font, tip, mouseX, mouseY);
        }
        dialog.render(g, mouseX, mouseY);
    }

    // tooltip is going to be the whole entire row for now including the number box but that might get annoying, we'll see
    @Nullable
    private Row rowAt(int mouseX, int mouseY) {
        if (mouseX < left() + PAD || mouseX >= right() - PAD || mouseY < rowY(0)) {
            return null;
        }
        int onPage = (mouseY - rowY(0)) / ROW_H;
        int index = page * rowsPerPage() + onPage;
        return onPage < rowsPerPage() && index < lines.size() && lines.get(index) instanceof Row row ? row : null;
    }

    // tune.createmotorsport.<section>.<path> in the lang file
    @Nullable
    private static Component tip(Row row) {
        String base = "tune.createmotorsport." + row.section().key() + ".";
        String path = row.field().path();
        if (row.section().pack() instanceof TireSpec) {
            String modelKey = base + ((TireSpec) applied(row.section())).model().id() + "." + path;
            if (I18n.exists(modelKey)) {
                return Component.translatable(modelKey);
            }
        }
        String key = base + path;
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog.isOpen()) {
            return true;
        }
        if (pages() > 1 && scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int l = left(), r = right(), t = top(), b = bottom();
        g.fill(l, t, r, b, Theme.PANEL);
        g.fill(l, t, r, t + 1, Theme.BORDER);
        g.fill(l, b - 1, r, b, Theme.BORDER);
        g.fill(l, t, l + 1, b, Theme.BORDER);
        g.fill(r - 1, t, r, b, Theme.BORDER);
        g.drawString(font, title, l + PAD, t + 8, Theme.TEXT, false);

        int perPage = rowsPerPage();
        int from = page * perPage;
        int labelRight = r - PAD - SMALL_W - 4 - BOX_W - 6;
        for (int i = from; i < Math.min(lines.size(), from + perPage); i++) {
            int y = rowY(i - from);
            Line line = lines.get(i);
            if (line instanceof Header header) {
                g.fill(l + PAD, y, r - PAD, y + ROW_H - 1, Theme.HEADER);
                g.drawString(font, header.text(), l + PAD + 4, y + 6, Theme.TEXT, false);
            } else if (line instanceof Row row) {
                g.fill(l + PAD, y, r - PAD, y + ROW_H - 1, (i % 2 == 0) ? Theme.ROW : Theme.ROW_ALT);
                String label = font.plainSubstrByWidth(row.field().label(), labelRight - (l + PAD + 4));
                g.drawString(font, label, l + PAD + 4, y + 6,
                        row.field().overridden() ? Theme.TEXT : Theme.TEXT_DIM, false);
            }
        }

        int footer = b - BOTTOM;
        Component note = canTune ? status : Component.literal("Tuning is limited to operators on this server");
        g.drawString(font, font.plainSubstrByWidth(note.getString(), r - l - PAD * 2), l + PAD, footer + 2,
                canTune ? statusColor : Theme.TEXT_DIM, false);
        if (pages() > 1) {
            String pager = (page + 1) + " / " + pages();
            g.drawString(font, pager, (l + r - font.width(pager)) / 2, footer + 17, Theme.TEXT, false);
        }
    }

    private int rowsPerPage() {
        return Math.max(1, (bottom() - top() - TOP - BOTTOM) / ROW_H);
    }

    private int pages() {
        return Math.max(1, (lines.size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private int rowY(int indexOnPage) {
        return top() + TOP + indexOnPage * ROW_H;
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int right() {
        return left() + panelWidth();
    }

    private int panelWidth() {
        return Math.min(MAX_WIDTH, width - MARGIN * 2);
    }

    private int top() {
        return MARGIN;
    }

    private int bottom() {
        return height - MARGIN;
    }

    @Override
    public void onClose() {
        commitFocused();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
