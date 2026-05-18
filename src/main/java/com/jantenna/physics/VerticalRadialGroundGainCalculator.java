package com.jantenna.physics;

import com.jantenna.physics.VOACAP48;
import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/**
 * KOP 17: Vertical monopole with radial conductor ground system antenna gain calculator.
 * Port of FORTRAN MUFESGAN.FOR lines 165 (shared KOP 2 setup) + 265-330 (KOP 17 specific).
 * Shares the RZERO computation with MonopoleGainCalculator (KOP 2).
 */
public final class VerticalRadialGroundGainCalculator implements AntennaGainCalculator {

    private static final double RINTW = 18.06;

    private final SpecialFunctions specialFunctions;

    public VerticalRadialGroundGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 17;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        double el1 = FastMath.abs(params.ynl() / wave);
        double fac2 = MathConstants.PI2 * el1;

        double cfac2 = FastMath.cos(fac2);
        double sfac2 = FastMath.sin(fac2);
        double hq = fac2 * q;
        double a = FastMath.cos(hq) - cfac2;
        double as = FastMath.sin(hq) - q * sfac2;

        // === RZERO calculation (same as MonopoleGainCalculator / KOP 2) ===
        double flog = FastMath.log(fac2);
        double c2kel = 2.0 * cfac2 * cfac2 - 1.0;
        double s2kel = 2.0 * cfac2 * sfac2;

        Complex zt1 = csz1(4.0 * fac2);
        double rzero = 0.5 * (c2kel * (zt1.getReal() - flog - 1.3862943612
                - AntennaConstants.EULER_MASCHERONI) - s2kel * zt1.getImaginary());

        Complex zt2 = csz1(fac2 * 2.0);   // FAC4 = 2*FAC2
        rzero += 30.0 * (rzero + (1.0 + c2kel) * (-zt2.getReal() + flog
                + 0.6931471806 + AntennaConstants.EULER_MASCHERONI) + s2kel * zt2.getImaginary());

        if (el1 < 0.2) {
            rzero = 400.0 * el1 * el1 * RINTW / 16.0;
        }

        // === KOP 17 specific: label 175 ===
        double[] tex = params.tex();
        double sigma = params.ysig();
        double er = params.yeps();
        double freq = params.frequencyMHz();
        double x = FastMath.abs(params.ynh() / wave);

        double phi = params.ynd();
        double aa = FastMath.abs(phi) / wave;
        double caya = MathConstants.PI2 * aa;

        // ETA = CSQRT(CMPLX(0, 8*PI^2*FREQ*0.1) / CMPLX(SIGMA, FREQ*ER*0.001/18.0))
        Complex etaNum = new Complex(0.0, 8.0 * MathConstants.PI * MathConstants.PI * freq * 0.1);
        Complex etaDen = new Complex(sigma, freq * er * 0.001 / 18.0);
        Complex eta = etaNum.divide(etaDen).sqrt();
        double alpha = eta.getArgument() + MathConstants.PI / 2.0;

        // Little R0 and R1
        double rz = FastMath.sqrt(aa * aa + el1 * el1);
        double r1 = aa + rz;

        // ZTR = CMPLX(0, PI/2)
        Complex ztr = new Complex(0.0, MathConstants.PI / 2.0);

        // DELTAZ1 — EQN.(39), P.14
        // DELZ = (CSZ1(2*PI2*(RZ+EL1))+ZTR)*CMPLX(C2KEL,S2KEL)
        //       +(CSZ1(2*PI2*(RZ-EL1))+ZTR)*CMPLX(C2KEL,-S2KEL)
        //       +(CSZ1(2*CAYA)+ZTR)*2*CFAC2^2
        //       +(CSZ1(PI2*R1)+ZTR)*4*CFAC2
        //       -(CSZ1(PI2*(R1-EL1))+ZTR)*4*CFAC2*CMPLX(CFAC2,-SFAC2)
        //       -(CSZ1(PI2*(R1+EL1))+ZTR)*4*CFAC2*CMPLX(CFAC2,SFAC2)
        Complex delz = csz1(2.0 * MathConstants.PI2 * (rz + el1)).add(ztr)
                .multiply(new Complex(c2kel, s2kel))
                .add(csz1(2.0 * MathConstants.PI2 * (rz - el1)).add(ztr)
                        .multiply(new Complex(c2kel, -s2kel)))
                .add(csz1(2.0 * caya).add(ztr)
                        .multiply(2.0 * cfac2 * cfac2))
                .add(csz1(MathConstants.PI2 * r1).add(ztr)
                        .multiply(4.0 * cfac2))
                .subtract(csz1(MathConstants.PI2 * (r1 - el1)).add(ztr)
                        .multiply(4.0 * cfac2).multiply(new Complex(cfac2, -sfac2)))
                .subtract(csz1(MathConstants.PI2 * (r1 + el1)).add(ztr)
                        .multiply(4.0 * cfac2).multiply(new Complex(cfac2, sfac2)));

