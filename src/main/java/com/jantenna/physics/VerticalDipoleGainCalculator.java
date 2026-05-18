package com.jantenna.physics;

import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/** KOP 5: Vertical Dipole antenna gain calculator. */
public final class VerticalDipoleGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public VerticalDipoleGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 5;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        double cv = qper.abs();
        double psiv = qper.getArgument();
        double el1 = Math.abs(params.ynl()) / wave;
        double x = Math.abs(params.ynh()) / wave;
        double fac = MathConstants.PI * el1;
        double hwave = MathConstants.PI2 * x;
        double hqwave = 2.0 * hwave * q;

        double tip = 0.5 * el1;
        if (tip > x) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double cfac = FastMath.cos(fac);
        double sphi2 = 1.0 - q * q;

        if (sphi2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double gi = (FastMath.cos(fac * q) - cfac) / sphi2;
        double w3 = FastMath.cos(psiv - hqwave);
        double eteta1 = -t * gi * (1.0 + cv * w3);
        double w4 = FastMath.sin(psiv - hqwave);
        double eteta2 = -t * gi * cv * w4;

        double hac2 = 2.0 * hwave;
        double hac4 = 2.0 * hac2;

        Complex azh2 = csz1(hac2);
        double w33 = azh2.getReal();
        double w4var = -azh2.getImaginary();

        Complex azh4 = csz1(hac4);
        double w5 = azh4.getReal();
        double w6 = -azh4.getImaginary();

        double gama = AntennaConstants.EULER_MASCHERONI;
        double rin = 60.0 * ((1.0 + FastMath.cos(hac2)) * (gama + FastMath.log(hac2) - w33)
                - 0.5 * FastMath.cos(hac2) * (gama + FastMath.log(hac4) - w5)
                + FastMath.sin(hac2) * (0.5 * w6 - w4var));

        // RINFR = 100.34 is the reference impedance for a vertical dipole at H/lambda=0.4
        final double rinfr = 100.34;
        if (el1 < 0.4) {
            rin = 800.0 * el1 * el1 * rinfr / 128.0;
        }

        // XINTR = (4-1)/(5-0.0001) interpolates conductivity correction between bounds
        final double xintr = 0.60001;
        double fmult = 4.0 - xintr * (params.ysig() - 0.0001);
        rin = 128.0 * fmult * rin / rinfr;

        double rain = 120.0 * (eteta1 * eteta1 + eteta2 * eteta2) / rin;

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) + params.ynd();
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
