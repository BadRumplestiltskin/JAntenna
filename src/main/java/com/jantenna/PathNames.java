package com.jantenna;

import java.nio.file.Path;

/**
 * Filename conventions shared by every bake entry point.
 *
 * <p>The stem of a source file becomes the pattern name when the caller does
 * not supply one, so CLI commands, the GUI bake panels and the defaults baker
 * must all derive it the same way.</p>
 */
public final class PathNames {

    private PathNames() {}

    /**
     * Filename without its extension: {@code /a.b/swwhip.voa} yields
     * {@code swwhip}. A dot in a parent directory is ignored, and a leading dot
     * is kept (a name that is all extension has no stem to take).
     */
    public static String stem(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : stem(name.toString());
    }

    /** As {@link #stem(Path)}, for a bare filename with no directory part. */
    public static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
