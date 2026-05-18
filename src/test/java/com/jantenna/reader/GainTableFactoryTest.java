package com.jantenna.reader;

import com.jantenna.GainTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for {@link GainTableFactory}. */
@DisplayName("GainTableFactory")
class GainTableFactoryTest {

    private static final double EPS = 0.005;

    @Test
    @DisplayName("Isotrope: single value broadcast across all axes")
    void isotrope() {
        GainTable t = GainTableFactory.fromIsotrope(5.0, 14.15);
        assertEquals(5.0, t.gainDbi(14.15,   0.0,  0.0), EPS);
        assertEquals(5.0, t.gainDbi(14.15,  45.0, 30.0), EPS);
        assertEquals(5.0, t.gainDbi(14.15, 180.0, 89.0), EPS);
        assertEquals(5.0, t.gainDbi(14.15, 359.5, 45.0), EPS);
        assertEquals(1, t.frequencyCount());
        assertEquals(1, t.azimuthCount());
        assertEquals(1, t.elevationCount());
    }

    @Test
    @DisplayName("Type-11 fold: absolute = maxGainDbi + offset")
    void type11_appliesMaxFold() {
        double[] offsets = new double[91];
        offsets[0] = -43.0;
        offsets[1] = -12.0;
        offsets[2] =  -7.0;
        offsets[3] =  -2.0;
        GainTable t = GainTableFactory.fromType11Elevations(offsets, 17.0, 10.0);
        assertEquals(17.0 - 43.0, t.gainDbi(10.0, 0.0,  0.0), EPS);
        assertEquals(17.0 - 12.0, t.gainDbi(10.0, 0.0,  1.0), EPS);
        assertEquals(17.0 -  7.0, t.gainDbi(10.0, 0.0,  2.0), EPS);
        assertEquals(17.0 -  2.0, t.gainDbi(10.0, 0.0,  3.0), EPS);
        assertEquals(17.0,        t.gainDbi(10.0, 0.0,  4.0), EPS);
        assertEquals(17.0,        t.gainDbi(10.0, 0.0, 90.0), EPS);
    }

    @Test
    @DisplayName("Type-11: sentinel preserved across max-fold")
    void type11_sentinelPreservedAcrossMaxFold() {
        double[] offsets = new double[91];
        offsets[0] = GainTable.SENTINEL_DBI;
        for (int i = 1; i < 91; i++) offsets[i] = 0.0;
        GainTable t = GainTableFactory.fromType11Elevations(offsets, 17.0, 10.0);
        assertEquals(GainTable.SENTINEL_DBI, t.gainDbi(10.0, 0.0, 0.0), EPS);
    }

    @Test
    @DisplayName("Type-13: stores absolute values")
    void type13_storesAbsoluteValues() {
        double[][] g = new double[360][91];
        for (int a = 0; a < 360; a++) {
            for (int e = 0; e < 91; e++) {
                g[a][e] = (a == 180 && e == 30) ? 12.74 : 0.0;
            }
        }
        GainTable t = GainTableFactory.fromType13AzEl(g, 10.0);
        assertEquals(12.74, t.gainDbi(10.0, 180.0, 30.0), EPS);
        assertEquals( 0.00, t.gainDbi(10.0,   0.0,  0.0), EPS);
        assertEquals(360, t.azimuthCount());
        assertEquals(91,  t.elevationCount());
    }

    @Test
    @DisplayName("Type-14: keeps freq axis, no azimuth")
    void type14_keepsFreqAxis() {
        double[] freqs = { 5.0, 10.0, 15.0 };
        double[][] g = new double[3][91];
        for (int f = 0; f < 3; f++)
            for (int e = 0; e < 91; e++)
                g[f][e] = f * 10.0;
        GainTable t = GainTableFactory.fromType14FreqEl(g, freqs);
        assertEquals( 0.0, t.gainDbi( 5.0, 0.0, 45.0), EPS);
        assertEquals(10.0, t.gainDbi(10.0, 0.0, 45.0), EPS);
        assertEquals(20.0, t.gainDbi(15.0, 0.0, 45.0), EPS);
        assertEquals(3, t.frequencyCount());
        assertEquals(1, t.azimuthCount());
    }

    @Test
    @DisplayName("Multi-frequency Type-13: linear interpolation between freq slabs")
    void multiFreqType13_interpolatesBetweenFreqs() {
        TreeMap<Double, double[][]> byMHz = new TreeMap<>();
        double[][] g10 = new double[360][91];
        double[][] g15 = new double[360][91];
        for (int a = 0; a < 360; a++) {
            for (int e = 0; e < 91; e++) {
                g10[a][e] = 5.0;
                g15[a][e] = 10.0;
            }
        }
        byMHz.put(10.0, g10);
        byMHz.put(15.0, g15);
        GainTable t = GainTableFactory.fromMultiFreqType13(byMHz);
        assertEquals( 5.0, t.gainDbi(10.0,  0.0, 45.0), EPS);
        assertEquals(10.0, t.gainDbi(15.0,  0.0, 45.0), EPS);
        assertEquals( 7.5, t.gainDbi(12.5,  0.0, 45.0), EPS);
    }

    @Test
    @DisplayName("fromAntennaModel: 1D pattern preserves gain at every integer angle")
    void fromAntennaModel_preservesIntegerAngles() {
        double[] gains = new double[91];
        for (int e = 0; e < 91; e++) gains[e] = e * 0.1;
        AntennaModel legacy = new AntennaModel(14.15, gains);
        GainTable t = GainTableFactory.fromAntennaModel(legacy);
        for (int e = 0; e < 91; e++) {
            assertEquals(e * 0.1, t.gainDbi(14.15, 0.0, e), EPS);
        }
        assertEquals(1, t.frequencyCount());
        assertEquals(1, t.azimuthCount());
        assertEquals(91, t.elevationCount());
    }

    @Test
    @DisplayName("fromAntennaModel2D: 360x91 pattern preserves gain at every integer (az, el)")
    void fromAntennaModel2D_preservesIntegerAngles() {
        double[][] gains = new double[360][91];
        for (int a = 0; a < 360; a++)
            for (int e = 0; e < 91; e++)
                gains[a][e] = (a + e) * 0.01;
        AntennaModel2D legacy = new AntennaModel2D(4.49, 10.0, gains);
        GainTable t = GainTableFactory.fromAntennaModel2D(legacy);
        assertEquals(0.10, t.gainDbi(10.0,  10.0,  0.0), EPS);
        assertEquals(0.65, t.gainDbi(10.0,  20.0, 45.0), EPS);
        assertEquals(4.49, t.gainDbi(10.0, 359.0, 90.0), EPS);
        assertEquals(360, t.azimuthCount());
        assertEquals( 91, t.elevationCount());
    }
}
