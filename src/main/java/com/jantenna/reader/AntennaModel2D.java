package com.jantenna.reader;

import java.util.Arrays;
import java.util.Objects;

/**
 * Intermediate model for a 2D azimuth x elevation antenna pattern
 * parsed from a VOACAPL Type-13 {@code .voa} file (360 x 91 grid at
 * 1 deg resolution).
 *
 * <p>Package-private: this is an internal converter type.  External
 * consumers should call
 * {@link VoaAntennaReader#readGainTable(java.nio.file.Path)} and work
 * with the unified {@link com.jantenna.GainTable} instead.</p>
 *
 * @param maxGainDbi      Header field "Max Gain dBi" from the source.
 * @param designFrequency Design frequency in MHz (0.0 if the source
 *                        file did not specify one).
 * @param gainsAbsoluteDbi Indexed {@code [azimuth][elevation]} -
 *                        absolute gain values in dBi (the Type-13 on-
 *                        disk convention stores absolutes already, so
 *                        no max-fold reconstruction is needed).
 */
record AntennaModel2D(
        double maxGainDbi,
        double designFrequency,
        double[][] gainsAbsoluteDbi
) {

    static final int AZIMUTH_POINTS = 360;
    static final int ELEVATION_POINTS = 91;

    AntennaModel2D {
        Objects.requireNonNull(gainsAbsoluteDbi, "gainsAbsoluteDbi must not be null");
        if (gainsAbsoluteDbi.length != AZIMUTH_POINTS) {
            throw new IllegalArgumentException(
                    "Type-13 pattern must have " + AZIMUTH_POINTS + " azimuth rows, got "
                    + gainsAbsoluteDbi.length);
        }
        for (int az = 0; az < AZIMUTH_POINTS; az++) {
            if (gainsAbsoluteDbi[az].length != ELEVATION_POINTS) {
                throw new IllegalArgumentException(
                        "Azimuth row " + az + " has " + gainsAbsoluteDbi[az].length
                        + " elevation samples; expected " + ELEVATION_POINTS);
            }
        }
    }

    double gainAt(int azimuthDeg, int elevationDeg) {
        return gainsAbsoluteDbi[Math.floorMod(azimuthDeg, AZIMUTH_POINTS)]
                               [Math.max(0, Math.min(ELEVATION_POINTS - 1, elevationDeg))];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AntennaModel2D that)) return false;
        return Double.compare(that.maxGainDbi, maxGainDbi) == 0
                && Double.compare(that.designFrequency, designFrequency) == 0
                && Arrays.deepEquals(gainsAbsoluteDbi, that.gainsAbsoluteDbi);
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(maxGainDbi);
        result = 31 * result + Double.hashCode(designFrequency);
        result = 31 * result + Arrays.deepHashCode(gainsAbsoluteDbi);
        return result;
    }

    @Override
    public String toString() {
        return "AntennaModel2D[maxGainDbi=" + maxGainDbi
                + ", designFrequency=" + designFrequency
                + ", gainsAbsoluteDbi=" + AZIMUTH_POINTS + "x" + ELEVATION_POINTS + ']';
    }
}
