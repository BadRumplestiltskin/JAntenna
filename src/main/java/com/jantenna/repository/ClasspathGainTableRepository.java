package com.jantenna.repository;

import com.jantenna.GainTable;
import com.jantenna.GainTableCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only {@link GainTableRepository} backed by classpath resources.
 * Patterns shipped inside a jar at
 * {@code classpath:antennas/<group>/<name>.gtable} are surfaced here.
 *
 * <p>This is the "defaults layer" of the layered-repo model: JAntenna's
 * own jar ships canonical pre-baked patterns ({@code swwhip}, {@code
 * const5}, {@code const17}, {@code isotrope-0dbi}) and downstream
 * consumers see them via this repository regardless of the user's
 * filesystem state.</p>
 *
 * <p>If the classpath doesn't carry any {@code antennas/} resources
 * the repository reports empty rather than failing.</p>
 */
public final class ClasspathGainTableRepository implements GainTableRepository {

    private static final String RESOURCE_PREFIX = "antennas/";
    private static final String EXT = ".gtable";

    private final ClassLoader classLoader;
    private final Map<String, GainTable> cache = new ConcurrentHashMap<>();

    public ClasspathGainTableRepository() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public ClasspathGainTableRepository(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    @Override
    public boolean contains(String name) {
        if (name == null) return false;
        String canon = FilesystemGainTableRepository.canonicalise(name);
        return classLoader.getResource(RESOURCE_PREFIX + canon + EXT) != null;
    }

    @Override
    public GainTable load(String name) throws IOException {
        if (name == null) throw new AntennaNotInRepository("<null>");
        String canon = FilesystemGainTableRepository.canonicalise(name);
        GainTable cached = cache.get(canon);
        if (cached != null) return cached;
        URL url = classLoader.getResource(RESOURCE_PREFIX + canon + EXT);
        if (url == null) throw new AntennaNotInRepository(name);
        try (InputStream in = url.openStream()) {
            GainTable table = GainTableCodec.read(in);
            cache.put(canon, table);
            return table;
        }
    }

    @Override
    public List<String> list() {
        return Collections.emptyList();
    }
}
