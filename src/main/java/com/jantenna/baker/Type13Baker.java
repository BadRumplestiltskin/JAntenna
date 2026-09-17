package com.jantenna.baker;

import com.jantenna.AntennaMetadata;
import com.jantenna.BakerVersion;
import com.jantenna.GainTable;
import com.jantenna.Sha256;
import com.jantenna.reader.VoaAntennaReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bakes a voacapl Type-13 {@code .voa}/{@code .13}/{@code .t13} file
 * (360 x 91 azimuth x elevation grid, single frequency).
 */
public final class Type13Baker implements AntennaBaker {

    @Override
    public String sourceKind() {
        return "VOA_TYPE_13";
    }

    @Override
    public BakeResult bake(Path source, String targetGroup, String targetName) throws IOException {
        GainTable table = VoaAntennaReader.readGainTable(source);
        String description = readDescription(source);

        AntennaMetadata.Source src = new AntennaMetadata.Source(
                sourceKind(),
                source.toAbsolutePath().toString(),
                Sha256.ofFile(source),
                null);
        AntennaMetadata.Baker baker = new AntennaMetadata.Baker(
                Type13Baker.class.getSimpleName(),
                BakerVersion.MODULE_SHA,
                BakerVersion.VERSION);
        AntennaMetadata.Grid grid = new AntennaMetadata.Grid(
                table.frequenciesMHz().clone(),
                table.azimuthCount(),
                table.elevationCount(),
                true,
                false);
        double peak = table.peakDbi();
        double[] freqs = table.frequenciesMHz();
        AntennaMetadata meta = new AntennaMetadata(
                targetName, targetGroup, description, src, baker,
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                grid, peak, freqs[0], freqs[freqs.length - 1], 1);
        return new BakeResult(table, meta);
    }

    /**
     * Merge several single-frequency Type-13 files into one multi-frequency GainTable.
     * Files are sorted by design frequency; duplicate frequencies retain the last file read.
     */
    public static BakeResult bakeMulti(
            List<Path> sources, String targetGroup, String targetName) throws IOException {

        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("bakeMulti requires at least one source");
        }

        TreeMap<Double, short[]> byFreq = new TreeMap<>();
        String description = null;

        for (Path source : sources) {
            GainTable t = VoaAntennaReader.readGainTable(source);
            if (t.frequencyCount() != 1 || t.azimuthCount() != 360 || t.elevationCount() != 91) {
                throw new IOException(source.getFileName()
                        + " is not a single-freq Type-13 pattern (got "
                        + t.frequencyCount() + "F × " + t.azimuthCount()
                        + "A × " + t.elevationCount() + "E)");
            }
            byFreq.put(t.frequenciesMHz()[0], t.gainsCentiDb());
            if (description == null) description = readDescription(source);
        }

        int F = byFreq.size();
        int A = 360, E = 91;
        double[] freqs      = new double[F];
        short[]  flat       = new short[F * A * E];
        double[] azimuths   = new double[A];
        double[] elevations = new double[E];
        for (int i = 0; i < A; i++) azimuths[i]   = i;
        for (int i = 0; i < E; i++) elevations[i]  = i;

        int fi = 0;
        for (Map.Entry<Double, short[]> entry : byFreq.entrySet()) {
            freqs[fi] = entry.getKey();
            System.arraycopy(entry.getValue(), 0, flat, fi * A * E, A * E);
            fi++;
        }

        GainTable combined = new GainTable(freqs, azimuths, elevations, flat);

        AntennaMetadata.Source src = new AntennaMetadata.Source(
                "VOA_TYPE_13",
                sources.get(0).getParent().toAbsolutePath().toString(),
                null,
                null);
        AntennaMetadata.Baker baker = new AntennaMetadata.Baker(
                Type13Baker.class.getSimpleName(),
                BakerVersion.MODULE_SHA,
                BakerVersion.VERSION);
        AntennaMetadata.Grid grid = new AntennaMetadata.Grid(
                freqs.clone(), A, E, true, false);
        double peak = combined.peakDbi();
        AntennaMetadata meta = new AntennaMetadata(
                targetName, targetGroup, description, src, baker,
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                grid, peak, freqs[0], freqs[F - 1], 1);

        return new BakeResult(combined, meta);
    }

    private static String readDescription(Path source) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(source)) {
            String first = br.readLine();
            return first == null ? "" : first.strip();
        }
    }
}
