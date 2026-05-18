package com.jantenna.physics;

import com.jantenna.math.MatrixOperations;
import com.jantenna.math.SpecialFunctions;
import com.jantenna.math.ComplexMatrix;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/**
 * KOP 4: Horizontal Yagi array antenna gain calculator.
 * Port of FORTRAN MUFESGAN.FOR lines 372-462 and SIM subroutine (sim.for).
 */
public final class YagiGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public YagiGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 4;
    }

    /** Minimum number of elements a Yagi must carry for the port's
     *  multi-element arithmetic to make sense (reflector + driven at the
     *  very least). */
    private static final int MIN_YAGI_ELEMENTS = 2;

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        double el1 = FastMath.abs(params.ynl() / wave);
        if (el1 < 0.25 || el1 > 0.75) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }
        double[] tex = params.tex();
        // tex[1] carries the element count. Guard against missing / zero-filled
        // tex arrays from ill-configured callers, which would otherwise crash
        // at d[nm1 - 1] below when nm1 - 1 goes negative. Signal the problem
        // by returning the gain floor rather than throwing — this mirrors the
        // semantics of the other early-return guards in this calculator.
        if (tex == null || tex.length < 4 || ((int) tex[1]) < MIN_YAGI_ELEMENTS) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double ch = qpar.abs();
        double psih = qpar.getArgument();
        double cv = qper.abs();
        double psiv = qper.getArgument();

        double x = FastMath.abs(params.ynh() / wave);
        double hwave = MathConstants.PI2 * x;
        double hqwave = 2.0 * hwave * q;

        double reTA = params.toaz();
        double sb = FastMath.sin(reTA);
        double cb = FastMath.cos(reTA);

        double w1 = ch * FastMath.cos(psih - hqwave);
        double w2 = ch * FastMath.sin(psih - hqwave);
        double w3 = cv * FastMath.cos(psiv - hqwave);
        double w4 = cv * FastMath.sin(psiv - hqwave);

        int n = (int) tex[1];
        int nm1 = n - 1;
        int nm2 = n - 2;

        // 0-indexed arrays (FORTRAN 1-indexed)
        double[] d = new double[n];
        double[] ell = new double[n];
        double[] xk = new double[n];

        // EX(4)/WAVE -> tex[3]; EX(3)/WAVE -> tex[2]; EX(1)/WAVE -> tex[0]
        d[nm1 - 1] = FastMath.abs(tex[3]) / wave;  // D(NM1) = EX(4)/WAVE
        d[0] = FastMath.abs(tex[2]) / wave;          // D(1) = EX(3)/WAVE

        double phi = params.ynd();
        ell[n - 1] = MathConstants.PI * FastMath.abs(phi) / wave; // ELL(N)
        double fac = MathConstants.PI * el1;
        ell[nm1 - 1] = fac;                          // ELL(NM1) = FAC = pi*el1
        ell[0] = MathConstants.PI * FastMath.abs(tex[0]) / wave;  // ELL(1)

        xk[0] = 0.0;

        // Fill intermediate elements for N > 3
        if (nm2 >= 2) {
            for (int j = 2; j <= nm2; j++) {         // j=2..NM2 (1-indexed)
                ell[j - 1] = ell[0];                  // 0-indexed: j-1
                d[j - 1] = d[0];
            }
        }

        // Build XK array: XK(J) = XK(J-1) + D(J-1)
        for (int j = 2; j <= n; j++) {               // 1-indexed j
            xk[j - 1] = xk[j - 2] + d[j - 2];
        }

        // Build impedance matrix
        double[][] yr = new double[n][n];
        double[][] yi = new double[n][n];

        double[] d1d = new double[n];

        for (int i = 1; i <= n; i++) {               // 1-indexed
            int nmxIdx = i - 1;                       // 0-indexed active element
            int no = i;
            // Compute D1D for K=1..I
            for (int k = 1; k <= no; k++) {
                double dist = MathConstants.PI2 * FastMath.abs(xk[i - 1] - xk[k - 1]);
                if (i == k) {
                    dist = ell[i - 1] / 125.1579;    // self-distance
                }
                d1d[k - 1] = dist;
            }
            // Call SIM: compute ZS[0..no-1]
            Complex[] zs = sim(d1d, ell, nmxIdx, no);

            for (int j = 1; j <= i; j++) {
                yr[i - 1][j - 1] = zs[j - 1].getReal();
                yr[j - 1][i - 1] = yr[i - 1][j - 1];
                yi[i - 1][j - 1] = zs[j - 1].getImaginary();
                yi[j - 1][i - 1] = yi[i - 1][j - 1];
            }
        }

        // Invert complex matrix: CALL CMPINV(YR, YI, TX, TY, N)
        // FORTRAN: CIX(I)=TX(I,NM1) where NM1=N-1 (1-indexed) -> column NM1-1=N-2 (0-indexed)
        ComplexMatrix inv = MatrixOperations.invertComplex(new ComplexMatrix(yr, yi));
        double[] cix = new double[n];
        double[] ciy = new double[n];
        for (int i = 0; i < n; i++) {
            cix[i] = inv.real()[i][nm1 - 1];
            ciy[i] = inv.imag()[i][nm1 - 1];
        }

        // V = 1 / CMPLX(CIX(NM1), CIY(NM1)) — CIX(NM1) in FORTRAN 1-indexed = cix[nm1-1] (0-indexed)
        Complex v = new Complex(cix[nm1 - 1], ciy[nm1 - 1]).reciprocal();
        double sum1 = v.getReal();

        // Build D1D for second SIM call
        double tt4 = 4.0 * x * x;
        for (int j = 1; j <= nm1; j++) {             // J=1..NM1 (1-indexed)
            double tx1 = (double)(nm1 - j) * (nm1 - j);
            d1d[j - 1] = MathConstants.PI2 * FastMath.sqrt(tt4 + tx1 * d[j - 1] * d[j - 1]);
        }
        d1d[n - 1] = MathConstants.PI2 * FastMath.sqrt(tt4 + d[nm1 - 1] * d[nm1 - 1]);

        // NMX = NM1 (1-indexed) -> nmxIdx = nm1-1 = N-2 (0-indexed); NO = N
        Complex[] zs2 = sim(d1d, ell, nm1 - 1, n);

        // V = sum_j CMPLX(CIX(J), CIY(J)) * ZS(J) for J=1..N
        Complex vSum = Complex.ZERO;
        for (int j = 0; j < n; j++) {
            vSum = vSum.add(new Complex(cix[j], ciy[j]).multiply(zs2[j]));
        }
        Complex sqrd = dif.sqrt();
        Complex denom = Complex.ONE.add(sqrd);
        Complex numer = Complex.ONE.subtract(sqrd);
        double sum2 = vSum.multiply(numer.divide(denom))
                          .divide(new Complex(cix[nm1 - 1], ciy[nm1 - 1]))
                          .getReal();
        double rin = sum1 + sum2;

        // Radiation pattern
        double cpsi = t * sb;
        double spsi2 = 1.0 - cpsi * cpsi;
        if (spsi2 == 0.0) {
            return AntennaConstants.GAIN_FLOOR_DB;
        }

        double etr = 0.0;
        double eti = 0.0;
        double pr = -cb * MathConstants.PI2 * t;

        for (int j = 0; j < n; j++) {
            double ctk = FastMath.cos(pr * xk[j]);
            double stk = FastMath.sin(pr * xk[j]);
            double sinEll = FastMath.sin(ell[j]);
            double sis = 1.0 / sinEll;
            double tt2 = sis * sis * (FastMath.cos(ell[j] * cpsi) - FastMath.cos(ell[j]));
            etr += tt2 * (cix[j] * ctk - ciy[j] * stk);
            eti += tt2 * (cix[j] * stk + ciy[j] * ctk);
        }

        double epmag = cb * cb * (FastMath.pow(etr * (1.0 + w1) - eti * w2, 2)
                                + FastMath.pow(eti * (1.0 + w1) + etr * w2, 2));
        double etmag = sb * sb * q * q * (FastMath.pow(etr * (1.0 - w3) + eti * w4, 2)
                                        + FastMath.pow(eti * (1.0 - w3) - etr * w4, 2));

        double rain = 120.0 * rin * (etmag + epmag) / (spsi2 * spsi2);
        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001));
    }

    /**
     * Port of SIM subroutine (sim.for).
     * Calculates mutual impedance between parallel dipole elements of unequal lengths.
     *
     * @param d1d    array of distances (PI2 * |XK[i]-XK[k]| or self-distance)
     * @param ell    half-lengths array (0-indexed)
     * @param nmxIdx 0-indexed active element index (FORTRAN NMX - 1)
     * @param no     number of elements to compute (FORTRAN NO)
     * @return array of complex mutual impedances ZS[0..no-1]
     */
    private Complex[] sim(double[] d1d, double[] ell, int nmxIdx, int no) {
        Complex[] zs = new Complex[no];
        for (int j = 0; j < no; j++) {
            double djj = d1d[j];
            double djs = djj * djj;
            double hs = ell[nmxIdx] + ell[j];
            double hd = ell[nmxIdx] - ell[j];
            double cw1 = FastMath.cos(hs);
            double cw2 = FastMath.cos(hd);
            double sw1 = FastMath.sin(hs);
            double sw2 = FastMath.sin(hd);

            double tt = FastMath.sqrt(djs + hs * hs);
            double uz = tt - hs;
            double vz = tt + hs;
            tt = FastMath.sqrt(djs + hd * hd);
            double uzp = tt - hd;
            double vzp = tt + hd;
            tt = FastMath.sqrt(djs + ell[nmxIdx] * ell[nmxIdx]);
            double u1 = tt - ell[nmxIdx];
            double v1 = tt + ell[nmxIdx];
            tt = FastMath.sqrt(djs + ell[j] * ell[j]);
            double u2 = tt - ell[j];
            double v2 = tt + ell[j];

            Complex term1 = csz1(uz)
                    .subtract(csz1(u1))
                    .subtract(csz1(u2))
                    .multiply(new Complex(cw1, -sw1));
            Complex term2 = csz1(vz)
                    .subtract(csz1(v1))
                    .subtract(csz1(v2))
                    .multiply(new Complex(cw1, sw1));
            Complex term3 = csz1(uzp)
                    .subtract(csz1(u1))
                    .subtract(csz1(v2))
                    .multiply(new Complex(cw2, -sw2));
            Complex term4 = csz1(vzp)
                    .subtract(csz1(v1))
                    .subtract(csz1(u2))
                    .multiply(new Complex(cw2, sw2));
            Complex term5 = csz1(djj).multiply(2.0 * (cw1 + cw2));

            Complex zsj = term1.add(term2).add(term3).add(term4).add(term5);
            // ZS(J) = ZS(J) * 60.0 / (CW2 - CW1)
            zs[j] = zsj.multiply(60.0 / (cw2 - cw1));
        }
        return zs;
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
