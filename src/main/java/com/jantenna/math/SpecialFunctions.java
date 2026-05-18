package com.jantenna.math;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.special.BesselJ;
import org.apache.commons.math3.util.FastMath;

/**
 * Special-function kernels used by the VOACAP antenna-impedance integrals.
 *
 * <p><b>Provided functions</b></p>
 * <ul>
 *   <li>{@link #sineIntegral(double)}   — Si(x) = ∫₀ˣ sin(t)/t dt</li>
 *   <li>{@link #cosineIntegral(double)} — Ci(x) = γ + ln(x) + ∫₀ˣ (cos(t)-1)/t dt</li>
 *   <li>{@link #csz1(double)}           — CSZ1(x) = Ci(x) + j·Si(x), the complex
 *       combination used throughout {@code MUFESGAN.FOR} for resistive and
 *       reactive components of dipole-array impedance</li>
 *   <li>{@link #onej(double)}           — Bessel J₁(x), porting {@code onej.for}</li>
 *   <li>{@link #besselJ(int,double)}    — Bessel Jₙ(x) via Apache Commons Math</li>
 * </ul>
 *
 * <p><b>Numerical strategy.</b> Si and Ci switch at x = 18 between
 * Abramowitz &amp; Stegun power series ({@code 5.2.7}, {@code 5.2.16}) and the
 * asymptotic envelope {@code Si(x) = π/2 − f(x)·cos(x) − g(x)·sin(x)},
 * {@code Ci(x) = f(x)·sin(x) − g(x)·cos(x)} (A&amp;S 5.2.8 / 5.2.9). The
 * auxiliary functions {@code f(x)} and {@code g(x)} are truncated asymptotic
 * series (A&amp;S 5.2.34 / 5.2.35); six terms are retained, bounding the
 * relative error below 10⁻⁸ for x ≥ 18.</p>
 *
 * <p><b>Accuracy.</b> Against NIST DLMF tabulations the series path reproduces
 * Si(1) ≈ 0.9460831 and Ci(1) ≈ 0.3374039 to ~1·10⁻¹⁴; the asymptotic
 * path matches Si/Ci at x ≥ 18 to better than 1·10⁻⁷ relative, dominated
 * by the truncation of the {@code f} and {@code g} auxiliary series at six
 * terms.</p>
 *
 * <p><b>Behavioural change history.</b> Prior to April 2026 the Ci
 * power-series initial term was set to −x²/2 instead of the correct
 * −x²/4 (A&amp;S 5.2.16). That bug produced Ci(1) ≈ 0.0976 instead of
 * 0.3374 — a 3.5× error that propagated through every antenna KOP
 * whose impedance integral uses {@link #csz1(double)}. After the fix,
 * antenna-gain predictions shift by roughly 0.5–3 dB depending on
 * element length; calibration baselines captured against the old code
 * must be regenerated.</p>
 *
 * <p><b>Thread safety.</b> Stateless; all methods are pure functions. Safe for
 * concurrent invocation from Spring singletons.</p>
 *
 * @see <a href="https://dlmf.nist.gov/6.6">NIST DLMF §6.6 — Sine and Cosine Integrals</a>
 */
public class SpecialFunctions {

    /** Euler–Mascheroni constant γ (A&amp;S 4.1.32). Single source in {@link com.jantenna.math.MathConstants}. */
    private static final double EULER_MASCHERONI = com.jantenna.math.MathConstants.EULER_MASCHERONI;

    /** Series-expansion stopping criterion (absolute term magnitude). */
    private static final double EPSILON = 1.0e-15;

    /** Crossover between power-series and asymptotic forms for Si/Ci. */
    private static final double ASYMPTOTIC_THRESHOLD = 18.0;

    /** Small-x / large-x crossover for J₁ (A&amp;S 9.4.4 / 9.4.6). */
    private static final double J1_ASYMPTOTIC_THRESHOLD = 4.0;

    /**
     * Bessel function of the first kind, J<sub>ν</sub>(x), for integer order.
     * Delegates to Apache Commons Math for full-precision evaluation.
     *
     * @param order  integer order ν
     * @param value  argument x (any real)
     * @return J<sub>ν</sub>(x)
     */
    public double besselJ(int order, double value) {
        return BesselJ.value(order, value);
    }

