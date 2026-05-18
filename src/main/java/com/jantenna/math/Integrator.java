// src/main/java/com/voacap/math/Integrator.java
package com.jantenna.math;

import org.apache.commons.math3.analysis.UnivariateFunction;

/**
 * Defines the contract for a numerical integration strategy.
 * <p>
 * This abstraction allows different integration methods (e.g., Gauss–Legendre,
 * Trapezoidal) to be used interchangeably throughout the application, adhering
 * to the Dependency Inversion Principle.
 */
public interface Integrator {

    /**
     * Integrates a function over a specified interval.
     *
     * @param function the function to integrate (must be non-null)
     * @param lowerBound the lower integration limit
     * @param upperBound the upper integration limit
     * @return the approximate value of the definite integral
     */
    double integrate(UnivariateFunction function, double lowerBound, double upperBound);
}
