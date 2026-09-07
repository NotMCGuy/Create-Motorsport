package com.createmotorsport.client.widget;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.DoubleConsumer;

// A text box that holds one number. Saves on enter or loss of focus
//
// forward keyPressed before super, because AbstractContainerScreen takes
// the inventory key and closes the menu

@OnlyIn(Dist.CLIENT)
public class NumberField extends EditBox {

    private final double min;
    private final double max;
    private final DoubleConsumer onCommit;
    private double committed;

    public NumberField(Font font, int x, int y, int width, int height, Component label,
                       double min, double max, DoubleConsumer onCommit) {
        super(font, x, y, width, height, label);
        this.min = min;
        this.max = max;
        this.onCommit = onCommit;
        setMaxLength(12);
        setFilter(NumberField::typeable);
    }

    private static boolean typeable(String s) {
        if (s.isEmpty()) {
            return true;
        }
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' && i == 0) {
                continue;
            }
            if (c == '.') {
                if (++dots > 1) {
                    return false;
                }
                continue;
            }
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    // Set from the server's value without firing a commit back at it
    public void showValue(double value) {
        this.committed = value;
        setValue(trim(value));
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    public void commit() {
        double parsed;
        try {
            parsed = Double.parseDouble(getValue());
        } catch (NumberFormatException e) {
            showValue(committed);
            return;
        }
        parsed = Math.max(min, Math.min(max, parsed));
        showValue(parsed);
        onCommit.accept(parsed);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (isFocused() && (key == 257 || key == 335)) {   // codes for enter & numpad enter
            commit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void setFocused(boolean focused) {
        boolean was = isFocused();
        super.setFocused(focused);
        if (was && !focused) {
            commit();
        }
    }
}
