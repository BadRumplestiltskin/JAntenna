package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/** KOP 1: Terminated Rhombic antenna gain calculator. */
public final class RhombicGainCalculator implements AntennaGainCalculator {

    @Override
    public int getAntennaType() {
        return 1;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        double cv = qper.abs();
        double psiv = qper.getArgument();
        double ch = qpar.abs();
        double psih = qpar.getArgument();
        double el1 = Math.abs(params.ynl()) / wave;
        double x = Math.abs(params.ynh()) / wave;
        double fac = MathConstants.PI * el1;
        double hwave = MathConstants.PI2 * x;
        double hqwave = 2.0 * hwave * q;
        double sb = FastMath.sin(params.toaz());
        double cb = FastMath.cos(params.toaz());
        double rhi = FastMath.toRadians(params.ynd());
        double sr = FastMath.sin(rhi);
        double cr = FastMath.cos(rhi);

        double tsc = 1.0 - t * sr * cb;
        double tcs = t * cr * sb;
        double u1 = tsc - tcs;
        double u2 = tsc + tcs;

        if (u1 == 0.0 || u2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double w1 = FastMath.cos(psih - hqwave);
        double w3 = FastMath.cos(psiv - hqwave);

        double sinFacU1 = FastMath.sin(fac * u1);
        double sinFacU2 = FastMath.sin(fac * u2);
        double crSinRatio = cr * sinFacU1 * sinFacU2 / (u1 * u2);

        double term1 = (cb - sr * t) * (cb - sr * t) * (ch * ch + 1.0 + 2.0 * ch * w1);
        double term2 = sb * sb * (cv * cv + 1.0 - 2.0 * cv * w3) * q * q;

        double rain = 3.20 * crSinRatio * crSinRatio * (term1 + term2);

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) - 1.7;
    }
}
