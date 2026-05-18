package com.jantenna.physics;

import com.jantenna.math.Integrator;
import com.jantenna.physics.Impedance;
import com.jantenna.physics.MutualImpedanceParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the MutualImpedanceCalculator.
 * These tests use a mocked Integrator to verify the calculator's logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class MutualImpedanceCalculatorTest {

    private static final double DELTA = 1e-9;

    @Mock
    private Integrator integrator;

    private MutualImpedanceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MutualImpedanceCalculator(integrator);
    }

    @Test
    @DisplayName("Calculates impedance correctly when y0 is above the threshold")
    void testCalculate_withReactance() {
        // Arrange: Use parameters from a known test case
        final MutualImpedanceParameters params = new MutualImpedanceParameters(
                1.0, 0.866, 0.25, -0.5, 0.5236, 0.0, 0.0, 0.1, 0.5, 240.0
        );

        // Mock the integrator to return pre-calculated values for the integrals
        when(integrator.integrate(any(SafeReactFunction.class), eq(-0.25), eq(0.25))).thenReturn(0.04916);
        when(integrator.integrate(any(SafeResistFunction.class), eq(-0.25), eq(0.25))).thenReturn(0.05416);

        // Act
        final Impedance impedance = calculator.calculate(params);

        // Assert: The scaling factor is -0.1 * vofl (240.0) = -24.0
        // Resistance = -24.0 * 0.05416 = -1.29984
        // Reactance = -24.0 * 0.04916 = -1.17984
        assertEquals(-1.29984, impedance.real(), DELTA, "Resistance should match the expected scaled value");
        assertEquals(-1.17984, impedance.imaginary(), DELTA, "Reactance should match the expected scaled value");

        // Verify that the integrator was called for both functions
        verify(integrator, times(1)).integrate(any(SafeReactFunction.class), eq(-0.25), eq(0.25));
        verify(integrator, times(1)).integrate(any(SafeResistFunction.class), eq(-0.25), eq(0.25));
    }

    @Test
    @DisplayName("Skips reactance calculation when y0 is below the threshold")
    void testCalculate_withoutReactance() {
        // Arrange: Use a y0 value below the 0.005 threshold
        final MutualImpedanceParameters params = new MutualImpedanceParameters(
                1.0, 0.866, 0.25, -0.5, 0.5236, 0.0, 0.0, 0.004, 0.5, 240.0
        );

        // Mock the integrator to return a value only for the resistance integral
        when(integrator.integrate(any(SafeResistFunction.class), eq(-0.25), eq(0.25))).thenReturn(0.05416);

        // Act
        final Impedance impedance = calculator.calculate(params);

        // Assert: Resistance should be calculated, but reactance should be 0
        // Resistance = -24.0 * 0.05416 = -1.29984
        assertEquals(-1.29984, impedance.real(), DELTA, "Resistance should still be calculated");
        assertEquals(0.0, impedance.imaginary(), DELTA, "Reactance should be zero");

        // Verify that the integrator was only called for the ResistFunction
        verify(integrator, times(0)).integrate(any(SafeReactFunction.class), anyDouble(), anyDouble());
        verify(integrator, times(1)).integrate(any(SafeResistFunction.class), eq(-0.25), eq(0.25));
    }
}