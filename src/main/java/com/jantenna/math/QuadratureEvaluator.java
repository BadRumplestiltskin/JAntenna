package com.jantenna.math;

/**
 * Functional interface for batch evaluation of quadrature points.
 * <p>
 * This interface enables vectorized (batch) evaluation of functions at multiple
 * quadrature points simultaneously, allowing for SIMD optimization and reduced
 * JVM call overhead.
 * <p>
 * Implementations should evaluate the integrand at all provided points and return
 * the results as a double array with the same length as the input.
 * <p>
 * Example:
 * <pre>
 *   QuadratureEvaluator evaluator = (points) -> {
 *       double[] results = new double[points.length];
 *       for (int i = 0; i &lt; points.length; i++) {
 *           results[i] = functionValue(points[i]);
 *       }
 *       return results;
 *   };
 * </pre>
 */
@FunctionalInterface
public interface QuadratureEvaluator {

    /**
     * Evaluate the integrand at multiple points (typically 48 quadrature points).
     * <p>
     * Implementations are encouraged to:
     * <ul>
     *   <li>Evaluate all points in a single batch (not point-by-point)</li>
     *   <li>Leverage SIMD instructions for mathematical operations</li>
     *   <li>Minimize intermediate object allocation</li>
     *   <li>Maintain numerical accuracy to at least 1e-12</li>
     * </ul>
     *
     * @param points the array of x-coordinates where the integrand is evaluated
     * @return an array of integrand values at each point, same length as {@code points}
     * @throws NullPointerException if {@code points} is null
     * @throws IllegalArgumentException if the result array length doesn't match input
     */
    double[] evaluateBatch(double[] points);
}
