package com.jantenna;

/**
 * Build-time version identifiers stamped into every baked
 * {@link AntennaMetadata} for drift detection.
 *
 * <p>These are set at compile time and travel with each baked
 * {@code .gtable}.  When a consumer starts and the recorded
 * {@code baker.sha} disagrees with the running build, the runtime
 * logs a drift warning.</p>
 */
public final class BakerVersion {

    /** Semver of the baker module - bumped on any formula or format change. */
    public static final String VERSION = "1.0.0";

    /** Pseudo-SHA identifying the baker module; bump on any baker edit. */
    public static final String MODULE_SHA = "jantenna-1.0.0";

    /** Semver of the running JAntenna build that produced the bake. */
    public static final String JANTENNA_VERSION = "1.0.0-SNAPSHOT";

    private BakerVersion() { }
}
