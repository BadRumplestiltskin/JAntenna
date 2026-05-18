package com.jantenna.math;

import com.jantenna.math.ComplexMatrix;
import java.util.Objects;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.linear.FieldLUDecomposition;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;

/**
 * Utility class for real and complex matrix operations.
 * <p>
 * Provides standard inversion (LU Decomposition) for real matrices and
 * Field-based inversion for Complex matrices, mapping to the CMPINV
 * subroutine in VOACAP.
 * <p>
 * Thread-Safety: Thread-safe (static methods).
 */
public final class MatrixOperations {

    private MatrixOperations() {
        // Prevent instantiation
    }

    /**
     * Inverts a real, square matrix using LU decomposition.
     *
     * @param matrixData the n x n matrix to invert.
     * @return the inverted matrix.
     * @throws IllegalArgumentException if matrix is singular or invalid.
     */
    public static double[][] invert(double[][] matrixData) {
        Objects.requireNonNull(matrixData, "matrixData must not be null");
        int n = matrixData.length;
        if (n == 0) {
            throw new IllegalArgumentException("matrixData must have at least one row");
        }

        RealMatrix matrix = MatrixUtils.createRealMatrix(matrixData);
        LUDecomposition lu = new LUDecomposition(matrix);
        if (FastMath.abs(lu.getDeterminant()) < Double.MIN_VALUE) {
            throw new IllegalArgumentException("matrix is singular");
        }
        return lu.getSolver().getInverse().getData();
    }

    /**
     * Inverts a complex square matrix.
     * <p>
     * This provides the functionality of the CMPINV subroutine in VOACAP,
     * solving the system (TX + jTY)^-1 required for Log-Periodic currents.
     *
     * @param input Record containing the real and imaginary component arrays.
     * @return A new ComplexMatrix containing the inverted real and imaginary parts.
     */
    public static ComplexMatrix invertComplex(ComplexMatrix input) {
        Objects.requireNonNull(input, "Input matrix must not be null");
        double[][] real = input.real();
        double[][] imag = input.imag();
        int n = real.length;

        if (n != imag.length || n != real[0].length) {
            throw new IllegalArgumentException("Matrix dimensions must be square and consistent");
        }

        FieldMatrix<Complex> matrix = MatrixUtils.createFieldMatrix(ComplexField.getInstance(), n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix.setEntry(i, j, new Complex(real[i][j], imag[i][j]));
            }
        }

        FieldLUDecomposition<Complex> lu = new FieldLUDecomposition<>(matrix);
        // FieldLUDecomposition handles singularity checks via the solver
        FieldMatrix<Complex> inverse = lu.getSolver().getInverse();

        double[][] outReal = new double[n][n];
        double[][] outImag = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Complex c = inverse.getEntry(i, j);
                outReal[i][j] = c.getReal();
                outImag[i][j] = c.getImaginary();
            }
        }

        return new ComplexMatrix(outReal, outImag);
    }

    /**
     * Inverts a single complex number.
     * Computes 1/(real + i*imag).
     *
     * @param real The real part of the complex number.
     * @param imag The imaginary part of the complex number.
     * @return A Complex object representing 1/(real + i*imag).
     * @throws IllegalArgumentException if the complex number is zero-valued or non-finite.
     */
    public static Complex invertComplex(double real, double imag) {
        // Check for finite values
        if (!Double.isFinite(real) || !Double.isFinite(imag)) {
            throw new IllegalArgumentException("real and imag must be finite");
        }

        // Check for zero-valued complex number
        if (real == 0.0 && imag == 0.0) {
            throw new IllegalArgumentException("Cannot invert zero-valued complex number");
        }

        // Compute 1/(real + i*imag) = (real - i*imag) / (real^2 + imag^2)
        double denominator = real * real + imag * imag;
        double resultReal = real / denominator;
        double resultImag = -imag / denominator;

        return new Complex(resultReal, resultImag);
    }
}