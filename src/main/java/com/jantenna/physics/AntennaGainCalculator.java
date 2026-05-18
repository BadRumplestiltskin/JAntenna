package com.jantenna.physics;

import com.jantenna.physics.MufParameters;
import org.apache.commons.math3.complex.Complex;

/**
 * Strategy interface for antenna gain calculations.
 * Implementations compute antenna gain in dB for different antenna types (KOP codes).
 */
public interface AntennaGainCalculator {

    /**
     * Calculates antenna gain for the given propagation and antenna parameters.
     *
     * @param params Complete antenna and propagation parameters (includes KOP code, dimensions, etc.)
     * @param q      sin(takeoff_angle) - Elevation angle component
     * @param t      cos(takeoff_angle) - Elevation angle component
     * @param wave   Wavelength in the medium
     * @param qpar   Fresnel reflection coefficient (parallel polarization)
     * @param qper   Fresnel reflection coefficient (perpendicular polarization)
     * @param dif    Complex ground parameter (relative permittivity - j*conductivity/frequency)
     * @return Antenna gain in dB
     */
    double calculateGain(MufParameters params, double q, double t, double wave,
                        Complex qpar, Complex qper, Complex dif);

    /**
     * Returns the antenna type code (KOP) this calculator handles.
     * KOP codes: 2=Monopole, 3=Dipole, 13=LogPeriodic
     *
     * @return KOP code as integer
     */
    int getAntennaType();
}
