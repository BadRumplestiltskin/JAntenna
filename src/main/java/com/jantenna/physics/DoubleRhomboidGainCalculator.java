package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/** KOP 16: Sloping Double Rhomboid antenna gain calculator. */
public final class DoubleRhomboidGainCalculator implements AntennaGainCalculator {

    @Override
    public int getAntennaType() {
        return 16;
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

        double[] tex = params.tex();
        double el2 = Math.abs(tex[2]) / wave;
        double fak = MathConstants.PI2 * el2;
        double ht = Math.abs(tex[3]) / wave;

        double delArg = (ht - x) / (el1 + el2);
        delArg = Math.max(-1.0, Math.min(1.0, delArg));
        double del = FastMath.asin(delArg);
        double cdel = FastMath.cos(del);
        double sdel = FastMath.sin(del);

        double w1 = ch * FastMath.cos(psih - hqwave);
        double w2 = ch * FastMath.sin(psih - hqwave);
        double w3 = cv * FastMath.cos(psiv - hqwave);
        double w4 = cv * FastMath.sin(psiv - hqwave);

        double d2r = MathConstants.PI / 180.0;

        double sx1 = FastMath.sin(tex[0] * d2r);
        double bp1Arg = Math.max(-1.0, Math.min(1.0, sx1 / cdel));
        double bp1 = FastMath.asin(bp1Arg);
        double cp1 = FastMath.cos(bp1);
        double sp1 = FastMath.sin(bp1);

        double sx2 = FastMath.sin(tex[1] * d2r);
        double bp2Arg = Math.max(-1.0, Math.min(1.0, sx2 / cdel));
        double bp2 = FastMath.asin(bp2Arg);
        double cp2 = FastMath.cos(bp2);
        double sp2 = FastMath.sin(bp2);

        double rp1m = ((cb * cp1 - sb * sp1) * cr + (sb * cp1 + cb * sp1) * sr) * cdel;
        double rp2p = ((cb * cp2 - sb * sp2) * cr - (sb * cp2 + cb * sp2) * sr) * cdel;
        double rm2m = ((cb * cp2 + sb * sp2) * cr + (sb * cp2 - cb * sp2) * sr) * cdel;
        double rm1p = ((cb * cp1 + sb * sp1) * cr - (sb * cp1 - cb * sp1) * sr) * cdel;

        double argl1 = fac4 * sdel * q;
        double sl1 = FastMath.sin(argl1);
        double cl1 = FastMath.sqrt(1.0 - sl1 * sl1);
        double w1h1 = w1 * cl1 + w2 * sl1;
        double w2h1 = w2 * cl1 - w1 * sl1;
        double w3h1 = w3 * cl1 + w4 * sl1;
        double w4h1 = w4 * cl1 - w3 * sl1;

        double argl2 = 2.0 * fak * sdel * q;
        double sl2 = FastMath.sin(argl2);
        double cl2 = FastMath.sqrt(1.0 - sl2 * sl2);
        double w1h2 = w1 * cl2 + w2 * sl2;
        double w2h2 = w2 * cl2 - w1 * sl2;
        double w3h2 = w3 * cl2 + w4 * sl2;
        double w4h2 = w4 * cl2 - w3 * sl2;

        double u1 = 1.0 - (q * sdel + t * rp1m);
        double u2 = 1.0 - (q * sdel + t * rp2p);
        double u3 = 1.0 - (q * sdel + t * rm2m);
        double u4 = 1.0 - (q * sdel + t * rm1p);

        double c11 = FastMath.cos(fac2 * u1);
        double s11 = FastMath.sin(fac2 * u1);
        double c22 = FastMath.cos(fak * u2);
        double s22 = FastMath.sin(fak * u2);
        double c23 = FastMath.cos(fak * u3);
        double s23 = FastMath.sin(fak * u3);
        double c14 = FastMath.cos(fac2 * u4);
        double s14 = FastMath.sin(fac2 * u4);

        double denom1 = 1.0 + q * sdel - t * rp1m;
        double denom2 = 1.0 + q * sdel - t * rp2p;
        double denom3 = 1.0 + q * sdel - t * rm2m;
        double denom4 = 1.0 + q * sdel - t * rm1p;

        if (u1 == 0.0 || u2 == 0.0 || u3 == 0.0 || u4 == 0.0
                || denom1 == 0.0 || denom2 == 0.0 || denom3 == 0.0 || denom4 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double u1g = 1.0 / denom1;
        double u2g = 1.0 / denom2;
        double u3g = 1.0 / denom3;
        double u4g = 1.0 / denom4;

        double vr1 = (1.0 - c11) / u1;
        double vi1 = s11 / u1;
        double vr2 = (1.0 - c22) / u2;
        double vi2 = s22 / u2;
        double vr3 = (1.0 - c23) / u3;
        double vi3 = s23 / u3;
        double vr4 = (1.0 - c14) / u4;
        double vi4 = s14 / u4;

        double vr1g = (w3 * (1.0 - c11) - w4 * s11) * u1g;
        double vi1g = (w3 * s11 + w4 * (1.0 - c11)) * u1g;
        double vr2g = (w3 * (1.0 - c22) - w4 * s22) * u2g;
        double vi2g = (w3 * s22 + w4 * (1.0 - c22)) * u2g;
        double vr3g = (w3 * (1.0 - c23) - w4 * s23) * u3g;
        double vi3g = (w3 * s23 + w4 * (1.0 - c23)) * u3g;
        double vr4g = (w3 * (1.0 - c14) - w4 * s14) * u4g;
        double vi4g = (w3 * s14 + w4 * (1.0 - c14)) * u4g;

        double vr5g = (w3h2 * (1.0 - c11) - w4h2 * s11) * u1g;
        double vi5g = (w3h2 * s11 + w4h2 * (1.0 - c11)) * u1g;
        double vr6g = (w3h1 * (1.0 - c22) - w4h1 * s22) * u2g;
        double vi6g = (w3h1 * s22 + w4h1 * (1.0 - c22)) * u2g;
        double vr7g = (w3h1 * (1.0 - c23) - w4h1 * s23) * u3g;
        double vi7g = (w3h1 * s23 + w4h1 * (1.0 - c23)) * u3g;
        double vr8g = (w3h2 * (1.0 - c14) - w4h2 * s14) * u4g;
        double vi8g = (w3h2 * s14 + w4h2 * (1.0 - c14)) * u4g;

        double vr1h = (w1 * (1.0 - c11) - w2 * s11) * u1g;
        double vi1h = (w1 * s11 + w2 * (1.0 - c11)) * u1g;
        double vr2h = (w1 * (1.0 - c22) - w2 * s22) * u2g;
        double vi2h = (w1 * s22 + w2 * (1.0 - c22)) * u2g;
        double vr3h = (w1 * (1.0 - c23) - w2 * s23) * u3g;
        double vi3h = (w1 * s23 + w2 * (1.0 - c23)) * u3g;
        double vr4h = (w1 * (1.0 - c14) - w2 * s14) * u4g;
        double vi4h = (w1 * s14 + w2 * (1.0 - c14)) * u4g;

        double vr5h = (w1h2 * (1.0 - c11) - w2h2 * s11) * u1g;
        double vi5h = (w1h2 * s11 + w2h2 * (1.0 - c11)) * u1g;
        double vr6h = (w1h1 * (1.0 - c22) - w2h1 * s22) * u2g;
        double vi6h = (w1h1 * s22 + w2h1 * (1.0 - c22)) * u2g;
        double vr7h = (w1h1 * (1.0 - c23) - w2h1 * s23) * u3g;
        double vi7h = (w1h1 * s23 + w2h1 * (1.0 - c23)) * u3g;
        double vr8h = (w1h2 * (1.0 - c14) - w2h2 * s14) * u4g;
        double vi8h = (w1h2 * s14 + w2h2 * (1.0 - c14)) * u4g;

        double e1r = (vr1 - vr1g) * q * rp1m - (vr1 + vr1g) * sdel * t;
        double e1i = (vi1 - vi1g) * q * rp1m - (vi1 + vi1g) * sdel * t;
        double e2r = (vr2 - vr2g) * q * rp2p - (vr2 + vr2g) * sdel * t;
        double e2i = (vi2 - vi2g) * q * rp2p - (vi2 + vi2g) * sdel * t;
        double e3r = -(vr3 - vr3g) * q * rm2m + (vr3 + vr3g) * sdel * t;
        double e3i = -(vi3 - vi3g) * q * rm2m + (vi3 + vi3g) * sdel * t;
        double e4r = -(vr4 - vr4g) * q * rm1p + (vr4 + vr4g) * sdel * t;
        double e4i = -(vi4 - vi4g) * q * rm1p + (vi4 + vi4g) * sdel * t;

        double e5r = -c23 * ((vr1 - vr5g) * q * rp1m - (vr1 + vr5g) * sdel * t)
                - s23 * ((vi1 - vi5g) * q * rp1m - (vi1 + vi5g) * sdel * t);
        double e5i = -c23 * ((vi1 - vi5g) * q * rp1m - (vi1 + vi5g) * sdel * t)
                + s23 * ((vr1 - vr5g) * q * rp1m - (vr1 + vr5g) * sdel * t);

        double e6r = -c14 * ((vr2 - vr6g) * q * rp2p - (vr2 + vr6g) * sdel * t)
                - s14 * ((vi2 - vi6g) * q * rp2p - (vi2 + vi6g) * sdel * t);
        double e6i = -c14 * ((vi2 - vi6g) * q * rp2p - (vi2 + vi6g) * sdel * t)
                + s14 * ((vr2 - vr6g) * q * rp2p - (vr2 + vr6g) * sdel * t);

        double e7r = c11 * ((vr3 - vr7g) * q * rm2m - (vr3 + vr7g) * sdel * t)
                + s11 * ((vi3 - vi7g) * q * rm2m - (vi3 + vi7g) * sdel * t);
        double e7i = c11 * ((vi3 - vi7g) * q * rm2m - (vi3 + vi7g) * sdel * t)
                - s11 * ((vr3 - vr7g) * q * rm2m - (vr3 + vr7g) * sdel * t);

        double e8r = c22 * ((vr4 - vr8g) * q * rm1p - (vr4 + vr8g) * sdel * t)
                + s22 * ((vi4 - vi8g) * q * rm1p - (vi4 + vi8g) * sdel * t);
        double e8i = c22 * ((vi4 - vi8g) * q * rm1p - (vi4 + vi8g) * sdel * t)
                - s22 * ((vr4 - vr8g) * q * rm1p - (vr4 + vr8g) * sdel * t);

        double ethr = e1r + e2r + e3r + e4r + e5r + e6r + e7r + e8r;
        double ethi = e1i + e2i + e3i + e4i + e5i + e6i + e7i + e8i;

        double sp1m = (sb * cp1 + cb * sp1) * cr - (cb * cp1 - sb * sp1) * sr;
        double sp2p = (sb * cp2 + cb * sp2) * cr + (cb * cp2 - sb * sp2) * sr;
        double sm2m = (sb * cp2 - cb * sp2) * cr - (cb * cp2 + sb * sp2) * sr;
        double sm1p = (sb * cp1 - cb * sp1) * cr + (cb * cp1 + sb * sp1) * sr;

        double p1r = -(vr1 + vr1h) * sp1m;
        double p1i = -(vi1 + vi1h) * sp1m;
        double p2r = -(vr2 + vr2h) * sp2p;
        double p2i = -(vi2 + vi2h) * sp2p;
        double p3r = (vr3 + vr3h) * sm2m;
        double p3i = (vi3 + vi3h) * sm2m;
        double p4r = (vr4 + vr4h) * sm1p;
        double p4i = (vi4 + vi4h) * sm1p;

        double p5r = ((vr1 + vr5h) * c23 + (vi1 + vi5h) * s23) * sp1m;
        double p5i = ((vi1 + vi5h) * c23 - (vr1 + vr5h) * s23) * sp1m;
        double p6r = ((vr2 + vr6h) * c14 + (vi2 + vi6h) * s14) * sp2p;
        double p6i = ((vi2 + vi6h) * c14 - (vr2 + vr6h) * s14) * sp2p;
        double p7r = -((vr3 + vr7h) * c11 + (vi3 + vi7h) * s11) * sm2m;
        double p7i = -((vi3 + vi7h) * c11 - (vr3 + vr7h) * s11) * sm2m;
        double p8r = -((vr4 + vr8h) * c22 + (vi4 + vi8h) * s22) * sm1p;
        double p8i = -((vi4 + vi8h) * c22 - (vr4 + vr8h) * s22) * sm1p;

        double ephr = (p1r + p2r + p3r + p4r + p5r + p6r + p7r + p8r) * cdel;
        double ephi = (p1i + p2i + p3i + p4i + p5i + p6i + p7i + p8i) * cdel;

        double rain = 0.0296 * (ethr * ethr + ethi * ethi + ephr * ephr + ephi * ephi);

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) - 1.7;
    }
}
