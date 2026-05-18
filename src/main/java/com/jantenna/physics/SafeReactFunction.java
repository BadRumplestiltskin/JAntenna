// src/main/java/com/voacap/antenna/SafeReactFunction.java
package com.jantenna.physics;

import com.jantenna.math.GaussianQuadratureRule;
import com.jantenna.math.QuadratureEvaluator;

import com.jantenna.physics.MutualImpedanceParameters;
import org.apache.commons.math3.analysis.UnivariateFunction;

import java.util.Objects;

import static com.jantenna.math.MathConstants.PI2;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

/**
 * Reactive-part integrand for the mutual-impedance Gaussian integral. Direct
 * port of {@code REACT.FOR}:
 *
 * <pre>
 *   REACT(S) = { [(CR1·CA1 + CR2·CA2 − FACX·CA)·SY] / TERM
 *              +  (FACX − CR1 − CR2)·SZ }
 *            · sin(PI2·(H2 − |S|)) / S
 * </pre>
 *
 * <p>The {@code 1/S} factor is integrable: the numerator is {@code O(S)} as
 * {@code S → 0} because {@code SY}, {@code SZ} both scale with {@code S}, so
 * the integrand has a removable singularity. {@link #value(double)} returns
 * the analytic L'Hôpital limit when {@code S = 0}, so the function is safe
 * for any quadrature rule including rules that place an abscissa at the
 * origin.</p>
 */
public final class SafeReactFunction implements UnivariateFunction, QuadratureEvaluator {

    private final MutualImpedanceParameters params;

    public SafeReactFunction(MutualImpedanceParameters params) {
        this.params = Objects.requireNonNull(params, "params must not be null");
    }

    /**
     * Reactive integrand at point {@code s}. Matches {@code REACT.FOR} line
     * 28-29 including the outer {@code sin(PI2·(H2 − |S|))} factor.
     * @param s
     * @return 
     */
    @Override
    public double value(double s) {
        if (s == 0.0) {
            return limitAtZero();
        }

        final double sz = s * params.ct();
        final double sy = s * params.prod1();
        final double term = params.y0() + sy;
        final double rho2 = term * term;
        final double ca = params.z0() + sz;

        double r = sqrt(rho2 + ca * ca);
        double r1 = sqrt(rho2 + (ca + params.h2()) * (ca + params.h2()));
        double r2 = sqrt(rho2 + (ca - params.h2()) * (ca - params.h2()));

        double cr  = cos(PI2 * r)  / r;
        double cr1 = cos(PI2 * r1) / r1;
        double cr2 = cos(PI2 * r2) / r2;
        double facx = 2.0 * params.cfac() * cr;

        double ca1 = ca + params.h2();
        double ca2 = ca - params.h2();

        double inner = ((cr1 * ca1 + cr2 * ca2 - facx * ca) * sy) / term
                     + (facx - cr1 - cr2) * sz;

        return inner * sin(PI2 * (params.h2() - abs(s))) / s;
    }

    /**
     * L'Hôpital limit of the integrand at {@code s = 0}. The outer
     * {@code sin(PI2·(H2 − |S|))} tends to {@code sin(PI2·H2)}; the bracketed
     * inner expression scales linearly with {@code S} through both {@code SY}
     * and {@code SZ}, so dividing by {@code S} yields a finite value:
     *
     * <pre>
     *   lim_{S→0} REACT(S) = { A·PROD1/Y0 + B·CT } · sin(PI2·H2)
     *     A = CR1·CA1 + CR2·CA2 − FACX·CA    (evaluated at S=0)
     *     B = FACX − CR1 − CR2               (evaluated at S=0)
     * </pre>
     *
     * <p>Requires {@code Y0 > 0}; {@link com.jantenna.physics.MutualImpedanceCalculator}
     * guards the reactance integral behind a {@code Y0 > 0.005} threshold, so
     * the limit is never evaluated when {@code Y0} is degenerate.</p>
     */
    private double limitAtZero() {
        final double h2 = params.h2();
        final double y0 = params.y0();
        final double z0 = params.z0();
        if (y0 == 0.0) {
            return 0.0; // caller's Y0 threshold suppresses this path
        }

        double ca = z0;
        double ca1 = z0 + h2;
        double ca2 = z0 - h2;
        double r  = sqrt(y0 * y0 + ca  * ca);
        double r1 = sqrt(y0 * y0 + ca1 * ca1);
        double r2 = sqrt(y0 * y0 + ca2 * ca2);
        double cr  = cos(PI2 * r)  / r;
        double cr1 = cos(PI2 * r1) / r1;
        double cr2 = cos(PI2 * r2) / r2;
        double facx = 2.0 * params.cfac() * cr;

        double a = cr1 * ca1 + cr2 * ca2 - facx * ca;
        double b = facx - cr1 - cr2;

        return (a * params.prod1() / y0 + b * params.ct()) * sin(PI2 * h2);
    }

    @Override
    public double[] evaluateBatch(double[] points) {
        Objects.requireNonNull(points, "points must not be null");
        double[] results = new double[points.length];

        final double ct    = params.ct();
        final double prod1 = params.prod1();
        final double y0    = params.y0();
        final double z0    = params.z0();
        final double h2    = params.h2();
        final double cfac  = params.cfac();

        for (int i = 0; i < points.length; i++) {
            double s = points[i];

            if (s == 0.0) {
                results[i] = limitAtZero();
                continue;
            }

            final double sz   = s * ct;
            final double sy   = s * prod1;
            final double term = y0 + sy;
            final double rho2 = term * term;
            final double ca   = z0 + sz;
            final double ca1  = ca + h2;
            final double ca2  = ca - h2;

            double r  = sqrt(rho2 + ca  * ca);
            double r1 = sqrt(rho2 + ca1 * ca1);
            double r2 = sqrt(rho2 + ca2 * ca2);

            double cr  = cos(PI2 * r)  / r;
            double cr1 = cos(PI2 * r1) / r1;
            double cr2 = cos(PI2 * r2) / r2;
            double facx = 2.0 * cfac * cr;

            double inner = ((cr1 * ca1 + cr2 * ca2 - facx * ca) * sy) / term
                         + (facx - cr1 - cr2) * sz;

            results[i] = inner * sin(PI2 * (h2 - abs(s))) / s;
        }

        return results;
    }
}
