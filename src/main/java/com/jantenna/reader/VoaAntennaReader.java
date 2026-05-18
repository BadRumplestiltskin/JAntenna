package com.jantenna.reader;

import com.jantenna.GainTable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reader for VOACAPL-native antenna pattern files ({@code .voa}).
 *
 * <p>{@link #readGainTable(Path)} is the canonical public entry point:
 * it dispatches by the on-disk Antenna Type field and returns a
 * unified {@link GainTable} regardless of source format.  Type-specific
 * package-private parsers ({@link #read}, {@link #readType13}) are
 * retained for tests and intra-package bakers.</p>
 *
 * <p><b>Format</b></p>
 * <pre>
 * Line 1:  &lt;free-text description&gt;
 * Line 2:  &lt;nParams&gt;  &lt;nParams&gt; parameters        (e.g. " 3     3 parameters")
 * Lines 3..(2+nParams):  one parameter per line
 *          &lt;value&gt; [nn] &lt;description&gt;
 *          where parameter 1 is "Max Gain dBi"
 *                parameter 2 is "Antenna Type" (0, 11, 13, ...)
 *                parameter 3 onwards is type-specific (efficiency, frequency)
 * Remaining lines: whitespace-separated gain values in dBi.
 *   Type 0  (isotrope):  no gain values - uniform Max Gain.
 *   Type 11 (1D elev):   91 values at 1 deg increments (0..90).
 *   Type 13 (2D az/el):  360 azimuth blocks of 91 elevation values.
 * </pre>
 */
public final class VoaAntennaReader {

    /** Antenna type 0: isotropic / constant-gain radiator. */
    public static final int TYPE_ISOTROPIC = 0;
    /** Antenna type 11: 91 values gain-vs-elevation (0..90 deg). */
    public static final int TYPE_91_ELEVATION_GAINS = 11;
    /** Antenna type 13: 360 x 91 azimuth x elevation gain grid. */
    public static final int TYPE_360_BY_91 = 13;

    private static final int PATTERN_POINTS = 91;

    private VoaAntennaReader() { }

    /**
     * Parse a {@code .voa} file from disk and return the unified
     * {@link GainTable}.  Dispatches internally by the on-disk
     * Antenna Type field.
     */
    public static GainTable readGainTable(Path voaPath) throws IOException {
        Objects.requireNonNull(voaPath, "voaPath must not be null");
        try (BufferedReader r = new BufferedReader(new FileReader(voaPath.toFile()))) {
            return readGainTable(r, voaPath.getFileName().toString());
        }
    }

    /** Reader-based overload of {@link #readGainTable(Path)}. */
    public static GainTable readGainTable(Reader reader, String sourceLabel) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        Header h = parseHeader(br, sourceLabel);
        return switch (h.antennaType) {
            case TYPE_ISOTROPIC ->
                GainTableFactory.fromIsotrope(h.maxGainDbi, h.designFrequency);
            case TYPE_91_ELEVATION_GAINS ->
                GainTableFactory.fromType11Elevations(
                    readGainArray(br, sourceLabel), h.maxGainDbi, h.designFrequency);
            case TYPE_360_BY_91 ->
                GainTableFactory.fromType13AzEl(
                    readType13Grid(br, sourceLabel), h.designFrequency);
            default ->
                throw new IOException(sourceLabel + ": unsupported antenna type "
                    + h.antennaType);
        };
    }

    /**
     * Package-private parser for Type 0 / 11 sources.  Returns the
     * legacy {@link AntennaModel} with absolute-dBi gains (max-fold
     * applied here).  Tests and intra-package converters may call
     * this; external consumers should use {@link #readGainTable}.
     */
    static AntennaModel read(Path voaPath) throws IOException {
        Objects.requireNonNull(voaPath, "voaPath must not be null");
        try (BufferedReader r = new BufferedReader(new FileReader(voaPath.toFile()))) {
            return read(r, voaPath.getFileName().toString());
        }
    }

    static AntennaModel read(Reader reader, String sourceLabel) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        Header h = parseHeader(br, sourceLabel);
        double[] gains = switch (h.antennaType) {
            case TYPE_ISOTROPIC          -> new double[PATTERN_POINTS];
            case TYPE_91_ELEVATION_GAINS -> readGainArray(br, sourceLabel);
            default -> throw new IOException(sourceLabel + ": unsupported antenna type "
                    + h.antennaType + " (only types " + TYPE_ISOTROPIC
                    + " and " + TYPE_91_ELEVATION_GAINS + " handled by read())");
        };
        for (int i = 0; i < gains.length; i++) gains[i] = h.maxGainDbi + gains[i];
        return new AntennaModel(h.designFrequency, gains);
    }

    /** Package-private parser for Type 13 sources. */
    static AntennaModel2D readType13(Path voaPath) throws IOException {
        Objects.requireNonNull(voaPath, "voaPath must not be null");
        try (BufferedReader r = new BufferedReader(new FileReader(voaPath.toFile()))) {
            return readType13(r, voaPath.getFileName().toString());
        }
    }

    static AntennaModel2D readType13(Reader reader, String sourceLabel) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        Header h = parseHeader(br, sourceLabel);
        if (h.antennaType != TYPE_360_BY_91) {
            throw new IOException(sourceLabel + ": expected Antenna Type "
                    + TYPE_360_BY_91 + ", got " + h.antennaType
                    + " - use read() for Type 0 / 11 patterns");
        }
        return new AntennaModel2D(h.maxGainDbi, h.designFrequency,
                readType13Grid(br, sourceLabel));
    }

    public static boolean exists(Path p) {
        return p != null && Files.exists(p) && Files.isRegularFile(p);
    }

    private record Header(double maxGainDbi, int antennaType, double designFrequency) { }

    private static Header parseHeader(BufferedReader br, String sourceLabel) throws IOException {
        String description = br.readLine();
        if (description == null) throw new IOException("empty .voa file: " + sourceLabel);

        String countLine = br.readLine();
        if (countLine == null) throw new IOException(sourceLabel + ": missing parameter-count line");
        int nParams = parseIntHeader(countLine, sourceLabel, "parameter-count");

        double maxGainDbi = 0.0;
        int antennaType = TYPE_ISOTROPIC;
        double designFrequency = 0.0;
        for (int i = 0; i < nParams; i++) {
            String paramLine = br.readLine();
            if (paramLine == null) {
                throw new IOException(sourceLabel + ": missing parameter line " + (i + 1));
            }
            String numericHead = paramLine.trim().split("\\s+")[0];
            double value;
            try {
                value = Double.parseDouble(numericHead);
            } catch (NumberFormatException e) {
                throw new IOException(sourceLabel + ": parameter " + (i + 1)
                        + " leading token not a number: \"" + numericHead + "\"", e);
            }
            switch (i) {
                case 0 -> maxGainDbi = value;
                case 1 -> antennaType = (int) value;
                case 2 -> { }
                case 3 -> designFrequency = value;
                default -> { }
            }
        }
        return new Header(maxGainDbi, antennaType, designFrequency);
    }

    private static int parseIntHeader(String line, String source, String what) throws IOException {
        String[] tokens = line.trim().split("\\s+");
        try {
            return Integer.parseInt(tokens[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IOException(source + ": cannot parse " + what + " from \"" + line + "\"", e);
        }
    }

    private static double[] readGainArray(BufferedReader br, String source) throws IOException {
        List<Double> values = new ArrayList<>(PATTERN_POINTS);
        String line;
        while ((line = br.readLine()) != null && values.size() < PATTERN_POINTS) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            for (String tok : trimmed.split("\\s+")) {
                if (tok.isEmpty()) continue;
                try {
                    values.add(Double.valueOf(tok));
                } catch (NumberFormatException e) {
                    throw new IOException(source + ": non-numeric gain value \""
                            + tok + "\" in pattern body", e);
                }
                if (values.size() >= PATTERN_POINTS) break;
            }
        }
        if (values.size() != PATTERN_POINTS) {
            throw new IOException(source + ": expected " + PATTERN_POINTS
                    + " pattern values, got " + values.size());
        }
        double[] out = new double[PATTERN_POINTS];
        for (int i = 0; i < PATTERN_POINTS; i++) out[i] = values.get(i);
        return out;
    }

    private static double[][] readType13Grid(BufferedReader br, String sourceLabel)
            throws IOException {
        double[][] absoluteGains = new double[AntennaModel2D.AZIMUTH_POINTS]
                                              [AntennaModel2D.ELEVATION_POINTS];
        TokenStream tokens = new TokenStream(br);
        for (int az = 0; az < AntennaModel2D.AZIMUTH_POINTS; az++) {
            String azLabel = tokens.next();
            if (azLabel == null) {
                throw new IOException(sourceLabel + ": EOF mid-pattern at azimuth row " + az);
            }
            int actualAz;
            try {
                actualAz = Integer.parseInt(azLabel);
            } catch (NumberFormatException e) {
                throw new IOException(sourceLabel + ": expected integer azimuth label at "
                        + "row " + az + ", got \"" + azLabel + "\"", e);
            }
            if (actualAz != az) {
                throw new IOException(sourceLabel + ": azimuth row " + az
                        + " disordered - file declares it as " + actualAz);
            }
            for (int el = 0; el < AntennaModel2D.ELEVATION_POINTS; el++) {
                String tok = tokens.next();
                if (tok == null) {
                    throw new IOException(sourceLabel + ": EOF inside azimuth " + az
                            + " at elevation " + el);
                }
                try {
                    absoluteGains[az][el] = Double.parseDouble(tok);
                } catch (NumberFormatException e) {
                    throw new IOException(sourceLabel + ": non-numeric gain at (az=" + az
                            + ", el=" + el + "): \"" + tok + "\"", e);
                }
            }
        }
        return absoluteGains;
    }

    /**
     * Whitespace-tokeniser that handles voacapl's f7.3 fixed-format
     * output, where negative values consume the full 7-char field
     * and run together without intervening whitespace.  We insert a
     * delimiter before any {@code -} that follows a digit or decimal
     * point so jammed runs decompose into individual numeric tokens.
     */
    private static final class TokenStream {
        private final BufferedReader br;
        private String[] pending = new String[0];
        private int idx;

        TokenStream(BufferedReader br) { this.br = br; }

        String next() throws IOException {
            while (idx >= pending.length) {
                String line = br.readLine();
                if (line == null) return null;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String normalized = trimmed.replaceAll("(?<=[0-9.])-", " -");
                pending = normalized.split("\\s+");
                idx = 0;
            }
            return pending[idx++];
        }
    }
}
