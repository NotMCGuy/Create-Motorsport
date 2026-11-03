package com.createmotorsport.client.widget;

public final class Theme {

    public static final int PANEL = 0xFF1A1D21;
    public static final int BORDER = 0xFF454B54;
    public static final int RULE = 0xFF2D3238;

    public static final int ROW = 0xFF25292F;
    public static final int ROW_ALT = 0xFF262A31;
    public static final int INSET = 0xFF111317;
    public static final int SLOT = 0xFF111317;
    public static final int SLOT_EDGE = 0xFF373D45;
    public static final int HEADER = 0xFF22364B;

    public static final int NEUTRAL = 0xFF333942;
    public static final int ACCENT = 0xFF315C84;
    public static final int GO = 0xFF2D6343;
    public static final int STOP = 0xFF893333;
    public static final int CAUTION = 0xFF806121;
    public static final int DISABLED = 0xFF22262C;
    public static final int EDGE = 0x26FFFFFF;
    public static final int EDGE_HOVER = 0x66FFFFFF;
    public static final int HOVER_LIFT = 24;

    public static final int TEXT = 0xFFEAEDF1;
    public static final int TEXT_DIM = 0xFF98A0AA;
    public static final int TEXT_MUTED = 0xFF636B75;

    public static final int READOUT = 0xFFACCEEB;
    public static final int GOOD = 0xFF6ECE89;
    public static final int WARN = 0xFFE8B250;
    public static final int BAD = 0xFFEF6F6F;

    public static final int DIM = 0xB0000000;
    public static final int HUD_BACKDROP = 0x90000000;

    public static final int FREQ_A = 0x60DE4646;
    public static final int FREQ_B = 0x604F82E9;

    public static final int DASH_BG = 0xE0222222;
    public static final int DASH_SPEED = 0xFF47FFFF;
    public static final int DASH_GEAR = 0xFFCDE9FF;
    public static final int DASH_RPM = 0xFFFF8A46;
    public static final int DASH_BRAKE = 0xFFFC5252;
    public static final int DASH_OFF = 0xFF586958;
    public static final int DASH_MODE = 0xFFFFD54D;
    public static final int DASH_BOOST = 0xFF31FDAE;
    public static final int DASH_TC = 0xFF65FE65;

    private Theme() {
    }

    // highlight on hover
    public static int hover(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + HOVER_LIFT);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + HOVER_LIFT);
        int b = Math.min(255, (argb & 0xFF) + HOVER_LIFT);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
