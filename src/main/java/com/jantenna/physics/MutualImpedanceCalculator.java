// src/main/java/com/voacap/antenna/MutualImpedanceCalculator.java
package com.jantenna.physics;

import com.jantenna.math.Integrator;
import com.jantenna.physics.Impedance;
import com.jantenna.physics.MutualImpedanceParameters;

import java.util.Objects;

/**
 * Calculates the mutual impedance between two antennas using a numerical
 * integrator for both resistive and reactive components.
 * <p>
 * Reactive term is omitted when the shunt admittance Y₀ is below a small
 * threshold. Scaling and sign conventions follow ITU-R P.533 guidelines.
 */
public final class MutualImpedanceCalculator {

    /**
     * Scaling factor to convert the dimensionless integral into impedance
     * units.
     */
    private static final double IMPEDANCE_SCALING_FACTOR = -0.1;

    /**
     * Below this admittance (S), the reactive component is treated as zero.
     */
    private static final double Y0_THRESHOLD = 0.005;

    private final Integrator integrator;

    /**
     * @param integrator the numerical integrator to use (must not be null)
     * @throws NullPointerException if {@code integrator} is null
     */
    public MutualImpedanceCalculator(Integrator integrator) {
        this.integrator = Objects.requireNonNull(integrator, "integrator must not be null");
    }

    /**
     * Computes the mutual impedance for the given antenna parameters.
     *
     * @param params the set of mutual-impedance parameters (must not be null)
     * @return an {@link Impedance} containing (resistance, reactance)
     * @throws NullPointerException if {@code params} is null
     * @throws IllegalArgumentException if any parameter is non-finite or out of
     * expected range
     */
    public Impedance calculate(MutualImpedanceParameters params) {
        Objects.requireNonNull(params, "params must not be null");

        double h2 = params.h2();
        double y0 = params.y0();
        double vofl = params.vofl();

        if (!Double.isFinite(h2) || h2 < 0) {
            throw new IllegalArgumentException("h2 must be finite ≥ 0");
        }
        if (!Double.isFinite(y0) || y0 < 0) {
            throw new IllegalArgumentException("y0 must be finite ≥ 0");
        }
        if (!Double.isFinite(vofl)) {
            throw new IllegalArgumentException("vofl must be finite");
        }

        // overall scaling including sign
        double scalingFactor = IMPEDANCE_SCALING_FACTOR * vofl;

        // wrap these in null-safe functions of z
        var reactFunc = new SafeReactFunction(params);
        var resistFunc = new SafeResistFunction(params);

        // reactive part only if Y₀ exceeds threshold
        double reactance = 0.0;
        if (y0 > Y0_THRESHOLD) {
            reactance = scalingFactor * integrator.integrate(reactFunc, -h2, h2);
        }

        // always compute resistance
        double resistance = scalingFactor * integrator.integrate(resistFunc, -h2, h2);

        return new Impedance(resistance, reactance);
    }

}
