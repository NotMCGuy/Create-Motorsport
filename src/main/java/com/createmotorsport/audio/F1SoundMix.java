package com.createmotorsport.audio;

public final class F1SoundMix {
    private static final double[] REFERENCES = {4000, 8000, 12000, 17000};

    private F1SoundMix() { }

    public static float pitch(double rpm, int band) {
        if (!Double.isFinite(rpm) || band < 0 || band >= REFERENCES.length) return 1;
        return (float) Math.clamp(rpm / REFERENCES[band], .5, 1.5);
    }

    public static float weight(double rpm, int band) {
        if (!Double.isFinite(rpm) || band < 0 || band >= REFERENCES.length) return 0;
        for (int low = 0; low < 3; low++) {
            double centre = Math.sqrt(REFERENCES[low] * REFERENCES[low + 1]);
            if (rpm < centre * .95) return band == low ? 1 : 0;
            if (rpm <= centre * 1.05) {
                double blend = (rpm - centre * .95) / (centre * .10);
                blend = blend * blend * (3 - 2 * blend);
                return band == low ? (float)(1 - blend) : band == low + 1 ? (float)blend : 0;
            }
        }
        return band == 3 ? 1 : 0;
    }

    public static float combustionGain(boolean shifting, boolean limiter) {
        return shifting ? .45f : limiter ? .7f : 1;
    }
}
