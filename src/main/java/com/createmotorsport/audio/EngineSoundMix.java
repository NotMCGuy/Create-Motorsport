package com.createmotorsport.audio;

public final class EngineSoundMix {
    private EngineSoundMix() { }
    public static float weight(double rpm,int band) {
        if(!Double.isFinite(rpm)||band<0||band>3)return 0;
        double position=Math.clamp((rpm-.15)/.75,0,1)*3;
        return (float)Math.sin(Math.max(0,1-Math.abs(position-band))*Math.PI/2);
    }
    public static float loadBlend(float power, boolean coast) {
        double load = Float.isFinite(power) ? Math.clamp(power, 0, 1) : 0;
        return (float)Math.sqrt(coast ? 1-load : load);
    }
    public static float pitch(double rpm,int band,boolean diesel) {
        double centre=.15+band*.25;
        double offset=Double.isFinite(rpm)?Math.clamp(rpm-centre,-.25,.25):0;
        return (float)((diesel?.95:1)+offset*.65);
    }
}
