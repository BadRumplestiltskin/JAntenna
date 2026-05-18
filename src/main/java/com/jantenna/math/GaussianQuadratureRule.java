package com.jantenna.math;

/**
 * A rule for Gaussian‑Legendre quadrature integration over the interval
 * {@code [-1, 1]}.
 * <p>
 * The integral of a function {@code f} over {@code [-1,1]} can be approximated
 * as:
 * <pre>
 *   ∫_{-1}^{1} f(x) dx ≈ Σ_{i=0}^{n-1} w[i] * f(x[i])
 * </pre> where {@code n} is the number of points, {@code x[i]} are the
 * abscissas (nodes), and {@code w[i]} are the weights.
 * <p>
 * Implementations must be immutable and thread‑safe. Each call to
 * {@link #getAbscissas()} or {@link #getWeights()} returns a fresh copy of the
 * underlying array.
 */
public interface GaussianQuadratureRule {

    /**
     * Returns the number of integration points (nodes) in this rule.
     *
     * @return the number of abscissas and weights
     */
    int getNumberOfPoints();

    /**
     * Returns a new array of abscissas (the x‑coordinates where the integrand
     * is evaluated).
     * <p>
     * The array length is {@link #getNumberOfPoints()}, and each value lies in
     * {@code [-1,1]}.
     *
     * @return a copy of the abscissas array
     */
    double[] getAbscissas();

    /**
     * Returns a new array of weights corresponding to each abscissa.
     * <p>
     * The array length is {@link #getNumberOfPoints()}, and the weights sum to
     * the length of the integration interval (typically 2 for {@code [-1,1]}).
     *
     * @return a copy of the weights array
     */
    double[] getWeights();
}
