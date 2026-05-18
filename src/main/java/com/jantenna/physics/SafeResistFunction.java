// src/main/java/com/voacap/antenna/SafeResistFunction.java
package com.jantenna.physics;

import com.jantenna.math.QuadratureEvaluator;

import com.jantenna.physics.MutualImpedanceParameters;
import org.apache.commons.math3.analysis.UnivariateFunction;

import java.util.Objects;

import static com.jantenna.math.MathConstants.PI2;
import static java.lang.Math.abs;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

/**
 * Resistive-part integrand for the mutual-impedance Gaussian integral. Direct
 * port of {@code RESIST.FOR}:
 *
 * <pre>
 *   RESIST(S) = { [(SR1·CA1 + SR2·CA2 − FACR·CA)·SY] / TERM
 *               +  (FACR − SR1 − SR2)·SZ }
 *             · sin(PI2·(H2 − |S|)) / S
 * </pre>
 *
 * <p>The singularity at {@code S = 0} is removable: the bracketed inner
 * expression scales as {@code O(S)} through {@code SY} and {@code SZ}, so
 * dividing by {@code S} yields a finite analytic limit
 * ({@link #limitAtZero()}).</p>
 */
public final class SafeResistFunction implements UnivariateFunction, QuadratureEvaluator {

    private final MutualImpedanceParameters params;

    public SafeResistFunction(MutualImpedanceParameters params) {
        this.params = Objects.requireNonNull(params, "params must not be null");
    }

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

        double r  = sqrt(rho2 + ca * ca);
        double r1 = sqrt(rho2 + (ca + params.h2()) * (ca + params.h2()));
        double r2 = sqrt(rho2 + (ca - params.h2()) * (ca - params.h2()));

        if (r == 0.0 || r1 == 0.0 || r2 == 0.0 || term == 0.0) return 0.0;

        double sr  = sin(PI2 * r)  / r;
        double sr1 = sin(PI2 * r1) / r1;
        double sr2 = sin(PI2 * r2) / r2;
        double facr = 2.0 * params.cfac() * sr;

        double ca1 = ca + params.h2();
        double ca2 = ca - params.h2();

        double inner = ((sr1 * ca1 + sr2 * ca2 - facr * ca) * sy) / term
                     + (facr - sr1 - sr2) * sz;

        return inner * sin(PI2 * (params.h2() - abs(s))) / s;
    }

    /**
     * L'Hôpital limit of the integrand at {@code s = 0}, same derivation as
     * {@code SafeReactFunction} but using {@code SR = sin(PI2·R)/R} instead of
     * {@code CR = cos(PI2·R)/R}.
     *
     * <p>If {@code Y0 = 0} the real-dipole ports call the resistance integral
     * with {@code TERM = S·PROD1}, producing a {@code 1/S} singularity that
     * cannot be removed. 48-point Gauss-Legendre never evaluates at the
     * origin, so this defensive branch returns 0 rather than diverging.</p>
     */
    private double limitAtZero() {
        final double h2 = params.h2();
        final double y0 = params.y0();
        final double z0 = params.z0();
        if (y0 == 0.0) {
            return 0.0;
        }

        double ca = z0;
        double ca1 = z0 + h2;
        double ca2 = z0 - h2;
        double r  = sqrt(y0 * y0 + ca  * ca);
        double r1 = sqrt(y0 * y0 + ca1 * ca1);
        double r2 = sqrt(y0 * y0 + ca2 * ca2);
        double sr  = sin(PI2 * r)  / r;
        double sr1 = sin(PI2 * r1) / r1;
        double sr2 = sin(PI2 * r2) / r2;
        double facr = 2.0 * params.cfac() * sr;

        double a = sr1 * ca1 + sr2 * ca2 - facr * ca;
        double b = facr - sr1 - sr2;

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

            if (r == 0.0 || r1 == 0.0 || r2 == 0.0 || term == 0.0) { results[i] = 0.0; continue; }

            double sr  = sin(PI2 * r)  / r;
            double sr1 = sin(PI2 * r1) / r1;
            double sr2 = sin(PI2 * r2) / r2;
            double facr = 2.0 * cfac * sr;

            double inner = ((sr1 * ca1 + sr2 * ca2 - facr * ca) * sy) / term
                         + (facr - sr1 - sr2) * sz;

            results[i] = inner * sin(PI2 * (h2 - abs(s))) / s;
        }

        return results;
    }
}
