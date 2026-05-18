package com.jantenna.baker;

import com.jantenna.AntennaMetadata;
import com.jantenna.BakerVersion;
import com.jantenna.GainTable;
import com.jantenna.reader.GainTableFactory;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Bakes an analytical isotrope antenna (jant 0).  No source file —
 * just a max-gain value and a design frequency.  Produces a 1×1×1
 * {@link GainTable}.
 */
public final class IsotropeBaker implements AntennaBaker {

    private final double maxGainDbi;
    private final double designFreqMHz;

    public IsotropeBaker(double maxGainDbi, double designFreqMHz) {
        this.maxGainDbi = maxGainDbi;
        this.designFreqMHz = designFreqMHz;
    }

    @Override
    public String sourceKind() {
        return "ANALYTICAL_ISOTROPE";
    }

    @Override
    public BakeResult bake(Path source, String targetGroup, String targetName) {
        // source path ignored — no input file for isotrope
        GainTable table = GainTableFactory.fromIsotrope(maxGainDbi, designFreqMHz);

        String paramsJson = String.format(
                "{\"maxGainDbi\":%s,\"designFreqMHz\":%s}",
                maxGainDbi, designFreqMHz);
        AntennaMetadata.Source src = new AntennaMetadata.Source(
                sourceKind(), null, null, paramsJson);
        AntennaMetadata.Baker baker = new AntennaMetadata.Baker(
                IsotropeBaker.class.getSimpleName(),
                BakerVersion.MODULE_SHA,
                BakerVersion.VERSION);
        AntennaMetadata.Grid grid = new AntennaMetadata.Grid(
                new double[]{ designFreqMHz }, 1, 1, false, false);
        AntennaMetadata meta = new AntennaMetadata(
                targetName, targetGroup,
                String.format("Isotrope @ %.2f dBi", maxGainDbi),
                src, baker,
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                grid, maxGainDbi, designFreqMHz, designFreqMHz, 1);
        return new BakeResult(table, meta);
    }
}
