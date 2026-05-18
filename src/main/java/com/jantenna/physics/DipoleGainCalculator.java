package com.jantenna.physics;

import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/**
 * KOP 3: Horizontal Half-Wave Dipole antenna gain calculator.
 *
 * <p>Direct port of {@code MUFESGAN.FOR} lines 331-371 (label 205, HORIZONTAL
 * HALFWAVE DIPOLE ANTENNA, KOP=3). Input resistance {@code RIN} is computed
 * by the induced-EMF closed-form expression using the complex sine/cosine
 * integral {@code CSZ1} — not by numerical quadrature.</p>
 */
public final class DipoleGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public DipoleGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 3;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t,
                               double wave, Complex qpar, Complex qper, Complex dif) {
        double el1 = FastMath.abs(params.ynl() / wave);
        double fac = MathConstants.PI * el1;
        double hwave = MathConstants.PI2 * (params.ynh() / wave);
        double hqwave = 2.0 * hwave * q;

        // Zero-length antenna (el1 == 0 → fac == 0) produces d1d2 = 0 in the
        // induced-EMF integral, which feeds Ci(0) — a logarithmic singularity.
        // Physically, an antenna with no length has no radiation resistance,
        // so the gain is undefined and we return the conventional floor.
        // Matches the existing sphi2 == 0 / sfac == 0 guards further down.
        if (el1 <= 0.0) return AntennaConstants.GAIN_FLOOR_DB;

        double reTA = params.toaz();
        double sb = FastMath.sin(reTA);
        double cb = FastMath.cos(reTA);
        double cphi = t * sb;
        double sphi2 = 1.0 - cphi * cphi;

        if (sphi2 == 0.0) return AntennaConstants.GAIN_FLOOR_DB;

        double gi = (FastMath.cos(fac * cphi) - FastMath.cos(fac)) / sphi2;

        double arg = qper.getArgument() - hqwave;
        double aa = FastMath.sqrt(1.0 + FastMath.pow(qper.abs(), 2) - 2.0 * qper.abs() * FastMath.cos(arg)) * gi;

        arg = qpar.getArgument() - hqwave;
        double bb = FastMath.sqrt(1.0 + FastMath.pow(qpar.abs(), 2) + 2.0 * qpar.abs() * FastMath.cos(arg)) * gi;

        double rin = calculateFeedPointResistance(el1, fac, hwave, dif);
        double sfac = FastMath.sin(fac);
        if (sfac == 0.0 || rin == 0.0) return AntennaConstants.GAIN_FLOOR_DB;

        double rain = (120.0 * (aa * aa * sb * sb * q * q + bb * bb * cb * cb)) /
                (rin * sfac * sfac);

        // MUFESGAN.FOR lines 1316-1322: log10 → add user offset (PHI for KOP=3)
        // → floor at GAIN_FLOOR_DB.
        double gainDb = 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) + params.ynd();
        return FastMath.max(gainDb, AntennaConstants.GAIN_FLOOR_DB);
    }

    /**
     * Feed-point resistance by the induced-EMF closed-form, per
     * {@code MUFESGAN.FOR} label 205 (lines 348-366).
     *
     * <pre>
     *   D1D(1) = 2·HWAVE                  (image distance, 2·height·2π/λ)
     *   D1D(2) = RATIO · FAC              (thin-wire equivalent-radius distance)
     *   Z(J)   = closed-form CSZ1 sum     (induced-EMF integral, parallel dipoles)
     *   CXC    = Re[ Z(1) · (1−√DIF)/(1+√DIF) ]
     *   RIN    = Re[ Z(2) ] + CXC
     * </pre>
     *
     * <p>{@code RATIO = √2/4680} is the thin-wire radius factor from Ma (1974)
     * Table 4.1 p. 254 — length/diameter = 4680 for a half-wave dipole. The
     * image-theory ground-reflection factor {@code RHCP = (1−√DIF)/(1+√DIF)}
     * scales the real-to-image mutual impedance {@code Z(1)}.</p>
     */
    private double calculateFeedPointResistance(double el1, double fac, double hwave, Complex dif) {
        double fac2 = 2.0 * fac;
        double sfac2 = FastMath.sin(fac2);
        double cfac2 = FastMath.cos(fac2);

        double d1d1 = 2.0 * hwave;
        double d1d2 = AntennaConstants.IMPEDANCE_RATIO_FACTOR * fac;

        // When the antenna is at ground level (hwave=0), d1d1=0 and Ci(0) diverges;
        // skip the image term (z1 contribution = 0, matching an antenna without a ground reflection).
        Complex z1 = (d1d1 > 0.0) ? inducedEmfImpedance(d1d1, fac, fac2, cfac2, sfac2) : Complex.ZERO;
        Complex z2 = inducedEmfImpedance(d1d2, fac, fac2, cfac2, sfac2);

        Complex sqrd = dif.sqrt();
        Complex rhcp = Complex.ONE.subtract(sqrd).divide(Complex.ONE.add(sqrd));
        double cxc = z1.multiply(rhcp).getReal();
        return z2.getReal() + cxc;
    }

    /**
     * Closed-form impedance between two parallel half-wave dipoles separated
     * axially by {@code d}, using complex sine/cosine integrals.
     *
     * <p>Port of {@code MUFESGAN.FOR} DO 210 loop (lines 353-363):</p>
     * <pre>
     *   TT = √(d² + FAC2²);  UZ = TT − FAC2;  VZ = TT + FAC2
     *   TT = √(d² + FAC²);   U1 = TT − FAC;   V1 = TT + FAC
     *   Z  = [CSZ1(UZ) − 2·CSZ1(U1)] · (CFAC2 − i·SFAC2)
     *      + [CSZ1(VZ) − 2·CSZ1(V1)] · (CFAC2 + i·SFAC2)
     *      − 2·[CSZ1(U1) + CSZ1(V1)]
     *      + 2·CSZ1(d) · (CFAC2 + 2)
     *   Z  = Z · 60 / (1 − CFAC2)
     * </pre>
     *
     * <p>Fortran {@code CSZ1(x) = Ci(x) − i·Si(x)} ({@code csz1.for} line 39),
     * whereas {@link SpecialFunctions#csz1(double)} returns
     * {@code Ci(x) + i·Si(x)}. Applying {@code .conjugate()} converts to the
     * Fortran convention so the literal formula ports 1:1.</p>
     */
    private Complex inducedEmfImpedance(double d, double fac, double fac2,
                                        double cfac2, double sfac2) {
        double d2 = d * d;
        double tt = FastMath.sqrt(d2 + fac2 * fac2);
        double uz = tt - fac2;
        double vz = tt + fac2;
        tt = FastMath.sqrt(d2 + fac * fac);
        double u1 = tt - fac;
        double v1 = tt + fac;

        Complex csUz = csz1(uz);
        Complex csVz = csz1(vz);
        Complex csU1 = csz1(u1);
        Complex csV1 = csz1(v1);
        Complex csD  = csz1(d);

        Complex eMinus = new Complex(cfac2, -sfac2);
        Complex ePlus  = new Complex(cfac2,  sfac2);

        Complex term1 = csUz.subtract(csU1.multiply(2.0)).multiply(eMinus);
        Complex term2 = csVz.subtract(csV1.multiply(2.0)).multiply(ePlus);
        Complex term3 = csU1.add(csV1).multiply(-2.0);
        Complex term4 = csD.multiply(2.0 * (cfac2 + 2.0));

        Complex sum = term1.add(term2).add(term3).add(term4);
        double denom = 1.0 - cfac2;
        if (denom == 0.0) return Complex.ZERO;
        return sum.multiply(60.0 / denom);
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
