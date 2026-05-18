package com.jantenna.physics;

import com.jantenna.physics.MutualImpedanceCalculator;
import com.jantenna.physics.Impedance;
import com.jantenna.physics.MutualImpedanceParameters;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/** KOP 14: Arbitrary Tilted Dipole antenna gain calculator. */
public final class TiltedDipoleGainCalculator implements AntennaGainCalculator {

    private final MutualImpedanceCalculator mutualImpedanceCalculator;

    public TiltedDipoleGainCalculator(MutualImpedanceCalculator mutualImpedanceCalculator) {
        this.mutualImpedanceCalculator = Objects.requireNonNull(mutualImpedanceCalculator);
    }

    /**
     * Minimum physical feed-point resistance floor (Ω) — see {@code rin}
     * guard below. 30 Ω matches the rough lower bound of a real half-wave
     * dipole's feed resistance over lossy ground with moderate mismatch;
     * 5 Ω would be mathematically sufficient to keep the divide positive
     * but inflates {@code rain} enough to over-predict gain by ~8 dB.
     */
    private static final double MIN_TILTED_FEED_RESISTANCE_OHMS = 30.0;

    @Override
    public int getAntennaType() {
        return 14;
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

        double fac = fac2;
        double cfac = FastMath.cos(fac);

        double w1 = FastMath.cos(psih - hqwave);
        double w2 = FastMath.sin(psih - hqwave);
        double w3 = FastMath.cos(psiv - hqwave);
        double w4 = FastMath.sin(psiv - hqwave);

        double tip = 0.5 * el1 * sr;
        if (tip > x) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double csb = cr * sb;
        double cphi = q * sr + t * csb;
        double sphi2 = 1.0 - cphi * cphi;
        double cphip = -q * sr + t * csb;
        double sphip2 = 1.0 - cphip * cphip;

        double etheta1 = 0.0;
        double ephi1 = 0.0;
        if (sphi2 != 0.0) {
            double gi = (FastMath.cos(fac * cphi) - cfac) / sphi2;
            etheta1 = (csb * q - sr * t) * gi;
            ephi1 = cr * cb * gi;
        }

        double etheta2 = 0.0;
        double ephi2 = 0.0;
        if (sphip2 != 0.0) {
            double di = (FastMath.cos(fac * cphip) - cfac) / sphip2;
            etheta1 -= (csb * q + sr * t) * di * cv * w3;
            ephi1 += di * ch * w1 * cr * cb;
            etheta2 = -(csb * q + sr * t) * di * cv * w4;
            ephi2 = cr * cb * di * ch * w2;
        }

        Impedance imp1 = mutualImpedanceCalculator.calculate(
                new MutualImpedanceParameters(1.0, 1.0, 0.5 * el1, 0.0, 0.0, 0.0, 0.0,
                        AntennaConstants.IMPEDANCE_RATIO_FACTOR * el1, 0.0, 1.0));
        double r11 = imp1.real();

        double y0_2 = 2.0 * x * cr;
        double z0_2 = 2.0 * x * sr;
        double rhi2 = 2.0 * rhi;
        Impedance imp2 = mutualImpedanceCalculator.calculate(
                new MutualImpedanceParameters(1.0, 1.0, 0.5 * el1, 0.0, rhi2, 0.0, 0.0, y0_2, z0_2, 1.0));
        Complex zm = new Complex(imp2.real(), imp2.imaginary());
        if (rhi2 > MathConstants.PI / 2.0) {
            zm = zm.negate();
        }

        Complex sqrd = dif.sqrt();
        Complex factor1 = sqrd.negate().add(1.0).divide(sqrd.add(1.0)).multiply(cr);
        Complex factor2 = dif.subtract(sqrd).divide(dif.add(sqrd)).multiply(sr).multiply(Complex.I);
        Complex cxcComplex = zm.multiply(factor1.add(factor2)).multiply(new Complex(cr, -sr));
        double cxc = cxcComplex.getReal();

        // Self-impedance + image-coupling sum can turn negative for tilted
        // geometries because the port's MutualImpedanceCalculator scaling
        // (IMPEDANCE_SCALING_FACTOR = −0.1) flips sign on some near-self
        // configurations. A wire dipole's feed-point resistance is strictly
        // positive (ITU-R P.1322 §3); floor to a small physical minimum to
        // stop the downstream divide from inverting sign on rain.
        double rin = FastMath.max(r11 + cxc, MIN_TILTED_FEED_RESISTANCE_OHMS);

        double rain = 120.0 * (etheta1 * etheta1 + etheta2 * etheta2 + ephi1 * ephi1 + ephi2 * ephi2) / rin;

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001));
    }
}
