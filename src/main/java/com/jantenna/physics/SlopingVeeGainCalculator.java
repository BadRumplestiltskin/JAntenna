package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/** KOP 7: Terminated Sloping Vee antenna gain calculator. */
public final class SlopingVeeGainCalculator implements AntennaGainCalculator {

    @Override
    public int getAntennaType() {
        return 7;
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
        double hwave = MathConstants.PI2 * x;
        double hqwave = 2.0 * hwave * q;
        double sb = FastMath.sin(params.toaz());
        double cb = FastMath.cos(params.toaz());
        double rhi = FastMath.toRadians(params.ynd());
        double sr = FastMath.sin(rhi);
        double cr = FastMath.cos(rhi);

        double ht = Math.abs(params.tex()[0]) / wave;
        double deltaArg = (ht - x) / el1;
        deltaArg = Math.max(-1.0, Math.min(1.0, deltaArg));
        double deltap = FastMath.asin(deltaArg);
        double cdelp = FastMath.cos(deltap);

        double sinRhiArg = sr / cdelp;
        sinRhiArg = Math.max(-1.0, Math.min(1.0, sinRhiArg));
        rhi = FastMath.asin(sinRhiArg);
        cr = FastMath.cos(rhi);
        sr = FastMath.sin(rhi);

        double cd = cb * cr + sb * sr;
        double cs = cb * cr - sb * sr;
        double ss = sb * cr + cb * sr;
        double sd = sb * cr - cb * sr;

        double scp = t * cdelp;
        double ccp = q * cdelp;
        double sdelp = FastMath.sin(deltap);
        double ssp = t * sdelp;
        double csp = q * sdelp;

        double u1 = 1.0 - (csp + scp * cd);
        double u2 = 1.0 - (csp + scp * cs);
        double u3 = 1.0 - (-csp + scp * cd);
        double u4 = 1.0 - (-csp + scp * cs);

        double cp5 = ssp + ccp * cd;
        double cp6 = ssp + ccp * cs;
        double cp7 = -ssp + ccp * cd;
        double cp8 = -ssp + ccp * cs;

        double w1 = FastMath.cos(psih - hqwave);
        double w2 = FastMath.sin(psih - hqwave);
        double w3 = FastMath.cos(psiv - hqwave);
        double w4 = FastMath.sin(psiv - hqwave);

        double fu1 = fac2 * u1;
        double v1 = FastMath.sin(fu1);
        double z1 = FastMath.cos(fu1);
        double fu2 = fac2 * u2;
        double v2 = FastMath.sin(fu2);
        double z2 = FastMath.cos(fu2);
        double fu3 = fac2 * u3;
        double v3 = FastMath.sin(fu3);
        double z3 = FastMath.cos(fu3);
        double fu4 = fac2 * u4;
        double v4 = FastMath.sin(fu4);
        double z4 = FastMath.cos(fu4);

        z1 = z1 - 1.0;
        z2 = z2 - 1.0;
        z3 = z3 - 1.0;
        z4 = z4 - 1.0;

        double y1 = u1 * ss;
        double y3 = u3 * ss;
        double y2 = u2 * sd;
        double y4 = u4 * sd;

        double u12 = u1 * u2;
        double u34 = u3 * u4;

        double a1, b1, c1, d1;

        if (u12 == 0.0) {
            a1 = 0.0;
            b1 = 0.0;
            c1 = 0.0;
            d1 = 0.0;
            if (u34 == 0.0) {
                return AntennaConstants.GAIN_FLOOR_DB;
            }
        } else {
            a1 = (u2 * cp7 * z1 - u1 * z2 * cp8) / u12;
            b1 = (u1 * v2 * cp8 - u2 * v1 * cp7) / u12;
            c1 = (y1 * z2 - y2 * z1) / u12;
            d1 = (y2 * v1 - y1 * v2) / u12;
        }

        if (u34 != 0.0) {
            double a2 = u3 * z4 * cp6 - u4 * z3 * cp5;
            double b2 = u3 * v4 * cp6 - u4 * v3 * cp5;
            a1 += cv * (w3 * a2 + w4 * b2) / u34;
            b1 += cv * (-b2 * w3 + w4 * a2) / u34;
            double aa2 = y3 * z4 - y4 * z3;
            double bb2 = y4 * v3 - y3 * v4;
            c1 += ch * (w1 * aa2 - w2 * bb2) / u34;
            d1 += ch * (w1 * bb2 + w2 * aa2) / u34;
        }

        double rain = 0.05 * (a1 * a1 + b1 * b1 + cdelp * cdelp * (c1 * c1 + d1 * d1));

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) - 1.7;
    }
}
