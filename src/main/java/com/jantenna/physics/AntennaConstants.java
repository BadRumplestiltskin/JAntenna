package com.jantenna.physics;

/** Constants for antenna physics calculations. */
public final class AntennaConstants {

    private AntennaConstants() { }

    /** Impedance ratio factor used in antenna impedance calculations: sqrt(2) / 4680. */
    public static final double IMPEDANCE_RATIO_FACTOR = Math.sqrt(2.0) / 4680.0;

    /** Gain floor (dB) returned by analytical calculators on degenerate geometries. */
    public static final double GAIN_FLOOR_DB = -30.0;
}
