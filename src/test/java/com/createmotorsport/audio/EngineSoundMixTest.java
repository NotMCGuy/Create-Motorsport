package com.createmotorsport.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineSoundMixTest {
    @Test void bandsCrossfadeWithoutVolumeHolesOrDoubling() {
        for(double rpm=0;rpm<=1;rpm+=.005) {
            double sum=0;int active=0;
            for(int band=0;band<4;band++){float weight=EngineSoundMix.weight(rpm,band);sum+=weight*weight;if(weight>0)active++;}
            assertEquals(1,sum,1e-6);assertTrue(active<=2);
        }
    }
    @Test void powerAndCoastPreserveEnergyThroughThrottleTransitions() {
        for (int i=0;i<=100;i++) {
            float power=EngineSoundMix.loadBlend(i/100f,false);
            float coast=EngineSoundMix.loadBlend(i/100f,true);
            assertEquals(1,power*power+coast*coast,1e-6);
        }
        assertEquals(0,EngineSoundMix.loadBlend(0,false));
        assertEquals(0,EngineSoundMix.loadBlend(1,true));
    }
    @Test void pitchIsBoundedAndInvalidRpmIsSilent() {
        for(int band=0;band<4;band++)for(double rpm:new double[]{0,.3,.7,1}) {
            assertTrue(EngineSoundMix.pitch(rpm,band,false)>=.8&&EngineSoundMix.pitch(rpm,band,false)<=1.2);
            assertTrue(EngineSoundMix.pitch(rpm,band,true)<EngineSoundMix.pitch(rpm,band,false));
        }
        assertEquals(0,EngineSoundMix.weight(Double.NaN,0));
    }
}
