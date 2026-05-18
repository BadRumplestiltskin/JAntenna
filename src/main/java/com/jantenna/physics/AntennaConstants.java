package com.jantenna.physics;

import com.jantenna.math.MathConstants;

/**
 * Centralized constants for antenna physics and modeling calculations.
 * Consolidates magic numbers from antenna-related services and utilities.
 */
public final class AntennaConstants {

    private AntennaConstants() {
    }

    // ===== Antenna Physics Constants =====

    /**
     * Impedance ratio factor used in antenna impedance calculations.
     * Derived from: sqrt(2.0) / 4680.0
     */
    public static final double IMPEDANCE_RATIO_FACTOR = Math.sqrt(2.0) / 4680.0;

    /**
     * Euler–Mascheroni constant γ. Redirected to the full-precision
     * {@link MathConstants#EULER_MASCHERONI} — this alias is retained for
     * backward compatibility with existing antenna calculators.
     */
    public static final double EULER_MASCHERONI = MathConstants.EULER_MASCHERONI;

    /**
     * Default gain floor (dB) for antenna calculations.
     * Used when antenna gain calculations produce invalid or degenerate results.
     */
    public static final double GAIN_FLOOR_DB = -30.0;

    // ===== Antenna Modeling Constants =====

    /**
     * Number of interpolation points for antenna gain patterns.
     * Defines the granularity of antenna gain patterns (0° to 90° in steps).
     */
    public static final int GAIN_PATTERN_POINTS = 91;

    /**
     * Standard interpolation angles (degrees) for antenna gain pattern definition.
     * Nineteen uniformly-spaced elevation angles from 0° to 90° in 5° steps,
     * matching the VOACAP .ant file format used by
     * {@link jvoacap legacy readant.for}.
     */
    public static final double[] INTERPOLATION_ANGLES = {
            0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90
    };

    /** Length of {@link #INTERPOLATION_ANGLES}. Sample .ant files must supply this many gain values. */
    public static final int INTERPOLATION_ANGLE_COUNT = INTERPOLATION_ANGLES.length;

    // ===== File Constants =====

    /**
     * Standard file extension for antenna definition files.
     */
    public static final String ANTENNA_FILE_EXTENSION = ".dat";
}
