package com.jantenna;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link GainTable} - the unified internal antenna
 * pattern representation.
 *
 * <p>Covers: construction validation, trilinear lookup with axis-
 * specific boundaries (clamp on freq/el, wrap on az), sentinel
 * encoding + propagation, fixed-point round-trip precision.</p>
 *
 * <p>Factory-method coverage lives in {@code GainTableFactoryTest}
 * (Phase 3) - this file exercises the record contract directly via
 * the raw constructor.</p>
 */
@DisplayName("GainTable record + lookup")
class GainTableTest {

    private static final double EPS = 0.005;

    @Nested
    @DisplayName("Construction validation")
    class Validation {
        @Test void rejectsNullArrays() {
            assertThrows(NullPointerException.class,
                () -> new GainTable(null, new double[]{0}, new double[]{0}, new short[]{0}));
            assertThrows(NullPointerException.class,
                () -> new GainTable(new double[]{1}, null, new double[]{0}, new short[]{0}));
            assertThrows(NullPointerException.class,
                () -> new GainTable(new double[]{1}, new double[]{0}, null, new short[]{0}));
            assertThrows(NullPointerException.class,
                () -> new GainTable(new double[]{1}, new double[]{0}, new double[]{0}, null));
        }

        @Test void rejectsEmptyAxes() {
            assertThrows(IllegalArgumentException.class,
                () -> new GainTable(new double[]{}, new double[]{0}, new double[]{0}, new short[]{0}));
        }

        @Test void rejectsMismatchedGainsLength() {
            assertThrows(IllegalArgumentException.class,
                () -> new GainTable(new double[]{1}, new double[]{0}, new double[]{0},
                                     new short[]{0, 0}));
        }

        @Test void rejectsUnsortedFrequencies() {
            assertThrows(IllegalArgumentException.class,
                () -> new GainTable(new double[]{10, 5}, new double[]{0}, new double[]{0},
                                     new short[]{0, 0}));
        }

        @Test void rejectsAzimuthAtOrAbove360() {
            assertThrows(IllegalArgumentException.class,
                () -> new GainTable(new double[]{1}, new double[]{0, 360}, new double[]{0},
                                     new short[]{0, 0}));
        }

        @Test void rejectsElevationAbove90() {
            assertThrows(IllegalArgumentException.class,
                () -> new GainTable(new double[]{1}, new double[]{0}, new double[]{0, 91},
                                     new short[]{0, 0}));
        }
    }

    private static GainTable simpleAzElGrid(double designFreqMHz) {
        double[] freqs = new double[]{ designFreqMHz };
        double[] azs   = new double[360];
        double[] els   = new double[91];
        for (int a = 0; a < 360; a++) azs[a] = a;
        for (int e = 0; e < 91;  e++) els[e] = e;
        short[] gains = new short[360 * 91];
        for (int a = 0; a < 360; a++) {
            for (int e = 0; e < 91; e++) {
                gains[a * 91 + e] = GainTable.toCentiDb((a + e) / 10.0);
            }
        }
        return new GainTable(freqs, azs, els, gains);
    }

    @Nested
    @DisplayName("Trilinear lookup")
    class Lookup {
        @Test void exactIntegerCoords_matchStoredValues() {
            GainTable t = simpleAzElGrid(14.15);
            assertEquals(  3.0, t.gainDbi(14.15,  10.0, 20.0), EPS);
            assertEquals(  9.0, t.gainDbi(14.15,  45.0, 45.0), EPS);
            assertEquals( 26.9, t.gainDbi(14.15, 269.0,  0.0), EPS);
        }

        @Test void azimuthInterpolation_at_halfDegree() {
            GainTable t = simpleAzElGrid(14.15);
            assertEquals(3.05, t.gainDbi(14.15, 10.5, 20.0), EPS);
        }

        @Test void elevationInterpolation_at_halfDegree() {
            GainTable t = simpleAzElGrid(14.15);
            assertEquals(3.05, t.gainDbi(14.15, 10.0, 20.5), EPS);
        }

        @Test void azimuthWrap_at_359_5() {
            GainTable t = simpleAzElGrid(14.15);
            assertEquals(22.45, t.gainDbi(14.15, 359.5, 45.0), EPS);
        }

        @Test void azimuthWrap_at_minus_0_5() {
            GainTable t = simpleAzElGrid(14.15);
            assertEquals(22.45, t.gainDbi(14.15, -0.5, 45.0), EPS);
        }

