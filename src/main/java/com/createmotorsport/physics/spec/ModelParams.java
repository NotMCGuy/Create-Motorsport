package com.createmotorsport.physics.spec;

public sealed interface ModelParams {

    TireSpec.Model model();

    /**
     * MODEL #4 (but the default) - TMEASY
     *
     * @param slipAtPeakLatLoadRise -> sym_p2n / sym_pn, like Chrono's ChTMeasyTire, read at TireSpec.latPeakAtLoad
     * @param relaxLengthLat -> Rill 3.4.2, lateral relaxation length at zero slip, in blocks
     * @param bristleDamping -> fraction of critical
     * @param standstillLeak -> m/s floor on the lateral bristle's slip speed
     * @param standstillSpeed -> m/s below which stand still bristle takes over from the curve
     */
    record TMeasy(TireSpec.Axis longitudinal, TireSpec.Axis lateral, double slipAtPeakLatLoadRise,
                  double relaxLengthLat, double bristleDamping, double standstillLeak,
                  double standstillSpeed) implements ModelParams {

        public static final double DEFAULT_RELAX_LENGTH_LAT = 1.27;

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.TMEASY;
        }
    }

    /** MODEL #2 - PACEJKA */
    record Pacejka(double corneringStiffness, double rFactor, double eFactor) implements ModelParams {

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.PACEJKA;
        }
    }

    /** MODEL #3 - FIALA brush model
     * @param relaxLength isn't the same as TMeasy's relaxLengthLat */
    record Fiala(double slipStiffness, double corneringStiffness, double muMax, double muMin,
                 double relaxLength) implements ModelParams {

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.FIALA;
        }
    }

    /** MODEL #1 - ARCADE */
    record Arcade() implements ModelParams {

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.ARCADE;
        }
    }
}
