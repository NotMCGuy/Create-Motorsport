package com.createmotorsport.physics.spec;

public sealed interface ModelParams {

    TireSpec.Model model();

    record TMeasy(TireSpec.Axis longitudinal, TireSpec.Axis lateral, double slipAtPeakLatLoadRise,
                  double relaxLengthLat, double bristleDamping, double standstillLeak,
                  double standstillSpeed) implements ModelParams {

        public static final double DEFAULT_RELAX_LENGTH_LAT = 1.27;

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.TMEASY;
        }
    }

    record Pacejka(double corneringStiffness, double rFactor, double eFactor) implements ModelParams {

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.PACEJKA;
        }
    }

    record Fiala(double slipStiffness, double corneringStiffness, double muMax, double muMin,
                 double relaxLength) implements ModelParams {

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.FIALA;
        }
    }

    record Arcade() implements ModelParams {

        @Override
        public TireSpec.Model model() {
            return TireSpec.Model.ARCADE;
        }
    }
}
