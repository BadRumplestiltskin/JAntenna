package com.jantenna.math;

import com.jantenna.math.GaussianQuadratureRule;
import com.jantenna.math.QuadratureEvaluator;
import org.apache.commons.math3.analysis.UnivariateFunction;

import java.util.Objects;

/**
 * Gauss–Legendre quadrature integrator using a batched-evaluation strategy
 * that stays JIT-auto-vectorisation friendly.
 *
 * <p><b>Algorithm.</b> A quadrature rule supplies n abscissas {@code xi[i]} and
 * weights {@code w[i]} over [−1, 1]. To integrate ƒ on [a, b] we apply the
 * affine transform {@code t = scale·xi + offset} with
 * {@code scale = (b−a)/2}, {@code offset = (b+a)/2}, then evaluate
 * {@code I ≈ scale · Σ w[i]·ƒ(t[i])}.</p>
 *
 * <p><b>Performance note — Vector API (JEP 338) was benchmarked and removed.</b>
 * An earlier version of this class used {@code DoubleVector} with
 * {@code SPECIES_PREFERRED}. Wall-clock measurements on Apple Silicon
 * (NEON, 2-lane doubles) showed a 10× <i>regression</i> on polynomial
 * integrands and ~20% regression on transcendentals: per-iteration
 * {@code DoubleVector} allocations outweighed the tiny SIMD win on a
 * 48-element loop. The plain scalar form below is what HotSpot C2
 * actually turns into NEON instructions through auto-vectorisation,
 * and it's measurably faster on the target host.</p>
 *
 * <p>The Vector API path was removed entirely along with the
 * {@code --add-modules jdk.incubator.vector} build flag under TD-43
 * (Java 21 LTS support).  Re-introducing it on hosts with AVX-512 /
 * SVE remains an option — the measurement harness is preserved at
 * {@code src/test/java/com/voacap/math/VectorizedQuadratureIntegratorBenchmark.java}.</p>
 *
 * <p><b>Where the real win lives.</b> The 48-point loop is dominated by
 * the integrand evaluation (transcendentals inside the antenna impedance
 * kernels). Integrands that implement {@link QuadratureEvaluator} can
 * vectorise their own batched evaluation — that is the productive place
 * to add SIMD, not this outer driver.</p>
 *
 * <p><b>Thread safety.</b> Stateless; the class holds only the immutable
 * {@link GaussianQuadratureRule}. Safe to share across threads and to invoke
 * from a ForkJoinPool.</p>
 *
 * <p><b>Spring registration.</b> Not {@code @Component}-scanned —
 * {@code AppConfig.integrator()} chooses between this and
 * {@code ApacheCommonsIntegrator} via the
 * {@code integrator.implementation} property. Scanning both as components
 * would double-register the {@code Integrator} bean.</p>
 *
 * @see <a href="https://openjdk.org/jeps/338">JEP 338: Vector API (Incubator)</a>
 */
public class VectorizedQuadratureIntegrator implements Integrator {

    private final GaussianQuadratureRule rule;

    public VectorizedQuadratureIntegrator(GaussianQuadratureRule rule) {
        this.rule = Objects.requireNonNull(rule, "GaussianQuadratureRule must not be null");
    }

    @Override
    public double integrate(UnivariateFunction function, double lowerBound, double upperBound) {
        Objects.requireNonNull(function, "function must not be null");
        if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound)) {
            throw new IllegalArgumentException("Integration bounds must be finite");
        }
        if (lowerBound >= upperBound) {
            throw new IllegalArgumentException("lowerBound must be less than upperBound");
        }

        final double[] abscissas = rule.getAbscissas();
        final double[] weights = rule.getWeights();
        final int n = abscissas.length;

        final double scale = (upperBound - lowerBound) / 2.0;
        final double offset = (upperBound + lowerBound) / 2.0;

        // Affine transform — auto-vectorised by C2.
        final double[] transformedPoints = new double[n];
        for (int i = 0; i < n; i++) {
            transformedPoints[i] = scale * abscissas[i] + offset;
        }

        // Batched integrand evaluation — the QuadratureEvaluator shortcut
        // is the place to vectorise, not this loop.
        final double[] functionValues = evaluateBatch(function, transformedPoints);

        // Weighted sum — also auto-vectorised by C2.
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += weights[i] * functionValues[i];
        }

        return sum * scale;
    }

    private static double[] evaluateBatch(UnivariateFunction function, double[] points) {
        if (function instanceof QuadratureEvaluator vectorized) {
            return vectorized.evaluateBatch(points);
        }
        double[] results = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            results[i] = function.value(points[i]);
        }
        return results;
    }
}
