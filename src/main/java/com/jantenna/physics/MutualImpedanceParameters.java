package com.jantenna.physics;

/**
 * A record to encapsulate the parameters required for the mutual impedance
 * calculation. This replaces the legacy FORTRAN COMMON /MUT/ block with a
 * type-safe, immutable data structure.
 *
 * @param cfac Dimensionless scaling factor on the integrand (typically 1.0).
 * @param ct Cosine of the angle between the dipole element axes.
 * @param h2 Element half-length in wavelengths (0.25 for a half-wave dipole).
 * @param prod1 Negative sine of the angle between the dipole element axes.
 * @param rhi2 Tilt angle (radians). Currently 0 for all call sites; reserved
 *             for tilted-array extensions — not a stub, not populated by any
 *             existing consumer.
 * @param r21 Mutual-resistance output slot, written by downstream integrator
 *            fills in the FORTRAN Z-matrix chain. Not used as an input —
 *            initialise to 0.
 * @param x21 Mutual-reactance output slot, analogous to {@link #r21()}.
 *            Initialise to 0.
 * @param y0 Lateral offset between dipole centres (wavelengths).
 * @param z0 Axial offset between dipole centres (wavelengths).
 * @param vofl Frequency-dependent scaling factor (≈ 240 π for VOACAP units).
 */
public record MutualImpedanceParameters(
        double cfac,
        double ct,
        double h2,
        double prod1,
        double rhi2,
        double r21,
        double x21,
        double y0,
        double z0,
        double vofl
) {
    /** Creates parameters for a single dipole element with all geometric defaults at unity/zero. */
    public static MutualImpedanceParameters forElement(double h2, double y0, double r21) {
        return new MutualImpedanceParameters(1.0, 1.0, h2, 0.0, 0.0, r21, 0.0, y0, 0.0, 1.0);
    }
}