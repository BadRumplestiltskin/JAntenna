package com.jantenna.physics;

import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/** KOP 11: Sloping Long-Wire antenna gain calculator. */
public final class SlopingLongWireGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public SlopingLongWireGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 11;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        double cv = qper.abs();
        double psiv = qper.getArgument();
        double ch = qpar.abs();
        double psih = qpar.getArgument();
        double el1 = Math.abs(params.ynl()) / wave;
        double fac2 = MathConstants.PI2 * el1;
        double fac4 = 2.0 * fac2;
        double hwave = MathConstants.PI2 * (Math.abs(params.ynh()) / wave);
        double hqwave = 2.0 * hwave * q;
        double sb = FastMath.sin(params.toaz());
        double cb = FastMath.cos(params.toaz());
        double rhi = FastMath.toRadians(params.ynd());
        double sr = FastMath.sin(rhi);
        double cr = FastMath.cos(rhi);

        double cfac2 = FastMath.cos(fac2);
        double sfac2 = FastMath.sin(fac2);

        double w1 = FastMath.cos(psih - hqwave);
        double w2 = FastMath.sin(psih - hqwave);
        double w3 = FastMath.cos(psiv - hqwave);
        double w4 = FastMath.sin(psiv - hqwave);

        double crb = cr * cb * q;
        double srt = sr * t;
        double cbs = cr * sb;
        double srq = q * sr;

        double cphi = srq + t * cr * cb;
        double sphi2 = 1.0 - cphi * cphi;
        double cphip = -srq + t * cr * cb;
        double sphip2 = 1.0 - cphip * cphip;

        if (sphi2 == 0.0 && sphip2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double ethet1 = 0.0;
        double ethet2 = 0.0;
        double ephi1 = 0.0;
        double ephi2 = 0.0;

        if (sphi2 != 0.0) {
            double cig = (FastMath.cos(fac2 * cphi) - cfac2) / sphi2;
            // sig_var is a pattern function, distinct from ysig (conductivity)
            double sig_var = (FastMath.sin(fac2 * cphi) - cphi * sfac2) / sphi2;
            ethet1 = (crb - srt) * cig;
            ethet2 = (crb - srt) * sig_var;
            ephi1 = -cbs * cig;
            ephi2 = -cbs * sig_var;
        }

        if (sphip2 != 0.0) {
            double cigp = (FastMath.cos(fac2 * cphip) - cfac2) / sphip2;
            double sigp = (FastMath.sin(fac2 * cphip) - cphip * sfac2) / sphip2;
            ethet1 -= (crb + srt) * cv * (w3 * cigp - w4 * sigp);
            ethet2 -= (crb + srt) * cv * (w4 * cigp + w3 * sigp);
            ephi1 -= cbs * ch * (w1 * cigp - w2 * sigp);
            ephi2 -= cbs * ch * (w2 * cigp + w1 * sigp);
        }

        Complex azh2fac4 = csz1(2.0 * fac4);
        double w5 = azh2fac4.getReal();
        double w6 = azh2fac4.getImaginary();

        Complex azhFac4 = csz1(fac4);
        double w33 = azhFac4.getReal();
        double w4var = azhFac4.getImaginary();

        double flog = FastMath.log(fac2) + AntennaConstants.EULER_MASCHERONI;
        // 0.6931471806 = ln(2)
        double rin = 30.0 * (0.5 * (flog - w5) + 0.6931471806
                + cfac2 * (cfac2 * (flog - 2.0 * w33 + w5) - sfac2 * (w6 - 2.0 * w4var)));

        double rain = 30.0 * (ethet1 * ethet1 + ethet2 * ethet2 + ephi1 * ephi1 + ephi2 * ephi2) / rin;

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001));
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
