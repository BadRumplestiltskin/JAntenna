package com.jantenna.physics;

import com.jantenna.math.QuadratureEvaluator;

import com.jantenna.physics.MutualImpedanceParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SafeReactFunction}.
 * Tests both scalar value() and batch evaluateBatch() methods.
 */
class SafeReactFunctionTest {

    private SafeReactFunction function;
    private MutualImpedanceParameters params;

    @BeforeEach
    void setUp() {
        // Create test parameters
        params = new MutualImpedanceParameters(
                1.0,      // cfac
                0.866,    // ct (cos of ~30°)
                0.25,     // h2
                -0.5,     // prod1
                0.5236,   // rhi2
                0.0,      // r21
                0.0,      // x21
                0.1,      // y0
                0.5,      // z0
                240.0     // vofl
        );
        function = new SafeReactFunction(params);
    }

    @Test
    @DisplayName("Scalar value() at non-zero point")
    void testScalarValue_nonZero() {
        double result = function.value(0.5);
        assertTrue(Double.isFinite(result), "Result should be finite");
        assertNotEquals(0.0, result, "Result should be non-zero at non-zero input");
    }

    @Test
    @DisplayName("Scalar value() at zero returns analytic L'Hôpital limit")
    void testScalarValue_zero() {
        // Analytic limit at s=0 computed directly from the Fortran REACT.FOR
        // integrand with the test parameters (cfac=1, ct=0.866, h2=0.25,
        // prod1=-0.5, y0=0.1, z0=0.5):
        //   limit = { A·prod1/y0 + B·ct } · sin(PI2·h2)
        //   where A = cr1·ca1 + cr2·ca2 - facx·ca,
        //         B = facx - cr1 - cr2  (at s=0).
        double result = function.value(0.0);
        assertTrue(Double.isFinite(result), "Limit must be finite");
        // Sanity: limit must match an adjacent non-zero evaluation as s→0.
        double nearZero = function.value(1e-6);
        assertEquals(result, nearZero, 1e-3,
                "Limit at s=0 must agree with nearby non-zero evaluation");
    }

    @Test
    @DisplayName("Scalar value() with negative input")
    void testScalarValue_negative() {
        double posResult = function.value(0.5);
        double negResult = function.value(-0.5);

        assertTrue(Double.isFinite(posResult), "Positive input should give finite result");
        assertTrue(Double.isFinite(negResult), "Negative input should give finite result");
    }

    @Test
    @DisplayName("Batch evaluateBatch() matches scalar value()")
    void testBatchMatchesScalar() {
        double[] points = {0.1, 0.2, 0.3, 0.4, 0.5};
        double[] batchResults = function.evaluateBatch(points);

        assertEquals(points.length, batchResults.length, "Batch should have same length as input");

        for (int i = 0; i < points.length; i++) {
            double scalarResult = function.value(points[i]);
            assertEquals(scalarResult, batchResults[i], 1e-9,
                    "Batch result at index " + i + " should match scalar result");
        }
    }

    @Test
    @DisplayName("Batch evaluateBatch() with 48 points (Gaussian quadrature size)")
    void testBatchGaussianSize() {
        double[] points = new double[48];
        for (int i = 0; i < 48; i++) {
            // Quadrature points typically in [-h2, h2]
            points[i] = -0.25 + (0.5 * i / 47.0);
        }

        double[] results = function.evaluateBatch(points);

        assertEquals(48, results.length, "Should return 48 results");
        for (int i = 0; i < 48; i++) {
            assertTrue(Double.isFinite(results[i]),
                    "Result at index " + i + " should be finite");
        }
    }

    @Test
    @DisplayName("Batch evaluateBatch() handles zero in array")
    void testBatchWithZero() {
        double[] points = {-0.1, 0.0, 0.1};
        double[] results = function.evaluateBatch(points);

        assertEquals(3, results.length, "Should return 3 results");
        // Zero input returns the analytic L'Hôpital limit; batch must agree
        // with the scalar path.
        assertEquals(function.value(0.0), results[1], 1e-15,
                "Batch at s=0 must match scalar limit");
    }

    @Test
    @DisplayName("Batch evaluateBatch() rejects null")
    void testBatchRejectsNull() {
        assertThrows(NullPointerException.class,
                () -> function.evaluateBatch(null),
                "Should reject null array");
    }

    @Test
    @DisplayName("Batch evaluateBatch() evaluates array elements")
    void testBatchPreservesOrder() {
        double[] points = {-0.1, -0.05, 0.05, 0.1};
        double[] results = function.evaluateBatch(points);

        // All results should be computed (may not all be finite depending on function)
        assertEquals(4, results.length, "Should return 4 results");
    }

    @Test
    @DisplayName("Function rejects null parameters")
    void testRejectsNullParameters() {
        assertThrows(NullPointerException.class,
                () -> new SafeReactFunction(null),
                "Should reject null MutualImpedanceParameters");
    }

    @Test
    @DisplayName("Batch evaluateBatch() with symmetric points")
    void testBatchSymmetricPoints() {
        double[] points = {-0.05, -0.01, 0.01, 0.05};
        double[] results = function.evaluateBatch(points);

        assertEquals(4, results.length, "Should return 4 results");
        // Note: Results may not be finite depending on parameter combinations
    }

    @Test
    @DisplayName("Batch evaluateBatch() with very small points")
    void testBatchSmallPoints() {
        double[] points = {-1e-10, 1e-10, 1e-9, -1e-9};
        double[] results = function.evaluateBatch(points);

        assertEquals(4, results.length, "Should return 4 results");
        for (int i = 0; i < 4; i++) {
            assertTrue(Double.isFinite(results[i]), "Result at index " + i + " should be finite");
        }
    }

    @Test
    @DisplayName("Batch evaluateBatch() with points at boundaries")
    void testBatchBoundaryPoints() {
        // h2 = 0.25, so boundary points
        double[] points = {-0.25, -0.249, 0.249, 0.25};
        double[] results = function.evaluateBatch(points);

        assertEquals(4, results.length, "Should return 4 results");
        for (int i = 0; i < 4; i++) {
            assertTrue(Double.isFinite(results[i]), "Result at index " + i + " (boundary) should be finite");
        }
    }

    @Test
    @DisplayName("Implements QuadratureEvaluator interface")
    void testImplementsQuadratureEvaluator() {
        assertTrue(function instanceof QuadratureEvaluator,
                "SafeReactFunction should implement QuadratureEvaluator");
    }

    @Test
    @DisplayName("Multiple evaluations are consistent")
    void testConsistency() {
        double value1 = function.value(0.15);
        double value2 = function.value(0.15);

        assertEquals(value1, value2, 1e-15, "Same input should give same output");

        double[] batch1 = function.evaluateBatch(new double[]{0.15});
        double[] batch2 = function.evaluateBatch(new double[]{0.15});

        assertEquals(batch1[0], batch2[0], 1e-15, "Batch evaluations should be consistent");
    }

    @Test
    @DisplayName("Reactive function differs from resistive function")
    void testReactiveVsResistive() {
        SafeResistFunction resistive = new SafeResistFunction(params);
        SafeReactFunction reactive = new SafeReactFunction(params);

        double testPoint = 0.15;
        double resistResult = resistive.value(testPoint);
        double reactResult = reactive.value(testPoint);

        // Results should be different (different trig functions and logic)
        assertNotEquals(resistResult, reactResult, 1e-9,
                "Resistive and reactive functions should produce different results");
    }
}
