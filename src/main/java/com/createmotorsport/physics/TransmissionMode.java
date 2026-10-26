package com.createmotorsport.physics;

public enum TransmissionMode {
    AUTOMATIC("automatic"), SEMI_AUTOMATIC("semi_automatic"), MANUAL("manual");

    private final String id;
    TransmissionMode(String id) { this.id = id; }
    public String id() { return id; }
    public String translationKey() { return "transmission.createmotorsport." + id; }
    public TransmissionMode next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean assistedClutch() { return this != MANUAL; }
    public boolean automaticShifts() { return this == AUTOMATIC; }
    public static TransmissionMode fromId(String id) {
        for (var mode : values()) if (mode.id.equals(id)) return mode;
        return null;
    }
}
