package com.createmotorsport.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineAudioProfileTest {
    @Test void unsupportedProfilesKeepTheirOriginalSounds() {
        for (int band = 0; band < 4; band++) {
            assertNull(EngineAudioProfile.optional("motorcycle_i4", band));
            assertNull(EngineAudioProfile.optional("f1_v8_2011", band));
            assertNull(EngineAudioProfile.optional("custom_engine", band));
            assertTrue(EngineAudioProfile.fallback("motorcycle_i4", band).startsWith("createmotorsport:motorcycle_"));
            assertTrue(EngineAudioProfile.fallback("f1_v8_2011", band).startsWith("createmotorsport:f1_"));
            assertTrue(EngineAudioProfile.fallback("custom_engine", band).startsWith("createmotorsport:engine_"));
        }
    }

    @Test void supportedProfilesHaveOptionalAndBundledBands() {
        for (int band = 0; band < 4; band++) {
            assertTrue(EngineAudioProfile.optional("passenger_i4_turbo", band).startsWith("snassets_engines:engines/car_scout_i4/"));
            assertTrue(EngineAudioProfile.optional("truck_diesel", band).startsWith("snassets_engines:engines/truck_detroit/"));
            assertTrue(EngineAudioProfile.fallback("truck_diesel", band).startsWith("createmotorsport:diesel_"));
        }
    }
}
