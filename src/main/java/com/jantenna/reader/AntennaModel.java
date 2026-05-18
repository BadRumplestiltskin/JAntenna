package com.jantenna.reader;

import java.util.Arrays;

/**
 * Intermediate model for a 1D elevation-only antenna pattern parsed
 * from a VOACAPL {@code .voa} file (legacy Type 0 / Type 11).
 *
 * <p>Package-private: this is an internal converter type.  External
 * consumers should call
 * {@link VoaAntennaReader#readGainTable(java.nio.file.Path)} and work
 * with the unified {@link com.jantenna.GainTable} instead.</p>
 *
 * @param designFrequency Design frequency in MHz (0.0 if the source
 *                        file did not specify one).
 * @param interpolatedGains 91 elevation-gain values (0 deg .. 90 deg
 *                          at 1 deg) in absolute dBi.
 */
record AntennaModel(double designFrequency, double[] interpolatedGains) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AntennaModel that)) return false;
        return Double.compare(that.designFrequency, designFrequency) == 0
                && Arrays.equals(interpolatedGains, that.interpolatedGains);
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(designFrequency);
        result = 31 * result + Arrays.hashCode(interpolatedGains);
        return result;
    }

    @Override
    public String toString() {
        return "AntennaModel[designFrequency=" + designFrequency
                + ", interpolatedGains=" + Arrays.toString(interpolatedGains) + ']';
    }
}
