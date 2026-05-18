package com.jantenna.math;

import org.apache.commons.math3.util.FastMath;

/**
 * A utility class to hold global, application-wide mathematical constants. This
 * class cannot be instantiated.
 */
public final class MathConstants {

    private MathConstants() {
        // Private constructor to prevent instantiation
    }

    /**
     * The value of Pi.
     */
    public static final double PI = FastMath.PI;

    /**
     * The value of 2 * Pi.
     */
    public static final double PI2 = 2.0 * PI;

    /**
     * Constant for converting degrees to radians.
     */
    public static final double DEGREES_TO_RADIANS = PI / 180.0;

    /**
     * Constant for converting radians to degrees.
     */
    public static final double RADIANS_TO_DEGREES = 180.0 / PI;

    /**
     * Euler–Mascheroni constant γ (A&amp;S 4.1.32) to full double precision.
     * Single source of truth — both {@code SpecialFunctions} and
     * {@code AntennaConstants} (deprecated alias) delegate to this value.
     */
    public static final double EULER_MASCHERONI = 0.57721566490153286060;

    /** π / 2 (90° in radians). */
    public static final double PI_OVER_TWO = PI / 2.0;

    /**
     * VOACAP Fortran Earth radius (km). Legacy value {@code 6370.0} used
     * throughout the original VOACAP source (e.g. {@code RZ} in ABSORP.FOR,
     * CURMUF.FOR, SYSSY.FOR). Java ports that must match Fortran output
     * bit-for-bit (absorption, MUF, system loss) must use this value.
     */
    public static final double EARTH_RADIUS_FORTRAN_KM = 6370.0;

    /**
     * Standard mean Earth radius (km). WGS-84-style value {@code 6371.0}
     * used by services that are not bound to Fortran-identical output
     * (geometry, path, bending, convergence, card parser).
     */
    public static final double EARTH_RADIUS_KM = 6371.0;
}
