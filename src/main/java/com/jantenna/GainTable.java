package com.jantenna;

import java.util.Arrays;
import java.util.Objects;

/**
 * Unified antenna pattern representation: a 3-axis tensor of antenna
 * gain values indexed by frequency x azimuth x elevation, stored as
 * int16 fixed-point (gain x 100, range +/-327.68 dB at 0.01 dB
 * resolution).
 *
 * <p>The same record holds 1D, 2D, and 3D patterns: any axis of length 1
 * is broadcast.  An isotrope is (F=1, A=1, E=1); an elevation-only
 * pattern is (F=1, A=1, E=N); an azimuth x elevation pattern is
 * (F=1, A=M, E=N); a multi-frequency pattern is (F=K, A=M, E=N).</p>
 *
 * <p><b>Storage layout:</b> flat {@code short[F*A*E]} in row-major order
 * ({@code flat[f*A*E + a*E + e]}).  Single allocation, cache-friendly,
 * ~4x smaller than {@code double[F][A][E]}.  Sentinel
 * {@link #SENTINEL_CENTI_DB} encodes "below horizon, no signal"
 * distinctly from any representable gain value.</p>
 *
 * <p><b>Axis semantics:</b></p>
 * <ul>
 *   <li>{@code frequenciesMHz}: clamped at endpoints; linear interp inside.</li>
 *   <li>{@code azimuthsDeg}: wrap-around (mod 360); the last cell wraps to the first.</li>
 *   <li>{@code elevationsDeg}: clamped to [0, 90]; linear interp inside.</li>
 * </ul>
 *
 * <p><b>Sentinel propagation:</b> when the elevation-lo bracket corner
 * carries the sentinel, the lookup collapses the elevation axis to the
 * elevation-hi corner (avoids catastrophic interpolation across the
 * horizon discontinuity).  At exactly {@code el = 0}, the lookup returns
 * {@link #SENTINEL_DBI}; downstream code drops such modes as
 * unphysical.</p>
 *
 * @param frequenciesMHz F entries (F &gt;= 1), sorted ascending
 * @param azimuthsDeg    A entries (A &gt;= 1), sorted ascending, in [0, 360)
 * @param elevationsDeg  E entries (E &gt;= 1), sorted ascending, in [0, 90]
 * @param gainsCentiDb   flat F*A*E, row-major; gain_dBi = value / 100.0
 */