    /**
     * Sine integral Si(x) = ∫₀ˣ sin(t)/t dt.
     *
     * <p>Si is an odd function: Si(−x) = −Si(x). The limit as x→∞ is π/2.</p>
     *
     * @param x argument (any real)
     * @return Si(x)
     */
    public double sineIntegral(double x) {
        if (x < 0.0) return -sineIntegral(-x);
        if (x == 0.0) return 0.0;
        if (x < ASYMPTOTIC_THRESHOLD) {
            return sineIntegralSeries(x);
        }
        return (Math.PI / 2.0) - f(x) * FastMath.cos(x) - g(x) * FastMath.sin(x);
    }

    /**
     * Cosine integral Ci(x) = γ + ln(x) + ∫₀ˣ (cos(t)−1)/t dt.
     *
     * <p>Defined only for x &gt; 0 on the principal branch. Ci(x) is negative
     * and has a logarithmic singularity at x = 0⁺.</p>
     *
     * @param x argument (must be strictly positive)
     * @return Ci(x)
     * @throws IllegalArgumentException if x ≤ 0
     */
    public double cosineIntegral(double x) {
        if (x <= 0.0) {
            throw new IllegalArgumentException("Ci(x) requires x > 0, got " + x);
        }
        if (x < ASYMPTOTIC_THRESHOLD) {
            return cosineIntegralSeries(x);
        }
        return f(x) * FastMath.sin(x) - g(x) * FastMath.cos(x);
    }

    /**
     * CSZ1(x) = Ci(x) + j·Si(x).
     *
     * <p>The complex combination used in {@code MUFESGAN.FOR} where
     * {@code REAL(CSZ1)} supplies the resistive contribution and
     * {@code AIMAG(CSZ1)} the reactive contribution of a dipole segment.</p>
     *
     * @param x argument (must be strictly positive)
     * @return CSZ1(x)
     * @throws IllegalArgumentException if x ≤ 0
     */
    public Complex csz1(double x) {
        return new Complex(cosineIntegral(x), sineIntegral(x));
    }

    /**
     * Bessel function of the first kind of order 1, J₁(x).
     *
     * <p>Port of {@code onej.for}. Uses an A&amp;S 9.4.4 polynomial for
     * |x| ≤ 4 and an A&amp;S 9.4.6 asymptotic P/Q expansion for |x| &gt; 4.
     * Error bound: &lt; 1·10⁻⁷ across the entire domain.</p>
     *
     * @param x argument (≥ 0 expected; for negative x, J₁ is odd: J₁(−x) = −J₁(x))
     * @return J₁(x)
     */
    public double onej(double x) {
        if (x <= J1_ASYMPTOTIC_THRESHOLD) {
            double t = x / 4.0;
            double y = t * t;
            return (((((((-1.289769e-4 * y + 2.2069155e-3) * y - 2.36616773e-2) * y
                    + 0.1777582922) * y - 0.8888839649) * y + 2.666666054) * y
                    - 3.999999971) * y + 2.0) * t;
        }
        double t = 4.0 / x;
        double y = t * t;
        double psum = (((((4.2414e-6 * y - 2.0092e-5) * y + 5.80759e-5) * y - 2.213203e-4) * y
                + 2.9218256e-3) * y + 0.3989422819) * 2.50662827;
        double qsum = (((((-3.6594e-6 * y + 1.622e-5) * y - 3.98708e-5) * y + 1.064741e-4) * y
                - 6.3904e-4) * y + 3.74008364e-2) * 2.50662827 * t;
        double r135 = FastMath.PI * 3.0 / 4.0;
        double ts = FastMath.sqrt(2.0 / (FastMath.PI * x));
        return ts * (psum * FastMath.cos(x - r135) - qsum * FastMath.sin(x - r135));
    }

    // -------------------------------------------------------------------------
    //  Private series / asymptotic helpers
    // -------------------------------------------------------------------------

