package com.jantenna.math;

import java.util.Arrays;

/**
 * A record to encapsulate the real and imaginary parts of a complex matrix.
 *
 * @param real The 2D array representing the real components of the matrix.
 * @param imag The 2D array representing the imaginary components of the matrix.
 */
public record ComplexMatrix(double[][] real, double[][] imag) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComplexMatrix that)) return false;
        return Arrays.deepEquals(real, that.real)
                && Arrays.deepEquals(imag, that.imag);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.deepHashCode(real) + Arrays.deepHashCode(imag);
    }

    @Override
    public String toString() {
        return "ComplexMatrix[real=" + Arrays.deepToString(real)
                + ", imag=" + Arrays.deepToString(imag) + ']';
    }
}