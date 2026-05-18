package com.jantenna.physics;

import com.jantenna.physics.MutualImpedanceCalculator;
import com.jantenna.math.MatrixOperations;
import com.jantenna.physics.MutualImpedanceParameters;
import com.jantenna.math.ComplexMatrix;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/** KOP 13: Horizontal Log-Periodic antenna gain calculator. */
public final class LogPeriodicGainCalculator implements AntennaGainCalculator {

    private final MutualImpedanceCalculator mutualImpedanceCalculator;

    public LogPeriodicGainCalculator(MutualImpedanceCalculator mutualImpedanceCalculator) {
        this.mutualImpedanceCalculator = Objects.requireNonNull(mutualImpedanceCalculator);
    }

    @Override
    public int getAntennaType() {
        return 13;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                               Complex qpar, Complex qper, Complex dif) {
        // tex maps to legacy 'aex' field
        double[] tex = params.tex();
        double z0 = tex[0];
        double alpha = tex[1];
        double tau = tex[2];
        int n = (int) tex[3];

        if (n < 2 || n > 20) return AntennaConstants.GAIN_FLOOR_DB;

        double yz = 1.0 / z0;
        double[] xk = new double[n];
        double[] ell = new double[n];

        ell[n - 1] = MathConstants.PI * params.ynl() / wave;
        xk[n - 1] = ell[n - 1] * (1.0 / FastMath.tan(FastMath.toRadians(alpha)));

        for (int i = n - 2; i >= 0; i--) {
            ell[i] = ell[i + 1] * tau;
            xk[i] = xk[i + 1] * tau;
        }

        // Admittance Matrix (YI)
        double[][] yi = new double[n][n];
        for (int i = 0; i < n - 1; i++) {
            double term = -yz / FastMath.sin(xk[i + 1] - xk[i]);
            yi[i][i + 1] = term;
            yi[i + 1][i] = term;

            if (i > 0) {
                yi[i][i] = -yz * (1.0 / FastMath.tan(xk[i + 1] - xk[i]) + 1.0 / FastMath.tan(xk[i] - xk[i - 1]));
            }
        }
        yi[0][0] = -yz * (1.0 / FastMath.tan(xk[1] - xk[0]));

        double shortCot = 1.0 / FastMath.tan(ell[n - 1] / 2.0);
        yi[n - 1][n - 1] = -(yz / shortCot + yz * (1.0 / FastMath.tan(xk[n - 1] - xk[n - 2])));

        // Impedance Matrix (Z). forElement's second positional arg is the
        // element-to-element separation (y0); when i == j we use the thin-
        // wire radius as a stand-in to avoid the integrand singularity at
        // y0 = 0. The third positional (r21) is an output slot — it must
        // stay zero. Earlier versions of this port miswired r21 with the
        // thin-wire radius and left y0 = 0, which produced NaN from the
        // self-impedance path.
        Complex[][] z = new Complex[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double dist = FastMath.abs(xk[i] - xk[j]);
                double effectiveY0 = (i == j)
                        ? ell[i] * AntennaConstants.IMPEDANCE_RATIO_FACTOR
                        : dist;
                Complex zij = mutualImpedanceCalculator.calculate(
                        MutualImpedanceParameters.forElement(ell[j], effectiveY0, 0.0)).toComplex();
                z[i][j] = zij;
                z[j][i] = zij;
            }
        }

        // System Matrix TX + jTY = I + Y*Z
        double[][] tx = new double[n][n];
        double[][] ty = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    tx[i][j] -= yi[i][k] * z[k][j].getImaginary();
                    ty[i][j] += yi[i][k] * z[k][j].getReal();
                }
            }
            tx[i][i] += 1.0;
        }

        ComplexMatrix invSystem = MatrixOperations.invertComplex(new ComplexMatrix(tx, ty));

        // Input Resistance (rin) calculation
        double rin = 0.0;
        for (int i = 0; i < n; i++) {
            double cix = invSystem.real()[i][0];
            double ciy = invSystem.imag()[i][0];
            rin += cix * z[i][0].getReal() - ciy * z[i][0].getImaginary();
        }
        // Floor rin at a small positive value: like Dipole/TiltedDipole, the
        // LPDA port's MutualImpedanceCalculator scaling can produce a
        // negative self-impedance sum in certain regimes. A dipole feed
        // impedance is strictly positive, so clamp rather than let a
        // negative rin invert the rain sign downstream.
        rin = FastMath.max(rin, 1.0);

        return calculateFieldSum(n, xk, ell, invSystem.real(), invSystem.imag(), q, t, params, rin);
    }

    private double calculateFieldSum(int n, double[] xk, double[] ell, double[][] cix, double[][] ciy,
                                     double q, double t, MufParameters params, double rin) {
        double reTA = params.toaz();
        double sb = FastMath.sin(reTA);
        double cpsi = t * sb;
        double spsi2 = 1.0 - cpsi * cpsi;

        if (spsi2 == 0.0) return AntennaConstants.GAIN_FLOOR_DB;

        double rhi = FastMath.toRadians(params.ynd());
        double cr = FastMath.cos(rhi);
        double sr = FastMath.sin(rhi);

        double cq2 = cr * q;
        double cbeta = cq2 - t * FastMath.cos(reTA) * sr;
        double etr = 0.0, eti = 0.0;

        for (int j = 0; j < n; j++) {
            double sinEll = FastMath.sin(ell[j]);
            if (FastMath.abs(sinEll) < 1e-9) sinEll = 1e-9;

            double ccb = FastMath.cos(xk[j] * cbeta) / sinEll;
            double scb = FastMath.sin(xk[j] * cbeta) / sinEll;
            double tt = FastMath.cos(ell[j] * cpsi) - FastMath.cos(ell[j]);

            etr += (cix[j][0] * ccb - ciy[j][0] * scb) * tt;
            eti += (ciy[j][0] * ccb + cix[j][0] * scb) * tt;
        }

        double etmag = (etr * etr + eti * eti) * FastMath.pow(q * sb / spsi2, 2);
        double rain = 120.0 * etmag / rin;
        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) + params.ynh();
    }
}
