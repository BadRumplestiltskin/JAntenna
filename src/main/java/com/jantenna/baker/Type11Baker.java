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

/**
 * Bakes a voacapl Type-0/11 {@code .voa} file (single elevation
 * pattern, 91 gain values at 1 deg steps).
 */
public final class Type11Baker implements AntennaBaker {

    @Override
    public String sourceKind() {
        return "VOA_TYPE_11";
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
                Type11Baker.class.getSimpleName(),
                BakerVersion.MODULE_SHA,
                BakerVersion.VERSION);
        AntennaMetadata.Grid grid = new AntennaMetadata.Grid(
                table.frequenciesMHz().clone(),
                table.azimuthCount(),
                table.elevationCount(),
                false,
                false);
        double peak = table.peakDbi();
        double[] freqs = table.frequenciesMHz();
        AntennaMetadata meta = new AntennaMetadata(
                targetName, targetGroup, description, src, baker,
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                grid, peak, freqs[0], freqs[freqs.length - 1], 1);
        return new BakeResult(table, meta);
    }

    private static String readDescription(Path source) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(source)) {
            String first = br.readLine();
            return first == null ? "" : first.strip();
        }
    }

}
