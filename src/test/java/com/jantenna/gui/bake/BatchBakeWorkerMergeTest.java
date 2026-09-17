package com.jantenna.gui.bake;

import com.jantenna.GainTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link BatchBakeWorker#mergeByFrequency} - the batch-bake merge step.
 *
 * <p>Focus is the duplicate-frequency case: the merge keeps the last table
 * seen at a given frequency, and must report that as a warning rather than
 * dropping the earlier one silently.</p>
 */
@DisplayName("BatchBakeWorker frequency merge")
class BatchBakeWorkerMergeTest {

    private static final double[] AZ = { 0.0, 90.0, 180.0, 270.0 };
    private static final double[] EL = { 0.0, 45.0, 90.0 };

    /** Single-frequency table whose every cell holds {@code gainDb}. */
    private static GainTable table(double freqMHz, double gainDb) {
        short[] gains = new short[AZ.length * EL.length];
        java.util.Arrays.fill(gains, (short) Math.round(gainDb * 100));
        return new GainTable(new double[]{ freqMHz }, AZ.clone(), EL.clone(), gains);
    }

    @Test
    @DisplayName("distinct frequencies merge in ascending order with no warnings")
    void mergesDistinctFrequencies() {
        BatchBakeWorker.MergeResult result = BatchBakeWorker.mergeByFrequency(
                List.of(table(14.0, 3.0), table(7.0, 1.0)),
                List.of("high.13", "low.13"));

        assertArrayEqualsExact(new double[]{ 7.0, 14.0 }, result.table().frequenciesMHz());
        assertEquals(2, result.table().frequencyCount());
        assertTrue(result.warnings().isEmpty(), "no duplicates, so no warnings");
    }

    @Test
    @DisplayName("duplicate frequency keeps the last table and warns")
    void warnsOnDuplicateFrequency() {
        BatchBakeWorker.MergeResult result = BatchBakeWorker.mergeByFrequency(
                List.of(table(7.0, 1.0), table(7.0, 9.0)),
                List.of("first.13", "second.13"));

        assertEquals(1, result.table().frequencyCount(), "duplicates collapse to one slot");
        assertEquals(900, result.table().gainsCentiDb()[0], "last source wins");

        assertEquals(1, result.warnings().size());
        String warning = result.warnings().get(0);
        assertTrue(warning.contains("second.13"), "names the winner: " + warning);
        assertTrue(warning.contains("first.13"),  "names the loser: "  + warning);
    }

    @Test
    @DisplayName("multi-frequency source is rejected")
    void rejectsMultiFrequencySource() {
        GainTable multi = new GainTable(
                new double[]{ 7.0, 14.0 }, AZ.clone(), EL.clone(),
                new short[2 * AZ.length * EL.length]);

        assertThrows(IllegalArgumentException.class, () ->
                BatchBakeWorker.mergeByFrequency(List.of(multi), List.of("multi.13")));
    }

    @Test
    @DisplayName("mismatched grid shape is rejected")
    void rejectsMismatchedGrid() {
        GainTable odd = new GainTable(
                new double[]{ 21.0 }, new double[]{ 0.0, 180.0 }, EL.clone(),
                new short[2 * EL.length]);

        assertThrows(IllegalArgumentException.class, () ->
                BatchBakeWorker.mergeByFrequency(
                        List.of(table(7.0, 1.0), odd),
                        List.of("ok.13", "odd.13")));
    }

    private static void assertArrayEqualsExact(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length, "frequency count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1e-9, "frequency[" + i + "]");
        }
    }
}