        @Test void azimuthWrap_at_720_5() {
            GainTable t = simpleAzElGrid(14.15);
            assertEquals(4.55, t.gainDbi(14.15, 720.5, 45.0), EPS);
        }

        @Test void outOfRangeFrequency_clampsToEndpoint() {
            double[] freqs = { 10.0, 15.0 };
            double[] azs   = { 0.0 };
            double[] els   = { 0.0, 45.0, 90.0 };
            short[] gains = new short[2 * 1 * 3];
            for (int e = 0; e < 3; e++) gains[0 * 3 + e] = GainTable.toCentiDb( 5.0);
            for (int e = 0; e < 3; e++) gains[1 * 3 + e] = GainTable.toCentiDb(10.0);
            GainTable t = new GainTable(freqs, azs, els, gains);
            assertEquals(10.0, t.gainDbi(25.0, 0.0, 45.0), EPS);
            assertEquals( 5.0, t.gainDbi( 1.0, 0.0, 45.0), EPS);
        }

        @Test void outOfRangeElevation_clampsToEndpoint() {
            double[] freqs = { 10.0 };
            double[] azs   = { 0.0 };
            double[] els   = new double[91];
            short[] gains = new short[91];
            for (int e = 0; e < 91; e++) {
                els[e] = e;
                gains[e] = GainTable.toCentiDb(e);
            }
            GainTable t = new GainTable(freqs, azs, els, gains);
            assertEquals(90.0, t.gainDbi(10.0, 0.0, 95.0), EPS);
            assertEquals( 0.0, t.gainDbi(10.0, 0.0, -5.0), EPS);
        }
    }

    @Nested
    @DisplayName("Sentinel handling")
    class Sentinels {
        private static GainTable elevationPatternWithHorizonSentinel() {
            double[] freqs = { 10.0 };
            double[] azs   = { 0.0 };
            double[] els   = new double[91];
            short[] gains = new short[91];
            for (int e = 0; e < 91; e++) els[e] = e;
            gains[0] = GainTable.SENTINEL_CENTI_DB;
            for (int e = 1; e < 91; e++) gains[e] = GainTable.toCentiDb(-7.0);
            return new GainTable(freqs, azs, els, gains);
        }

        @Test void elZero_returnsSentinel_whenStored() {
            GainTable t = elevationPatternWithHorizonSentinel();
            assertEquals(GainTable.SENTINEL_DBI, t.gainDbi(10.0, 0.0, 0.0), EPS);
        }

        @Test void elPointFive_collapsesToElOne_avoidingCliff() {
            GainTable t = elevationPatternWithHorizonSentinel();
            assertEquals(-7.0, t.gainDbi(10.0, 0.0, 0.5), EPS);
        }

        @Test void elOne_returnsExactStoredValue() {
            GainTable t = elevationPatternWithHorizonSentinel();
            assertEquals(-7.0, t.gainDbi(10.0, 0.0, 1.0), EPS);
        }
    }

    @Nested
    @DisplayName("Fixed-point int16 x 100 encoding")
    class FixedPoint {
        @Test void toCentiDb_roundsToInt16() {
            assertEquals(   0, GainTable.toCentiDb(  0.00));
            assertEquals( 500, GainTable.toCentiDb(  5.00));
            assertEquals(-500, GainTable.toCentiDb( -5.00));
            assertEquals(1274, GainTable.toCentiDb( 12.74));
            assertEquals(  10, GainTable.toCentiDb(  0.10));
            assertEquals(  -7, GainTable.toCentiDb( -0.07));
        }

        @Test void toCentiDb_sentinelPath() {
            assertEquals(GainTable.SENTINEL_CENTI_DB, GainTable.toCentiDb(GainTable.SENTINEL_DBI));
            assertEquals(GainTable.SENTINEL_CENTI_DB, GainTable.toCentiDb(-100.0));
            assertEquals(GainTable.SENTINEL_CENTI_DB, GainTable.toCentiDb(-999.9));
        }

        @Test void toCentiDb_clampsOutOfRange() {
            assertEquals(Short.MAX_VALUE, GainTable.toCentiDb(400.0));
            assertEquals(GainTable.SENTINEL_CENTI_DB, GainTable.toCentiDb(-99.99));
        }
    }
}