public record GainTable(
        double[] frequenciesMHz,
        double[] azimuthsDeg,
        double[] elevationsDeg,
        short[] gainsCentiDb
) {

    /** Sentinel encoded in the int16 grid: "below horizon, no signal". */
    public static final short SENTINEL_CENTI_DB = Short.MIN_VALUE;

    /** Sentinel as a dBi value (matches voacapl's -99.99 convention). */
    public static final double SENTINEL_DBI = -99.99;

    /** Largest absolute gain (in dBi) representable in the int16 fixed-point grid. */
    public static final double MAX_REPRESENTABLE_DBI = (Short.MAX_VALUE) / 100.0;

    public GainTable {
        Objects.requireNonNull(frequenciesMHz, "frequenciesMHz");
        Objects.requireNonNull(azimuthsDeg, "azimuthsDeg");
        Objects.requireNonNull(elevationsDeg, "elevationsDeg");
        Objects.requireNonNull(gainsCentiDb, "gainsCentiDb");
        if (frequenciesMHz.length < 1) throw new IllegalArgumentException("F must be >= 1");
        if (azimuthsDeg.length < 1)    throw new IllegalArgumentException("A must be >= 1");
        if (elevationsDeg.length < 1)  throw new IllegalArgumentException("E must be >= 1");
        int expected = frequenciesMHz.length * azimuthsDeg.length * elevationsDeg.length;
        if (gainsCentiDb.length != expected) {
            throw new IllegalArgumentException(
                    "gainsCentiDb length " + gainsCentiDb.length
                    + " != F*A*E = " + expected);
        }
        requireAscending(frequenciesMHz, "frequenciesMHz");
        requireAscending(azimuthsDeg,    "azimuthsDeg");
        requireAscending(elevationsDeg,  "elevationsDeg");
        if (azimuthsDeg[0] < 0.0 || azimuthsDeg[azimuthsDeg.length - 1] >= 360.0) {
            throw new IllegalArgumentException(
                    "azimuthsDeg must lie in [0, 360); got ["
                    + azimuthsDeg[0] + ", " + azimuthsDeg[azimuthsDeg.length - 1] + "]");
        }
        if (elevationsDeg[0] < 0.0 || elevationsDeg[elevationsDeg.length - 1] > 90.0) {
            throw new IllegalArgumentException(
                    "elevationsDeg must lie in [0, 90]; got ["
                    + elevationsDeg[0] + ", " + elevationsDeg[elevationsDeg.length - 1] + "]");
        }
    }

    public int frequencyCount()  { return frequenciesMHz.length; }
    public int azimuthCount()    { return azimuthsDeg.length;    }
    public int elevationCount()  { return elevationsDeg.length;  }

    /**
     * Return the gain in dBi at the given query point, trilinearly
     * interpolated on the tensor.
     *
     * <p>Boundary handling:</p>
     * <ul>
     *   <li>Out-of-range freq or elev -&gt; clamped to nearest endpoint.</li>
     *   <li>Azimuth -&gt; reduced modulo 360, then bracket-wrapped.</li>
     *   <li>If elevation-lo corner is sentinel, collapse the elevation
     *       axis to the elevation-hi corner (smooth physics at grazing).</li>
     * </ul>
     */
    public double gainDbi(double freqMHz, double azDeg, double elDeg) {
        double azNorm = Math.floorMod((long) Math.floor(azDeg), 360L)
                       + (azDeg - Math.floor(azDeg));
        double elClamp = Math.max(0.0, Math.min(90.0, elDeg));
        double fClamp  = Math.max(frequenciesMHz[0],
                                  Math.min(frequenciesMHz[frequenciesMHz.length - 1], freqMHz));

        int[] fb = bracketClamp(frequenciesMHz, fClamp);
        int[] ab = bracketWrap(azimuthsDeg,    azNorm);
        int[] eb = bracketClamp(elevationsDeg, elClamp);

        double wF = weightClamp(frequenciesMHz, fb, fClamp);
        double wA = weightWrap (azimuthsDeg,    ab, azNorm);
        double wE = weightClamp(elevationsDeg,  eb, elClamp);

        double g000 = readDbi(fb[0], ab[0], eb[0]);
        double g001 = readDbi(fb[0], ab[0], eb[1]);
        double g010 = readDbi(fb[0], ab[1], eb[0]);
        double g011 = readDbi(fb[0], ab[1], eb[1]);
        double g100 = readDbi(fb[1], ab[0], eb[0]);
        double g101 = readDbi(fb[1], ab[0], eb[1]);
        double g110 = readDbi(fb[1], ab[1], eb[0]);
        double g111 = readDbi(fb[1], ab[1], eb[1]);

        if (isSentinel(g000) || isSentinel(g010) || isSentinel(g100) || isSentinel(g110)) {
            g000 = g001; g010 = g011; g100 = g101; g110 = g111;
            wE = 0.0;
        }
        if (isSentinel(g000) || isSentinel(g001) || isSentinel(g010) || isSentinel(g011)
         || isSentinel(g100) || isSentinel(g101) || isSentinel(g110) || isSentinel(g111)) {
            return SENTINEL_DBI;
        }

        double g00 = lerp(g000, g100, wF);
        double g01 = lerp(g001, g101, wF);
        double g10 = lerp(g010, g110, wF);
        double g11 = lerp(g011, g111, wF);
        double g0  = lerp(g00,  g10,  wA);
        double g1  = lerp(g01,  g11,  wA);
        return       lerp(g0,   g1,   wE);
    }

    /**
     * Flat index of one grid cell. Callers that walk the grid should use this
     * rather than reproducing the row-major layout, which is an implementation
     * detail of this record.
     */
    public int index(int f, int a, int e) {
        return f * (azimuthsDeg.length * elevationsDeg.length)
                + a * elevationsDeg.length
                + e;
    }

    /**
     * Stored gain at one grid point, in dBi, with no interpolation.
     * Returns {@link #SENTINEL_DBI} for a cell carrying the sentinel.
     */
    public double readDbi(int f, int a, int e) {
        short raw = gainsCentiDb[index(f, a, e)];
        if (raw == SENTINEL_CENTI_DB) return SENTINEL_DBI;
        return raw / 100.0;
    }

    /**
     * Highest stored gain across the whole table, in dBi, ignoring sentinel
     * cells. Returns {@link #SENTINEL_DBI} for a table that is entirely
     * sentinel, i.e. one carrying no gain data at all.
     */
    public double peakDbi() {
        double peak = Double.NEGATIVE_INFINITY;
        for (short v : gainsCentiDb) {
            if (v != SENTINEL_CENTI_DB) {
                double g = v / 100.0;
                if (g > peak) peak = g;
            }
        }
        return peak == Double.NEGATIVE_INFINITY ? SENTINEL_DBI : peak;
    }

    private static boolean isSentinel(double dBi) {
        return dBi <= SENTINEL_DBI + 1e-9;
    }

    /**
     * Convert a dBi value to int16 fixed-point representation.  Values
     * at or below {@link #SENTINEL_DBI} map to {@link #SENTINEL_CENTI_DB}.
     * Out-of-range values are clamped to +/-{@code Short.MAX_VALUE}
     * (~+/-327.67 dB) reserving {@code Short.MIN_VALUE} for the sentinel.
     */
    public static short toCentiDb(double dBi) {
        if (dBi <= SENTINEL_DBI) return SENTINEL_CENTI_DB;
        long centi = Math.round(dBi * 100.0);
        if (centi > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (centi < Short.MIN_VALUE + 1) return (short) (Short.MIN_VALUE + 1);
        return (short) centi;
    }

    private static void requireAscending(double[] axis, String name) {
        for (int i = 1; i < axis.length; i++) {
            if (axis[i] <= axis[i - 1]) {
                throw new IllegalArgumentException(
                        name + " must be sorted strictly ascending; ["
                        + (i - 1) + "]=" + axis[i - 1] + " >= [" + i + "]=" + axis[i]);
            }
        }
    }

    private static int[] bracketClamp(double[] axis, double q) {
        int n = axis.length;
        if (n == 1) return new int[]{ 0, 0 };
        if (q <= axis[0])     return new int[]{ 0, 0 };
        if (q >= axis[n - 1]) return new int[]{ n - 1, n - 1 };
        int idx = Arrays.binarySearch(axis, q);
        if (idx >= 0) return new int[]{ idx, idx };
        int insertion = -idx - 1;
        return new int[]{ insertion - 1, insertion };
    }

    private static int[] bracketWrap(double[] axis, double q) {
        int n = axis.length;
        if (n == 1) return new int[]{ 0, 0 };
        if (q < axis[0])      return new int[]{ n - 1, 0 };
        if (q >= axis[n - 1]) return new int[]{ n - 1, 0 };
        int idx = Arrays.binarySearch(axis, q);
        if (idx >= 0) return new int[]{ idx, idx };
        int insertion = -idx - 1;
        return new int[]{ insertion - 1, insertion };
    }

    private static double weightClamp(double[] axis, int[] bracket, double q) {
        if (bracket[0] == bracket[1]) return 0.0;
        return (q - axis[bracket[0]]) / (axis[bracket[1]] - axis[bracket[0]]);
    }

    private static double weightWrap(double[] axis, int[] bracket, double q) {
        if (bracket[0] == bracket[1]) return 0.0;
        int n = axis.length;
        if (bracket[0] == n - 1 && bracket[1] == 0) {
            double span = 360.0 - axis[n - 1];
            double qRel = q < axis[0] ? q + 360.0 - axis[n - 1] : q - axis[n - 1];
            return qRel / span;
        }
        return (q - axis[bracket[0]]) / (axis[bracket[1]] - axis[bracket[0]]);
    }

    private static double lerp(double a, double b, double w) {
        return a + (b - a) * w;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GainTable that)) return false;
        return Arrays.equals(frequenciesMHz, that.frequenciesMHz)
            && Arrays.equals(azimuthsDeg,    that.azimuthsDeg)
            && Arrays.equals(elevationsDeg,  that.elevationsDeg)
            && Arrays.equals(gainsCentiDb,   that.gainsCentiDb);
    }

    @Override
    public int hashCode() {
        int h = Arrays.hashCode(frequenciesMHz);
        h = 31 * h + Arrays.hashCode(azimuthsDeg);
        h = 31 * h + Arrays.hashCode(elevationsDeg);
        h = 31 * h + Arrays.hashCode(gainsCentiDb);
        return h;
    }

    @Override
    public String toString() {
        return "GainTable[F=" + frequencyCount()
             + ", A=" + azimuthCount()
             + ", E=" + elevationCount()
             + ", bytes=" + (gainsCentiDb.length * 2L) + "]";
    }
}
