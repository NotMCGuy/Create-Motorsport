package com.createmotorsport.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class F1SoundMixTest {
    private static final double[] REFERENCES = {4000, 8000, 12000, 17000};

    @Test void crossfadesKeepEnergyWithoutOverpitchingTheRecordings() {
        for (double rpm = 4000; rpm <= 18000; rpm += 25) {
            double energy = 0;
            int active = 0;
            for (int band = 0; band < 4; band++) {
                float weight = F1SoundMix.weight(rpm, band);
                energy += weight;
                if (weight > 1e-5) {
                    active++;
                    assertTrue(F1SoundMix.pitch(rpm, band) >= .5);
                    assertTrue(F1SoundMix.pitch(rpm, band) <= 1.5);
                    assertTrue(F1SoundMix.pitch(rpm, band) >= F1SoundMix.pitch(rpm - 25, band));
                    assertEquals(rpm/30, REFERENCES[band]/30*F1SoundMix.pitch(rpm,band), .002);
                }
            }
            assertEquals(1, energy, 1e-6);
            assertTrue(active <= 2);
        }
        assertEquals(1, F1SoundMix.weight(4000, 0));
        assertEquals(0, F1SoundMix.weight(4000, 1));
        for (int band = 0; band < 4; band++) assertEquals(1, F1SoundMix.pitch(REFERENCES[band], band));
    }

    @Test void shiftDropsPitchInProportionToTheNewGearRatio() {
        double before = F1SoundMix.pitch(17500, 3);
        double after = F1SoundMix.pitch(14000, 3);
        assertEquals(.8, after / before, 1e-6);
        assertEquals(1.5, F1SoundMix.pitch(12000, 1) / F1SoundMix.pitch(8000, 1), 1e-6);
        assertTrue(F1SoundMix.combustionGain(true, false) < .5);
        assertEquals(1, F1SoundMix.combustionGain(false, false));
        assertEquals(0, F1SoundMix.weight(Double.NaN, 0));
    }
}
