package com.jantenna.repository;

import com.jantenna.GainTable;
import com.jantenna.GainTableCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Filesystem-backed {@link GainTableRepository} reading {@code .gtable}
 * files from a directory tree (hierarchical layout
 * {@code <root>/<group>/<name>.gtable}).
 *
 * <p>Pattern name resolution:</p>
 * <ul>
 *   <li>{@code "default/swwhip"}        -&gt; {@code <root>/default/swwhip.gtable}</li>
 *   <li>{@code "default/SWWHIP.VOA"}    -&gt; {@code <root>/default/swwhip.gtable} (extension stripped, lowercased)</li>
 *   <li>{@code "itur705/141-multifreq"} -&gt; {@code <root>/itur705/141-multifreq.gtable}</li>
 * </ul>
 *
 * <p>All names are canonicalised to lowercase (locale-independent).
 * Loaded {@link GainTable}s are cached per JVM lifetime - disk is
 * touched once per pattern.  Cache is keyed on the canonical name and
 * is thread-safe.</p>
 */
public final class FilesystemGainTableRepository implements GainTableRepository {

    private static final String EXT = ".gtable";

    private final Path root;
    private final Map<String, GainTable> cache = new ConcurrentHashMap<>();

    public FilesystemGainTableRepository(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Override
    public boolean contains(String name) {
        if (name == null) return false;
        return Files.exists(resolveFile(name));
    }

    @Override
    public GainTable load(String name) throws IOException {
        if (name == null) throw new AntennaNotInRepository("<null>");
        String canon = canonicalise(name);
        GainTable cached = cache.get(canon);
        if (cached != null) return cached;
        Path file = root.resolve(canon + EXT);
        if (!Files.exists(file)) {
            throw new AntennaNotInRepository(name);
        }
        GainTable table = GainTableCodec.read(file);
        cache.put(canon, table);
        return table;
    }

    @Override
    public List<String> list() throws IOException {
        if (!Files.exists(root) || !Files.isDirectory(root)) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(EXT))
                  .forEach(p -> {
                      Path rel = root.relativize(p);
                      String name = rel.toString().replace('\\', '/');
                      name = name.substring(0, name.length() - EXT.length());
                      names.add(name);
                  });
        }
        Collections.sort(names);
        return names;
    }

    /** Drop the cache; next load() will re-read from disk. */
    public void invalidateCache() {
        cache.clear();
    }

    public Path root() {
        return root;
    }

    private Path resolveFile(String name) {
        return root.resolve(canonicalise(name) + EXT);
    }

    /**
     * Canonicalise a pattern reference: strip any extension and
     * lowercase (locale-independent).  Forward slashes only.
     * @param name
     * @return 
     */
    public static String canonicalise(String name) {
        String s = name.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        int dot   = s.lastIndexOf('.');
        if (dot > slash) {
            s = s.substring(0, dot);
        }
        return s.toLowerCase(Locale.ROOT);
    }
}
