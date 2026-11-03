package com.createmotorsport.client.hud;

import com.createmotorsport.client.input.SteeringInputHandler;
import com.createmotorsport.Config;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.client.widget.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MotorsportHud {
    private static boolean enabled;

    private MotorsportHud() {
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        BlockPos pos = SteeringInputHandler.getActivePos();
        if (pos == null) { //so only works when driving
            return;
        }
        if (!(mc.level.getBlockEntity(pos) instanceof SteeringWheelBlockEntity wheel) || wheel.isRemoved()) {
            return;
        }
        render(event.getGuiGraphics(), mc.font, wheel);
    }

    private static void render(GuiGraphics g, Font font, SteeringWheelBlockEntity w) {
        List<String> lines = new ArrayList<>();
        lines.add("§eSPD §f" + w.dashboard().speedKmh() + " §7km/h");
        lines.add("§eGEAR §f" + gearLabel(w.dashboard().gearCode()) + "   §eRPM §f" + w.dashboard().rpm());
        lines.add("§eTHR §f" + w.dashboard().throttlePct() + "%   §eBRK " + (w.dashboard().braking() ? "§cON" : "§7off"));
        String aids = "§eMODE §f" + w.dashboard().powerMode()
                + (w.isTractionControlOn() ? "  §aTC" : "")
                + (w.dashboard().boosting() ? "  §bOT" : "")
                + (w.isDiffModeOn() ? "  §dDIFF" : "")
                + (w.isSteerAssistOff() ? "  §6ASSIST OFF" : "")
                + (w.getCruiseTargetKmh() > 0 ? "  §bCRZ " + w.getCruiseTargetKmh() : "");
        lines.add(aids);

        int[] temps = w.dashboard().tireTempsC();
        int slip = w.dashboard().slipMask();
        String gripLine = "§eGRIP §f" + String.format(Locale.ROOT, "%.2f", w.dashboard().effMuX100() / 100.0);
        if (slip != 0) {
            gripLine += "   §l§cSLIPPING!";
        }
        lines.add(gripLine);
        if (temps.length > 0) {
            StringBuilder tsb = new StringBuilder("§eTIRE ");
            for (int i = 0; i < temps.length; i++) {
                tsb.append("§7").append(tireLabel(i, temps.length))
                        .append(tempColor(temps[i])).append(temps[i]).append("° ");
            }
            lines.add(tsb.toString().trim());
        }

        int pad = 3;
        int lineH = font.lineHeight + 1;
        int contentW = 0;
        for (String s : lines) {
            contentW = Math.max(contentW, font.width(s));
        }
        int panelW = contentW + pad * 2;
        int panelH = lines.size() * lineH + pad * 2;

        float scale = (float) Config.HUD_SCALE.getAsDouble();
        int x = Config.HUD_X.get();
        int y = Config.HUD_Y.get();

        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0);
        pose.scale(scale, scale, 1.0F);
        g.fill(0, 0, panelW, panelH, Theme.HUD_BACKDROP);
        int ty = pad;
        for (String s : lines) {
            g.drawString(font, s, pad, ty, Theme.TEXT);
            ty += lineH;
        }
        pose.popPose();
    }

    private static String gearLabel(int code) {
        return code == 0 ? "R" : code == 1 ? "N" : String.valueOf(code - 1);
    }

    private static String tireLabel(int i, int count) {
        if (count == 4) {
            return switch (i) {
                case 0 -> "FL";
                case 1 -> "FR";
                case 2 -> "RL";
                default -> "RR";
            };
        }
        return "T" + i;
    }

    private static String tempColor(int c) {
        if (c >= 110) {
            return "§c";
        }
        if (c >= 70) {
            return "§a";
        }
        if (c >= 40) {
            return "§e";
        }
        return "§b";
    }
}
