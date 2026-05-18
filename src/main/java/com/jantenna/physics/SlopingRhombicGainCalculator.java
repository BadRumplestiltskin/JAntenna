package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/** KOP 9: Terminated Sloping Rhombic antenna gain calculator. */
public final class SlopingRhombicGainCalculator implements AntennaGainCalculator {

    @Override
    public int getAntennaType() {
        return 9;
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
        double deltaArg = (ht - x) / (2.0 * el1);
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

        if (u1 == 0.0 || u2 == 0.0 || u3 == 0.0 || u4 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double a1 = 1.0 + z1 * z2 - v1 * v2 - z1 - z2;
        double b1 = -v1 * z2 - z1 * v2 + v1 + v2;
        double a2 = 1.0 + z3 * z4 - v3 * v4 - z3 - z4;
        double b2 = -v3 * z4 - z3 * v4 + v3 + v4;

        double cm = cp8 / u2 - cp7 / u1;
        double cn = (cp5 / u3 - cp6 / u4) * cv;
        double cmp = sd / u1 - ss / u2;
        double cnp = (sd / u3 - ss / u4) * ch;

        double am = cm * a1 + cn * (a2 * w3 - b2 * w4);
        double an = cm * b1 + cn * (a2 * w4 + b2 * w3);
        double pam = cmp * a1 + cnp * (a2 * w1 - b2 * w2);
        double pan = cmp * b1 + cnp * (a2 * w2 + b2 * w1);

        double rain = 0.05 * (am * am + an * an + cdelp * cdelp * (pam * pam + pan * pan));

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) - 1.7;
    }
}
