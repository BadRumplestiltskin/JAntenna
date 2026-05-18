package com.jantenna.baker;

import com.jantenna.AntennaMetadata;

import com.jantenna.GainTable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A baker converts an antenna source (file or analytical parameters)
 * into a {@link GainTable} plus accompanying {@link AntennaMetadata}.
 * Per {@code docs/antennas.md} §9, bakers run only at bake time —
 * never at runtime.
 */
public interface AntennaBaker {

    /** Result of a single bake: the table + its metadata, paired for atomic write. */
    record BakeResult(GainTable table, AntennaMetadata metadata) { }

    /** Identifying string ({@code source.kind} in the metadata sidecar).
     * @return  */
    String sourceKind();

    /**
     * Bake the antenna at {@code source} into a {@link BakeResult}.
     *
     * @param source        path to the input file (or null for analytical bakers)
     * @param targetGroup   logical group ({@code "default"}, {@code "itur705"}, etc.)
     * @param targetName    pattern name without extension or group
     * @return 
     * @throws IOException on read/format errors
     */
    BakeResult bake(Path source, String targetGroup, String targetName) throws IOException;
}
