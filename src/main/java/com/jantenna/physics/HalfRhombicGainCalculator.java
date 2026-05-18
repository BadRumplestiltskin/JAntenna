package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/** KOP 15: Half Rhombic antenna gain calculator. */
public final class HalfRhombicGainCalculator implements AntennaGainCalculator {

    @Override
    public int getAntennaType() {
        return 15;
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
        double fac2 = MathConstants.PI2 * el1;
        double fac4 = 2.0 * fac2;
        double hwave = MathConstants.PI2 * x;
        double hqwave = 2.0 * hwave * q;
        double sb = FastMath.sin(params.toaz());
        double cb = FastMath.cos(params.toaz());
        double rhi = FastMath.toRadians(params.ynd());
        double sr = FastMath.sin(rhi);
        double cr = FastMath.cos(rhi);

        double w1 = FastMath.cos(psih);
        double w2 = FastMath.sin(psih);
        double w3 = FastMath.cos(psiv);
        double w4 = FastMath.sin(psiv);

        double tt = q * sr;
        double ts = 1.0 - t * cr * cb;

        double tt2 = ts + tt;
        if (tt2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }
        double ts2 = fac2 * tt2;
        double sts4 = FastMath.sin(ts2) / tt2;
        double cts4 = (1.0 - FastMath.cos(ts2)) / tt2;

        double tt1 = ts - tt;
        if (tt1 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }
        double ts1 = fac2 * tt1;
        double sts1 = FastMath.sin(ts1);
        double cts1 = FastMath.cos(ts1);

        double r1 = (1.0 - cts1) / tt1;
        double fi1 = sts1 / tt1;

        double cosFac4SrQ = FastMath.cos(fac4 * sr * q);
        double sinFac4SrQ = FastMath.sin(fac4 * sr * q);

        double r4 = (1.0 - cts1) * cosFac4SrQ + sts1 * sinFac4SrQ;
        double fi4 = sts1 * cosFac4SrQ - sinFac4SrQ * (1.0 - cts1);

        double r2 = cts4 * cts1 + sts4 * sts1;
        double fi2 = cts1 * sts4 - cts4 * sts1;

        double f4c = (fi4 * cts1 - r4 * sts1) / tt1;
        double r4c = (r4 * cts1 + fi4 * sts1) / tt1;

        double rb = r1 + r2 - ((cts4 + r4c) * w3 - (sts4 + f4c) * w4) * cv;
        double bi = fi1 + fi2 - ((cts4 + r4c) * w4 + (sts4 + f4c) * w3) * cv;
        double rc = -r1 + r2 + ((-cts4 + r4c) * w3 - (-sts4 + f4c) * w4) * cv;
        double cc = -fi1 + fi2 + ((-cts4 + r4c) * w4 + (-sts4 + f4c) * w3) * cv;
        double ra = r1 + r2 + ((cts4 + r4c) * w1 - (sts4 + f4c) * w2) * ch;
        double ai = fi1 + fi2 + ((cts4 + r4c) * w2 + (sts4 + f4c) * w1) * ch;

        double crCbQ = cr * cb * q;
        double srT = sr * t;
        double crSb = cr * sb;

        double em1 = (crCbQ * rb + srT * rc) * (crCbQ * rb + srT * rc)
                   + (crCbQ * bi + srT * cc) * (crCbQ * bi + srT * cc);
        double enn1 = (crSb * ra) * (crSb * ra) + (crSb * ai) * (crSb * ai);

        double rain = 0.1 * (enn1 + em1);

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) - 1.7;
    }
}
