package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import org.apache.commons.math3.complex.Complex;

/** KOP 12: Constant Gain antenna gain calculator. */
public final class ConstantGainCalculator implements AntennaGainCalculator {

    @Override
    public int getAntennaType() {
        return 12;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        // YNH is the gain value in dB directly — no log conversion (FORTRAN label 630 path).
        return params.ynh();
    }
}
