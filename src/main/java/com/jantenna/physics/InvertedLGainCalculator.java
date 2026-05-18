package com.jantenna.physics;

import com.jantenna.math.SpecialFunctions;
import com.jantenna.physics.MufParameters;
import com.jantenna.physics.AntennaConstants;
import com.jantenna.math.MathConstants;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.Objects;

/** KOP 8: Inverted-L antenna gain calculator. */
public final class InvertedLGainCalculator implements AntennaGainCalculator {

    private final SpecialFunctions specialFunctions;

    public InvertedLGainCalculator(SpecialFunctions specialFunctions) {
        this.specialFunctions = Objects.requireNonNull(specialFunctions);
    }

    @Override
    public int getAntennaType() {
        return 8;
    }

    @Override
    public double calculateGain(MufParameters params, double q, double t, double wave,
                                Complex qpar, Complex qper, Complex dif) {
        // SPH/CPH retain the path azimuth; SB/CB are then overridden to sin/cos of elevation
        double sph = FastMath.sin(params.toaz());
        double cph = FastMath.cos(params.toaz());
        double sb = q;
        double cb = t;

        double xl = params.ynl();
        double xh = params.ynh();
        double x = Math.abs(xh) / wave;

        double freq = params.frequencyMHz();
        double wk = MathConstants.PI2 / wave;
        double psig = -18000.0 * params.ysig() / freq;

        Complex wk2 = new Complex(params.yeps(), psig).sqrt().multiply(wk);
        Complex wkok2 = wk2.reciprocal().multiply(wk);
        Complex wk2ok = wk2.multiply(1.0 / wk);

        double wl = (xl < 0) ? MathConstants.PI2 * Math.abs(xl) : wk * xl;
        double wh = (xh < 0) ? MathConstants.PI2 * Math.abs(xh) : wk * xh;
        double wlh = wl + wh;

        double swl = FastMath.sin(wl);
        double cwl = FastMath.cos(wl);
        double swlh = FastMath.sin(wlh);
        double swlh2 = swlh * swlh;
        double cwlh = FastMath.cos(wlh);

        // RC = sqrt(1 - (WKOK2*CB)^2); CB=T (cos elevation) at this point
        Complex rc = Complex.ONE.subtract(wkok2.multiply(cb).pow(2)).sqrt();
        Complex rv = new Complex(sb, 0).subtract(wkok2.multiply(rc))
                .divide(new Complex(sb, 0).add(wkok2.multiply(rc)));
        Complex rh = new Complex(sb, 0).subtract(wk2ok.multiply(rc))
                .divide(new Complex(sb, 0).add(wk2ok.multiply(rc)));

        double rvab = rv.abs();
        double rhab = rh.abs();
        // Local PSIV/PSIH override: reflection coefficients for this antenna geometry
        double localPsiv = rv.getArgument();
        double localPsih = rh.getArgument();

        double cpsiph = cb * sph;
        double psiph = FastMath.acos(cpsiph);
        double spsiph = FastMath.sin(psiph);
        double spsiph2 = spsiph * spsiph;

        double wb = wh * sb;
        double swb = FastMath.sin(wb);
        double cwb = FastMath.cos(wb);

        double a4 = cwl * cwb - sb * swl * swb - cwlh;
        double b4 = sb * swl * cwb + cwl * swb - sb * swlh;
        double ab4 = FastMath.sqrt(a4 * a4 + b4 * b4);
        double bp = (ab4 != 0.0) ? FastMath.atan2(b4, a4) : 0.0;

        double wc = wl * cpsiph;
        double swc = FastMath.sin(wc);
        double cwc = FastMath.cos(wc);
        double a5 = cwc - cwl;
        double b5 = swc - cpsiph * swl;
        double ab5 = FastMath.sqrt(a5 * a5 + b5 * b5);
        double bpp = (ab5 != 0.0) ? FastMath.atan2(b5, a5) : 0.0;

        double parv = bpp + localPsiv - 2.0 * wh * sb;
        double parh = bpp + localPsih - 2.0 * wh * sb;

        double f11, g11, f2, g2;
        if (spsiph2 != 0.0) {
            double dab5 = ab5 * sph * sb / spsiph2;
            f11 = dab5 * (FastMath.cos(bpp) - rvab * FastMath.cos(parv));
            g11 = dab5 * (FastMath.sin(bpp) - rvab * FastMath.sin(parv));
            double hab5 = ab5 * cph / spsiph2;
            f2 = hab5 * (FastMath.cos(bpp) + rhab * FastMath.cos(parh));
            g2 = hab5 * (FastMath.sin(bpp) + rhab * FastMath.sin(parh));
        } else {
            f11 = 0.0;
            g11 = 0.0;
            f2 = 0.0;
            g2 = 0.0;
        }

        double f12, g12;
        if (cb != 0.0) {
            double dab4 = ab4 / cb;
            f12 = -dab4 * (FastMath.cos(bp) + rvab * FastMath.cos(localPsiv - bp));
            g12 = -dab4 * (FastMath.sin(bp) + rvab * FastMath.sin(localPsiv - bp));
        } else {
            f12 = 0.0;
            g12 = 0.0;
        }

        double f1 = f11 + f12;
        double g1 = g11 + g12;
        double g = 30.0 * (f1 * f1 + g1 * g1 + f2 * f2 + g2 * g2);

        double w2h = 2.0 * wh;
        double w4h = 2.0 * w2h;

        Complex csz2h = csz1(w2h);
        Complex csz4h = csz1(w4h);

        double ci2 = csz2h.getReal();
        double ci4 = csz4h.getReal();
        double gama = AntennaConstants.EULER_MASCHERONI;
        double cin2 = gama + FastMath.log(w2h) - ci2;
        double cin4 = gama + FastMath.log(w4h) - ci4;
        double si2 = -csz2h.getImaginary();
        double si4 = -csz4h.getImaginary();

        double cw2h = FastMath.cos(w2h);
        double sw2h = FastMath.sin(w2h);
        double vrt = 30.0 * ((1.0 + cw2h) * cin2 - 0.5 * cw2h * cin4 - sw2h * (si2 - 0.5 * si4));

        // RINTW = 18.06 is the reference impedance for a vertical monopole at H/lambda=0.2
        final double rintw = 18.06;
        double rin = vrt;
        if (x < 0.2) {
            rin = 400.0 * x * x * rintw / 16.0;
        }

        // XINTR interpolates conductivity correction
        final double xintr = 0.60001;
        double fmult = 4.0 - xintr * (params.ysig() - 0.0001);
        rin = 16.0 * fmult * rin / rintw;

        double rain = g / rin;

        double eff = 0.0;
        if (x <= 0.20) {
            eff = 20.0 * FastMath.log10(x * (6.335 + x * (67.95 + x * (-693.0 + x * 1600.0))));
        }

        return 10.0 * FastMath.log10(FastMath.max(rain, 0.001)) + eff;
    }

    /** CSZ1 in Fortran convention: {@code Ci(x) − i·Si(x)}. */
    private Complex csz1(double x) {
        return specialFunctions.csz1(x).conjugate();
    }
}
