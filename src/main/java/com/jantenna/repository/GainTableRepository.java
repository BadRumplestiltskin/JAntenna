package com.jantenna.repository;

import com.jantenna.GainTable;

import java.io.IOException;
import java.util.List;

/**
 * Read-only catalogue of baked {@link GainTable}s, keyed by
 * {@code <group>/<name>}.
 *
 * <p>Consumers query this to resolve antenna references by name.
 * Patterns are loaded on demand; implementations are responsible for
 * any caching.  Missing patterns throw {@link AntennaNotInRepository} -
 * strict-repo invariant (no fallback, no auto-bake).</p>
 *
 * <p>Implementations should be thread-safe for concurrent reads.</p>
 */
public interface GainTableRepository {

    /** {@code true} iff a pattern with the given canonical name exists. */
    boolean contains(String name);

    /**
     * Load and return the named pattern.
     *
     * @throws AntennaNotInRepository if the name is not present
     * @throws IOException on I/O or format errors during load
     */
    GainTable load(String name) throws IOException;

    /** List all available pattern names ({@code <group>/<name>} form), sorted. */
    List<String> list() throws IOException;

    /**
     * Thrown when a runtime lookup hits a name not in the repository.
     * There is no fallback path - the user must bake the source via
     * {@code jantenna bake}.
     */
    final class AntennaNotInRepository extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AntennaNotInRepository(String name) {
            super("Antenna '" + name + "' not in repository. "
                + "Run: jantenna bake <source-file>");
        }
    }
}
