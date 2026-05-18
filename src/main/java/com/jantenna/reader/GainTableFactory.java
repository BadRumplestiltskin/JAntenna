package com.jantenna.reader;

import com.jantenna.GainTable;

import java.util.Map;
import java.util.NavigableMap;

/**
 * Constructs {@link GainTable} instances from VOACAPL-format-specific
 * inputs (legacy Type 0 / 11 / 13 / 14 conventions, ITU-R Rec.705
 * multi-frequency Type-13 catalogues).
 *
 * <p>The legacy "Type N" naming is preserved on the factory methods
 * to make the conversion contract explicit: each factory knows which
 * on-disk format it accepts (offsets vs absolutes, max-fold semantics,
 * axis conventions).  Consumers that already have a unified
 * {@link GainTable} (e.g. loaded from a baked {@code .gtable} via
 * {@link com.jantenna.GainTableCodec}) do not need this class.</p>
 */
public final class GainTableFactory {

    private GainTableFactory() { }

    /**
     * Single gain value broadcast across all axes (1D isotrope).
     * Resulting tensor is (F=1, A=1, E=1).
     */
    public static GainTable fromIsotrope(double maxGainDbi, double designFreqMHz) {
        return new GainTable(
                new double[]{ designFreqMHz },
                new double[]{ 0.0 },
                new double[]{ 0.0 },
                new short[]{ GainTable.toCentiDb(maxGainDbi) });
    }

    /**
     * Type-11 elevation-only pattern (91 gain values at 1 deg
     * elevation steps, 0..90).  Stored values in voacapl Type-11
     * files are <b>offsets from {@code maxGainDbi}</b> (per
     * {@code const17.voa}: max=17, entries -43..0..0); this factory
     * reconstructs absolute dBi via {@code max + offset}.  Sentinel
     * passes through unchanged.
     */
    public static GainTable fromType11Elevations(double[] offsetGains91,
                                                  double maxGainDbi,
                                                  double designFreqMHz) {
        if (offsetGains91.length != 91) {
            throw new IllegalArgumentException(
                    "Type-11 needs 91 elevation gains; got " + offsetGains91.length);
        }
        short[] flat = new short[91];
        for (int e = 0; e < 91; e++) {
            double off = offsetGains91[e];
            double abs = off <= GainTable.SENTINEL_DBI ? GainTable.SENTINEL_DBI : maxGainDbi + off;
            flat[e] = GainTable.toCentiDb(abs);
        }
        return new GainTable(
                new double[]{ designFreqMHz },
                new double[]{ 0.0 },
                integerDegrees(0, 90),
                flat);
    }

    /**
     * Type-13 azimuth x elevation grid (single frequency).  Stored
     * values in voacapl Type-13 files are <b>absolute dBi</b>
     * (verified empirically against the ITU-R Rec.705 catalogue) so
     * no max-fold reconstruction is applied.
     */
    public static GainTable fromType13AzEl(double[][] absoluteGains360x91,
                                            double designFreqMHz) {
        if (absoluteGains360x91.length != 360) {
            throw new IllegalArgumentException(
                    "Type-13 needs 360 azimuth rows; got " + absoluteGains360x91.length);
        }
        short[] flat = new short[360 * 91];
        for (int a = 0; a < 360; a++) {
            if (absoluteGains360x91[a].length != 91) {
                throw new IllegalArgumentException(
                        "Type-13 azimuth row " + a + " needs 91 elevations; got "
                        + absoluteGains360x91[a].length);
            }
            for (int e = 0; e < 91; e++) {
                flat[a * 91 + e] = GainTable.toCentiDb(absoluteGains360x91[a][e]);
            }
        }
        return new GainTable(
                new double[]{ designFreqMHz },
                integerDegrees(0, 359),
                integerDegrees(0, 90),
                flat);
    }

    /**
     * Type-14 frequency x elevation grid (no azimuth).  Caller
     * supplies the frequency axis explicitly (commonly 1..30 MHz).
     */
    public static GainTable fromType14FreqEl(double[][] absoluteGainsFxE,
                                              double[] frequenciesMHz) {
        if (absoluteGainsFxE.length != frequenciesMHz.length) {
            throw new IllegalArgumentException(
                    "Type-14 freq row count " + absoluteGainsFxE.length
                    + " != frequenciesMHz length " + frequenciesMHz.length);
        }
        int F = frequenciesMHz.length;
        short[] flat = new short[F * 91];
        for (int f = 0; f < F; f++) {
            if (absoluteGainsFxE[f].length != 91) {
                throw new IllegalArgumentException(
                        "Type-14 freq row " + f + " needs 91 elevations; got "
                        + absoluteGainsFxE[f].length);
            }
            for (int e = 0; e < 91; e++) {
                flat[f * 91 + e] = GainTable.toCentiDb(absoluteGainsFxE[f][e]);
            }
        }
        return new GainTable(
                frequenciesMHz.clone(),
                new double[]{ 0.0 },
                integerDegrees(0, 90),
                flat);
    }

    /**
     * Multi-frequency Type-13 collection (ITU-R Rec.705 catalogue
     * layout - one Type-13 file per MHz).  Each map value is a
     * 360 x 91 absolute-dBi grid for the keyed frequency.
     */
    public static GainTable fromMultiFreqType13(NavigableMap<Double, double[][]> byMHz) {
        if (byMHz == null || byMHz.isEmpty()) {
            throw new IllegalArgumentException("multi-freq Type-13 requires >= 1 frequency");
        }
        int F = byMHz.size();
        double[] freqs = new double[F];
        short[] flat = new short[F * 360 * 91];
        int f = 0;
        for (Map.Entry<Double, double[][]> entry : byMHz.entrySet()) {
            freqs[f] = entry.getKey();
            double[][] grid = entry.getValue();
            if (grid.length != 360) {
                throw new IllegalArgumentException(
                        "multi-freq Type-13 at " + entry.getKey() + " MHz needs 360 az rows");
            }
            for (int a = 0; a < 360; a++) {
                if (grid[a].length != 91) {
                    throw new IllegalArgumentException(
                            "multi-freq Type-13 at " + entry.getKey() + " MHz az " + a
                            + " needs 91 elevations");
                }
                for (int e = 0; e < 91; e++) {
                    flat[f * 360 * 91 + a * 91 + e] = GainTable.toCentiDb(grid[a][e]);
                }
            }
            f++;
        }
        return new GainTable(freqs, integerDegrees(0, 359), integerDegrees(0, 90), flat);
    }

    /** Convert the internal {@link AntennaModel} (Type 0 / 11 fold) to {@link GainTable}. */
    static GainTable fromAntennaModel(AntennaModel model) {
        return fromType11Elevations(model.interpolatedGains(), 0.0, model.designFrequency());
    }

    /** Convert the internal {@link AntennaModel2D} (Type 13) to {@link GainTable}. */
    static GainTable fromAntennaModel2D(AntennaModel2D model) {
        return fromType13AzEl(model.gainsAbsoluteDbi(), model.designFrequency());
    }

    private static double[] integerDegrees(int from, int to) {
        double[] out = new double[to - from + 1];
        for (int i = 0; i < out.length; i++) out[i] = from + i;
        return out;
    }
}
