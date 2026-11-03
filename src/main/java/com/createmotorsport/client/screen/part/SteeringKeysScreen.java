package com.createmotorsport.client.screen.part;

import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity.SteeringControl;
import com.createmotorsport.client.input.GamepadCodes;
import com.createmotorsport.client.input.GamepadInput;
import com.createmotorsport.client.input.MouseCodes;
import com.createmotorsport.client.input.MouseInput;
import com.createmotorsport.client.widget.FlatButton;
import com.createmotorsport.client.widget.Theme;
import com.createmotorsport.network.SetSteeringKeyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class SteeringKeysScreen extends Screen {

    private static final int ROW_H = 19;
    private static final int COL_W = 150;
    private static final int BIND_OFF = 92;
    private static final int BIND_W = 54;
    private static final int ROWS = (SteeringWheelBlockEntity.CONTROLS.length + 1) / 2;
    private static final int W = 8 + COL_W * 2 + 8 + 4;
    private static final int H = 26 + ROWS * ROW_H + 6;

    private final Screen parent;
    private final BlockPos pos;
    private int left;
    private int top;
    private int capturing = -1;

    public SteeringKeysScreen(Screen parent, BlockPos pos) {
        super(Component.literal("Keybinds"));
        this.parent = parent;
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new FlatButton(left + W - 8 - 40, top + 4, 40, 12, Component.literal("Back"),
                Theme.NEUTRAL, b -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int colX(int i) {
        return left + 8 + (i < ROWS ? 0 : COL_W + 4);
    }

    private int rowY(int i) {
        return top + 26 + (i < ROWS ? i : i - ROWS) * ROW_H;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        g.fill(left, top, left + W, top + H, Theme.PANEL);
        g.fill(left, top, left + W, top + 1, Theme.BORDER);
        g.fill(left, top + H - 1, left + W, top + H, Theme.BORDER);
        g.fill(left, top, left + 1, top + H, Theme.BORDER);
        g.fill(left + W - 1, top, left + W, top + H, Theme.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawString(font, title, left + 8, top + 6, Theme.TEXT, false);
        SteeringWheelBlockEntity wheel = minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof SteeringWheelBlockEntity w ? w : null;
        for (SteeringControl control : SteeringWheelBlockEntity.CONTROLS) {
            int i = control.ordinal();
            int x = colX(i), y = rowY(i);
            g.fill(x, y - 2, x + COL_W, y + 16, Theme.ROW);
            g.drawString(font, control.getDisplayName(), x + 4, y + 4, Theme.TEXT, false);
            int bx = x + BIND_OFF;
            g.fill(bx, y - 1, bx + BIND_W, y + 15, capturing == i ? Theme.ACCENT : Theme.NEUTRAL);
            String label = capturing == i ? "press key" : keyName(wheel != null ? wheel.getKeyCode(i) : -1);
            g.drawString(font, label, bx + 3, y + 4, Theme.TEXT, false);
        }
    }

    private int bindAt(double mx, double my) {
        for (SteeringControl control : SteeringWheelBlockEntity.CONTROLS) {
            int i = control.ordinal();
            int bx = colX(i) + BIND_OFF, y = rowY(i);
            if (mx >= bx && mx < bx + BIND_W && my >= y - 1 && my < y + 15) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int hit = bindAt(mx, my);
        if (hit >= 0) {
            if (button == 1) {
                send(hit, -1);
                capturing = -1;
            } else {
                capturing = capturing == hit ? -1 : hit;
                if (capturing >= 0) {
                    GamepadInput.beginCapture(); // baseline so held stick/trigger isn't grabbed instantly
                    MouseInput.beginCapture();
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (capturing >= 0) {
            int code = GamepadInput.pollCapture();
            if (code < 0) {
                code = MouseInput.pollCapture();
            }
            if (code >= 0) {
                send(capturing, code);
                capturing = -1;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturing = -1;
            } else {
                send(capturing, keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : keyCode);
                capturing = -1;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(int control, int keyCode) {
        PacketDistributor.sendToServer(new SetSteeringKeyPacket(pos, control, keyCode));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static String keyName(int keyCode) {
        if (keyCode < 0) {
            return "(none)";
        }
        if (GamepadCodes.isGamepadCode(keyCode)) {
            return GamepadCodes.name(keyCode);
        }
        if (MouseCodes.isMouseCode(keyCode)) {
            return MouseCodes.name(keyCode);
        }
        String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
        if (glfwName != null && !glfwName.isEmpty()) {
            return glfwName.toUpperCase();
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            default -> "K:" + keyCode;
        };
    }
}
