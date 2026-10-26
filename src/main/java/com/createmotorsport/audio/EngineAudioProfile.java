package com.createmotorsport.audio;

public final class EngineAudioProfile {
    private static final String[] BANDS = {"idle", "low", "mid", "fast"};
    private EngineAudioProfile() { }

    public static String fallback(String profile, int band) {
        String prefix = profile.contains("motorcycle") ? "motorcycle" :
                profile.contains("truck") ? "diesel" : profile.contains("f1") ? "f1" : "engine";
        return "createmotorsport:" + prefix + "_" + BANDS[band];
    }

    public static String optional(String profile, int band) {
        if (profile.contains("truck")) {
            String[] names = {"running", "low", "mid", "high"};
            return "snassets_engines:engines/truck_detroit/enginedetroitdiesel_" + names[band];
        }
        if (profile.contains("passenger")) {
            String[] names = {"idle", "low", "med", "high"};
            return "snassets_engines:engines/car_scout_i4/engineamci4_" + names[band];
        }
        return null;
    }
}
