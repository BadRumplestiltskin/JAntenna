package com.jantenna.physics;

import org.apache.commons.math3.complex.Complex;

/**
 * A record to represent the complex impedance of an antenna. It encapsulates
 * the real (resistance) and imaginary (reactance) components.
 *
 * @param real The real part of the impedance (resistance), in ohms.
 * @param imaginary The imaginary part of the impedance (reactance), in ohms.
 */
public record Impedance(double real, double imaginary) {

    public Complex toComplex() {
        return new Complex(real, imaginary);
    }
}