        double delr1 = delz.multiply(eta).divide(2.0 * MathConstants.PI2).getReal();
        double eta1 = eta.getReal();
        double eta2 = eta.getImaginary();

        // DELTAZ2 — Gaussian quadrature, EQN.(40), P.15
        double delr2 = 0.0;
        double dp = aa / 2.0;
        double qu = 240.0 * MathConstants.PI * MathConstants.PI / tex[1];
        double cw = wave * 1000.0 / (tex[0] * tex[1]);

        double[] xi = VOACAP48.INSTANCE.getAbscissas();
        double[] hh = VOACAP48.INSTANCE.getWeights();

        for (int j = 0; j < 48; j++) {
            double p = dp * (xi[j] + 1.0);
            double rq = MathConstants.PI2 * FastMath.sqrt(p * p + el1 * el1);
            double plog = FastMath.log(p * cw);
            double qq = qu * p * plog;
            double eq = eta2 + qq;
            double ta = FastMath.atan2(eq, eta1);
            delr2 += (FastMath.cos(alpha - ta - 2.0 * rq)
                    + cfac2 * cfac2 * FastMath.cos(alpha - ta - 4.0 * MathConstants.PI * p)
                    - 2.0 * cfac2 * FastMath.cos(alpha - ta - MathConstants.PI2 * p - rq))
                    / FastMath.sqrt(eta1 * eta1 + eq * eq) * plog * dp * hh[j];
        }
        delr2 = -120.0 * MathConstants.PI * eta.abs() / tex[1] * delr2;

        double rin = rzero + delr1 + delr2;

        // HRATIO — EQN.(36), P.13
        Complex hratio = Complex.ZERO;
        for (int j = 0; j < 48; j++) {
            double xx = caya / 2.0 * (xi[j] + 1.0);
            double td = FastMath.sqrt(xx * xx + fac2 * fac2);
            double ts = specialFunctions.onej(xx * t);
            hratio = hratio.add(new Complex(
                    hh[j] * ts * (FastMath.cos(td) - FastMath.cos(xx) * cfac2),
                    hh[j] * ts * (-FastMath.sin(td) + FastMath.sin(xx) * cfac2)));
        }
        hratio = Complex.ONE.subtract(hratio.multiply(caya).multiply(eta).multiply(t)
                .divide(120.0 * MathConstants.PI2 * a));

        double rain = 0.0;
        if (FastMath.abs(hratio.getReal()) <= 2.0 && FastMath.abs(hratio.getImaginary()) <= 1.0) {
            double cv = qper.abs();
            double psiv = qper.getArgument();

            double bp = FastMath.atan2(as, a);
            double cayvh = 1.0 + cv * cv + 2.0 * cv * FastMath.cos(psiv - 2.0 * bp);
            double tb = a / (t * FastMath.cos(bp));
            // RAIN = 30/RIN * TB^2 * CAYVH * |HRATIO|^2
            double hratioMag2 = hratio.getReal() * hratio.getReal()
                    + hratio.getImaginary() * hratio.getImaginary();
            rain = 30.0 / rin * tb * tb * cayvh * hratioMag2;
        }

        double eff = 0.0;
        if (el1 < 0.35) {
            eff = -((((6416.702 * el1 - 6091.33) * el1 + 2179.89) * el1 - 364.817) * el1 + 25.646);
        }

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) + eff;
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
