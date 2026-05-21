package com.jantenna.physics;

import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/** KOP 2: Vertical Monopole antenna gain calculator. */
public final class MonopoleGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public MonopoleGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 2;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t,
                               double wave, Complex qpar, Complex qper, Complex dif) {
        double el1 = params.ynl() / wave;
        double fac2 = MathConstants.PI2 * el1;
        double fac4 = 2.0 * fac2;
        double hq = fac2 * q;

        double a = FastMath.cos(hq) - FastMath.cos(fac2);
        double as = FastMath.sin(hq) - q * FastMath.sin(fac2);

        // Mirrors MUFESGAN.FOR RZERO calculation
        double flog = FastMath.log(fac2);
        double c2kel = 2.0 * FastMath.pow(FastMath.cos(fac2), 2) - 1.0;
        double s2kel = 2.0 * FastMath.cos(fac2) * FastMath.sin(fac2);

        Complex zt1 = csz1(4.0 * fac2);
        double rzero = 0.5 * (c2kel * (zt1.getReal() - flog - 1.3862943612 - MathConstants.EULER_MASCHERONI) - s2kel * zt1.getImaginary());

        Complex zt2 = csz1(fac4);
        rzero += 30.0 * (rzero + (1.0 + c2kel) * (-zt2.getReal() + flog + 0.6931471806 + MathConstants.EULER_MASCHERONI) + s2kel * zt2.getImaginary());

        if (el1 < 0.2) {
            rzero = 400.0 * el1 * el1 * 1.12875;
        }
        double rin = rzero;

        double w3 = FastMath.cos(qper.getArgument());
        double w4 = FastMath.sin(qper.getArgument());
        double cv = qper.abs();

        double rainTerm1 = FastMath.pow(a * (1.0 + cv * w3) + as * cv * w4, 2);
        double rainTerm2 = FastMath.pow(a * cv * w4 + as * (1.0 - cv * w3), 2);

        double rain = 30.0 * (rainTerm1 + rainTerm2) / (rin * t * t);

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) + params.ynh();
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
