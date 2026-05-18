package com.jantenna.math;

import com.jantenna.math.GaussianQuadratureRule;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.IterativeLegendreGaussIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;

import java.util.Objects;

/**
 * An {@link Integrator} implementation backed by Commons-Math's
 * {@link IterativeLegendreGaussIntegrator}.
 *
 * <p><b>Spring registration.</b> Not {@code @Component}-scanned —
 * {@code AppConfig.integrator()} chooses between this and
 * {@link VectorizedQuadratureIntegrator} via the
 * {@code integrator.implementation} property. Scanning both as components
 * would double-register the {@code Integrator} bean.</p>
 */
public class ApacheCommonsIntegrator implements Integrator {

    private static final int DEFAULT_MAX_EVAL = 1000;
    private final UnivariateIntegrator integrator;

    /**
     * Constructs the integrator, receiving its rule via dependency injection.
     * @param rule the rule to use, provided by Spring's application context.
     */
    public ApacheCommonsIntegrator(GaussianQuadratureRule rule) {
        Objects.requireNonNull(rule, "GaussianQuadratureRule must not be null");
        int n = rule.getNumberOfPoints();
        this.integrator = new IterativeLegendreGaussIntegrator(
                n,
                1e-12,
                1e-12
        );
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
        return integrator.integrate(DEFAULT_MAX_EVAL, function, lowerBound, upperBound);
    }
}