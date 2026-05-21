package com.jantenna.baker;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects an antenna source file's voacapl {@code jant} type and routes
 * to the appropriate {@link AntennaBaker}.  Reads only the file header
 * (description line + parameter block) — no body parsing here.
 *
 * <p>The dispatcher knows about file-sourced bakers only.  Analytical
 * antennas (isotrope, HFMUFES KOP, CCIR jant) are invoked directly
 * via dedicated CLI commands (e.g.
 * {@code jvoacap-antennas bake-analytical}) that instantiate the
 * matching baker with explicit params.</p>
 */
public final class BakerDispatcher {

    private BakerDispatcher() { }

    /**
     * Read the {@code parm(2)} (jant) value from a {@code .voa}-format
     * source file and return the matching baker.
     *
     * @throws IOException on read errors or unrecognised jant
     */
    public static AntennaBaker forFile(Path source) throws IOException {
        int jant = peekJant(source);
        return switch (jant) {
            case 0  -> new Type11Baker();   // jant=0 isotrope written as a 91-elev table by VoaAntennaReader
            case 11 -> new Type11Baker();
            case 13 -> new Type13Baker();
            default -> throw new IOException(
                    "Unsupported jant=" + jant + " in " + source.getFileName()
                    + " (Phase B handles 0/11/13 only; CCIR + HFMUFES land in Phase D/E)");
        };
    }

    /**
     * Peek at the 2nd parameter of a voacapl-format antenna file header
     * to extract the {@code jant} (antenna type) field.  The file format
     * (per {@code wp10dwin/readant.for:32-36}):
     * <pre>
     *   line 1: description
     *   line 2: nParams
     *   line 3..(2+nParams): "&lt;value&gt; [&lt;index&gt;] &lt;description&gt;"
     * </pre>
     * {@code jant} is parameter 2 — the leading number on line 4.
     */
    static int peekJant(Path source) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(source)) {
            String description = br.readLine();
            String countLine = br.readLine();
            if (description == null || countLine == null) {
                throw new IOException("Truncated antenna header in " + source);
            }
            int nParams;
            try {
                nParams = Integer.parseInt(countLine.trim().split("\\s+")[0]);
            } catch (NumberFormatException nfe) {
                throw new IOException("Cannot parse param-count from \""
                        + countLine + "\" in " + source, nfe);
            }
            if (nParams < 2) {
                throw new IOException("Antenna file " + source + " declares only "
                        + nParams + " parameters; need at least 2 to read jant");
            }
            // Skip param 1 (Max Gain), read param 2 (jant)
            String maxGainLine = br.readLine();
            String jantLine = br.readLine();
            if (maxGainLine == null || jantLine == null) {
                throw new IOException("Truncated param block in " + source);
            }
            try {
                return Integer.parseInt(jantLine.trim().split("\\s+")[0]);
            } catch (NumberFormatException nfe) {
                throw new IOException("Cannot parse jant from \""
                        + jantLine + "\" in " + source, nfe);
            }
        }
    }
}
