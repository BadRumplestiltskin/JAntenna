package com.jantenna.baker;

import com.jantenna.AntennaMetadata;

import com.jantenna.GainTable;
import com.jantenna.physics.ConstantGainCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HfmufesAnalyticalBaker} — the harness that wraps an
 * {@link com.voacap.service.antenna.AntennaGainCalculator} and produces
 * a {@link GainTable} by evaluating the analytical formula at every
 * grid cell.
 *
 * <p>Phase D pass 1 wires only KOP 12 (Constant) end-to-end.  The
 * harness itself is KOP-agnostic; subsequent passes add the geometry-
 * dependent KOPs.</p>
 */
@DisplayName("HfmufesAnalyticalBaker — harness for KOP 1..17")
class HfmufesAnalyticalBakerTest {

    private static final double EPS = 0.005;

    @Test
    @DisplayName("KOP 12 Constant baked at single-freq grid → uniform gain across (az, el)")
    void constantKop_singleFreq() {
        HfmufesBakeSpec spec = new HfmufesBakeSpec(
                12, "Constant 5 dBi test",
                0.005, 15.0,
                0.0, 0.0, 5.0,
                new double[0], 0.0);
        HfmufesAnalyticalBaker baker = new HfmufesAnalyticalBaker(
                new ConstantGainCalculator(), spec, new double[]{ 14.15 });
        AntennaBaker.BakeResult result = baker.bake(null, "test", "const5-hfmufes");

        GainTable t = result.table();
        assertEquals(1,   t.frequencyCount());
        assertEquals(360, t.azimuthCount());
        assertEquals(91,  t.elevationCount());

        // ConstantGainCalculator returns params.ynh() which is 5.0 here.
        // The harness writes the sentinel into el=0 row, so peak gain
        // (excluding sentinel) should be exactly 5.0 dBi at all (az, el>=1).
        assertEquals(5.0, t.gainDbi(14.15,   0.0, 45.0), EPS);
        assertEquals(5.0, t.gainDbi(14.15,  90.0, 30.0), EPS);
        assertEquals(5.0, t.gainDbi(14.15, 180.0,  5.0), EPS);
        assertEquals(5.0, t.gainDbi(14.15, 270.0, 89.0), EPS);

        // El=0 is the sentinel
        assertEquals(GainTable.SENTINEL_DBI, t.gainDbi(14.15, 0.0, 0.0), EPS);
    }

    @Test
    @DisplayName("KOP mismatch between calculator and spec throws")
    void kopMismatchRejected() {
        HfmufesBakeSpec dipoleSpec = new HfmufesBakeSpec(
                3, "dipole", 0.005, 15.0, 0.0, 0.5, 0.25, new double[0], 0.0);
        // Constant calculator declares KOP 12, spec says KOP 3 → mismatch
        assertThrows(IllegalArgumentException.class,
                () -> new HfmufesAnalyticalBaker(new ConstantGainCalculator(), dipoleSpec));
    }

    @Test
    @DisplayName("Multi-frequency bake produces F-dimensional tensor")
    void multiFreq_tensorShape() {
        HfmufesBakeSpec spec = new HfmufesBakeSpec(
                12, "constant", 0.005, 15.0, 0.0, 0.0, 3.0, new double[0], 0.0);
        HfmufesAnalyticalBaker baker = new HfmufesAnalyticalBaker(
                new ConstantGainCalculator(), spec,
                new double[]{ 7.0, 14.0, 21.0, 28.0 });
        AntennaBaker.BakeResult result = baker.bake(null, "test", "multi-freq");

        assertEquals(4, result.table().frequencyCount());
        for (double f : new double[]{ 7.0, 14.0, 21.0, 28.0 }) {
            assertEquals(3.0, result.table().gainDbi(f, 45.0, 30.0), EPS,
                    "Constant gain should hold at " + f + " MHz");
        }
    }

    @Test
    @DisplayName("Metadata records source kind + KOP-specific provenance")
    void metadataPopulated() {
        HfmufesBakeSpec spec = new HfmufesBakeSpec(
                12, "Sample antenna", 0.005, 15.0, 0.0, 0.0, 7.5,
                new double[0], 0.0);
        HfmufesAnalyticalBaker baker = new HfmufesAnalyticalBaker(
                new ConstantGainCalculator(), spec, new double[]{ 14.15 });
        AntennaBaker.BakeResult result = baker.bake(null, "test", "sample");

        AntennaMetadata m = result.metadata();
        assertEquals("HFMUFES_KOP_12", m.source().kind());
        assertEquals("HFMUFES_KOP_12", baker.sourceKind());
        assertEquals("Sample antenna", m.description());
        assertNull(m.source().path(), "analytical bake has no source path");
        assertNull(m.source().sha256(), "analytical bake has no source sha");
        assertTrue(m.source().paramsJson().contains("\"kop\":12"));
        assertEquals(7.5, m.peakGainDbi(), EPS);
        assertTrue(m.grid().azDependent());
        assertFalse(m.grid().freqDependent(), "single-freq bake → freq_dependent = false");
    }

    @Test
    @DisplayName("Standard ITU-R freq axis covers 3..30 MHz integer steps (28 entries)")
    void standardFreqAxis_covers3to30() {
        HfmufesBakeSpec spec = new HfmufesBakeSpec(
                12, "default-grid", 0.005, 15.0, 0.0, 0.0, 0.0, new double[0], 0.0);
        // Default constructor uses standardFreqAxis()
        HfmufesAnalyticalBaker baker = new HfmufesAnalyticalBaker(
                new ConstantGainCalculator(), spec);
        AntennaBaker.BakeResult result = baker.bake(null, "test", "default");
        assertEquals(28, result.table().frequencyCount());
        assertEquals( 3.0, result.table().frequenciesMHz()[0],  1e-9);
        assertEquals(30.0, result.table().frequenciesMHz()[27], 1e-9);
    }
}