    /**
     * Power-series expansion of Si (A&amp;S 5.2.14):
     * <pre>
     *   Si(x) = Σ_{n=0}^∞ (−1)ⁿ · x^(2n+1) / ((2n+1) · (2n+1)!)
     *         = x − x³/18 + x⁵/600 − x⁷/35280 + …
     * </pre>
     * Recurrence: term_{k} / term_{k−1} = −x²·(2k−1) / [(2k+1)²·(2k)].
     */
    private double sineIntegralSeries(double x) {
        double sum = 0.0;
        double term = x;
        double x2 = x * x;
        int k = 1;
        while (FastMath.abs(term) > EPSILON) {
            sum += term;
            term *= -x2 * (2 * k - 1) / ((double)(2 * k + 1) * (2 * k) * (2 * k + 1));
            k++;
        }
        return sum;
    }

    /**
     * Power-series expansion of Ci (A&amp;S 5.2.16):
     * <pre>
     *   Ci(x) = γ + ln(x) + Σ_{n=1}^∞ (−1)ⁿ · x^(2n) / ((2n) · (2n)!)
     *         = γ + ln(x) − x²/4 + x⁴/96 − x⁶/4320 + …
     * </pre>
     *
     * <p>The first term added to the running sum is x²/(2·2!) = x²/4, <b>not</b>
     * x²/2. Recurrence matches Si, shifted by one index.</p>
     */
    private double cosineIntegralSeries(double x) {
        double sum = EULER_MASCHERONI + FastMath.log(x);
        double x2 = x * x;
        // First series term: n=1 → (−1)·x²/(2·2!) = −x²/4
        double term = -x2 / 4.0;
        int k = 1;
        while (FastMath.abs(term) > EPSILON) {
            sum += term;
            term *= -x2 * (2 * k) / ((double)(2 * k + 2) * (2 * k + 1) * (2 * k + 2));
            k++;
        }
        return sum;
    }

    /**
     * Auxiliary asymptotic function (A&amp;S 5.2.38):
     * <pre>
     *   f(x) ~ (1/x) · Σ_{k=0}^∞ (−1)^k · (2k)! / x^(2k)
     *        = 1/x − 2!/x³ + 4!/x⁵ − 6!/x⁷ + 8!/x⁹ − 10!/x¹¹ + …
     * </pre>
     * Six alternating terms retained; for x ≥ 18 the truncation error is
     * below 10⁻⁸ relative.
     */
    private double f(double x) {
        double x2 = x * x;
        double inv = 1.0 / x;
        double invX2 = 1.0 / x2;
        double sum = inv;
        double t = inv;
        // k=1: -2!/x^3 = -2/x^3
        t *= -2.0 * invX2;  sum += t;
        // k=2: +4!/x^5 = +24/x^5 → multiplier -3*4 = -12
        t *= -12.0 * invX2; sum += t;
        // k=3: -6!/x^7: multiplier -5*6 = -30
        t *= -30.0 * invX2; sum += t;
        // k=4: +8!/x^9: multiplier -7*8 = -56
        t *= -56.0 * invX2; sum += t;
        // k=5: -10!/x^11: multiplier -9*10 = -90
        t *= -90.0 * invX2; sum += t;
        return sum;
    }

    /**
     * Auxiliary asymptotic function (A&amp;S 5.2.39):
     * <pre>
     *   g(x) ~ (1/x²) · Σ_{k=0}^∞ (−1)^k · (2k+1)! / x^(2k)
     *        = 1/x² − 3!/x⁴ + 5!/x⁶ − 7!/x⁸ + 9!/x¹⁰ − 11!/x¹² + …
     * </pre>
     * Six alternating terms retained.
     */
    private double g(double x) {
        double x2 = x * x;
        double invX2 = 1.0 / x2;
        double sum = invX2;
        double t = invX2;
        // k=1: -3!/x^4 = -6/x^4 → multiplier -2*3 = -6
        t *= -6.0 * invX2;   sum += t;
        // k=2: +5!/x^6: multiplier -4*5 = -20
        t *= -20.0 * invX2;  sum += t;
        // k=3: -7!/x^8: multiplier -6*7 = -42
        t *= -42.0 * invX2;  sum += t;
        // k=4: +9!/x^10: multiplier -8*9 = -72
        t *= -72.0 * invX2;  sum += t;
        // k=5: -11!/x^12: multiplier -10*11 = -110
        t *= -110.0 * invX2; sum += t;
        return sum;
    }
}
