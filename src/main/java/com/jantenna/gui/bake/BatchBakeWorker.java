package com.jantenna.gui.bake;

import com.jantenna.AntennaMetadata;
import com.jantenna.BakerVersion;
import com.jantenna.GainTable;
import com.jantenna.GainTableCodec;
import com.jantenna.MetadataCodec;
import com.jantenna.baker.AntennaBaker;
import com.jantenna.baker.BakerDispatcher;
import com.jantenna.baker.ManifestWriter;
import com.jantenna.baker.RepositoryLock;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BatchBakeWorker extends SwingWorker<Void, Object[]> {

    private final List<Path>             sourceFiles;
    private final Path                   repoRoot;
    private final String                 group;
    private final String                 combinedName;
    private final JButton                bakeAllButton;
    private final JProgressBar           progressBar;
    private final BatchResultTableModel  tableModel;

    public BatchBakeWorker(List<Path> sourceFiles, Path repoRoot, String group,
                           String combinedName,
                           JButton bakeAllButton, JProgressBar progressBar,
                           BatchResultTableModel tableModel) {
        this.sourceFiles  = sourceFiles;
        this.repoRoot     = repoRoot;
        this.group        = group;
        this.combinedName = combinedName;
        this.bakeAllButton = bakeAllButton;
        this.progressBar  = progressBar;
        this.tableModel   = tableModel;
    }

    @Override
    protected Void doInBackground() throws Exception {
        Map<Path, Integer> rowByPath = new LinkedHashMap<>();
        for (int i = 0; i < sourceFiles.size(); i++) {
            rowByPath.put(sourceFiles.get(i), i);
        }

        int total = sourceFiles.size();
        int done  = 0;

        // Phase 1: bake each file with whatever baker it needs
        Map<Path, AntennaBaker.BakeResult> results = new LinkedHashMap<>();
        for (Path source : sourceFiles) {
            if (isCancelled()) break;
            publish(new Object[]{ rowByPath.get(source), "⏳", "Baking…" });
            try {
                AntennaBaker baker = BakerDispatcher.forFile(source);
                AntennaBaker.BakeResult result = baker.bake(source, group,
                        stripExtension(source.getFileName().toString()));
                results.put(source, result);
                publish(new Object[]{ rowByPath.get(source), "⏳", "Baked" });
            } catch (Exception ex) {
                publish(new Object[]{ rowByPath.get(source), "✗", ex.getMessage() });
            }
            setProgress((int)(++done * 90.0 / total));
        }

        if (results.isEmpty() || isCancelled()) return null;

        // Phase 2: merge all successfully baked tables by frequency and write
        List<Path> successful = new ArrayList<>(results.keySet());
        for (Path p : successful) publish(new Object[]{ rowByPath.get(p), "⏳", "Merging…" });

        try {
            List<GainTable> tables = results.values().stream()
                    .map(AntennaBaker.BakeResult::table).toList();
            GainTable merged = mergeByFrequency(tables);

            AntennaMetadata firstMeta = results.values().iterator().next().metadata();
            AntennaMetadata meta = buildCombinedMeta(merged, firstMeta,
                    successful.get(0).getParent().toAbsolutePath().toString(),
                    group, combinedName);

            try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
                Path groupDir   = repoRoot.resolve(group);
                Files.createDirectories(groupDir);
                Path gtablePath = groupDir.resolve(combinedName + ".gtable");
                Path metaPath   = groupDir.resolve(combinedName + ".json");

                GainTableCodec.writeAtomic(merged, gtablePath);
                MetadataCodec.writeAtomic(meta, metaPath);
                ManifestWriter.regenerate(repoRoot);

                String msg = "→ " + gtablePath.getFileName();
                for (Path p : successful) publish(new Object[]{ rowByPath.get(p), "✓", msg });
            }
        } catch (Exception ex) {
            String msg = ex.getMessage();
            for (Path p : successful) publish(new Object[]{ rowByPath.get(p), "✗", msg });
        }

        setProgress(100);
        return null;
    }

    /**
     * Merges a list of single-frequency GainTables into one multi-frequency table,
     * sorted ascending by frequency. All tables must share the same azimuth and
     * elevation axes. Duplicate frequencies retain the last entry.
     */
    private static GainTable mergeByFrequency(List<GainTable> tables) {
        if (tables.isEmpty()) throw new IllegalArgumentException("No tables to merge");

        double[] azimuths   = tables.get(0).azimuthsDeg();
        double[] elevations = tables.get(0).elevationsDeg();
        int A = azimuths.length, E = elevations.length;

        for (GainTable t : tables) {
            if (t.frequencyCount() != 1) throw new IllegalArgumentException(
                    "Each source must be a single-frequency table; got "
                    + t.frequencyCount() + " frequencies");
            if (t.azimuthCount() != A || t.elevationCount() != E)
                throw new IllegalArgumentException(
                        "Incompatible grid shapes: expected " + A + "×" + E
                        + ", got " + t.azimuthCount() + "×" + t.elevationCount());
        }

        TreeMap<Double, short[]> byFreq = new TreeMap<>();
        for (GainTable t : tables) {
            byFreq.put(t.frequenciesMHz()[0], t.gainsCentiDb().clone());
        }

        int F = byFreq.size();
        double[] freqs = new double[F];
        short[]  flat  = new short[F * A * E];
        int fi = 0;
        for (Map.Entry<Double, short[]> entry : byFreq.entrySet()) {
            freqs[fi] = entry.getKey();
            System.arraycopy(entry.getValue(), 0, flat, fi * A * E, A * E);
            fi++;
        }
        return new GainTable(freqs, azimuths, elevations, flat);
    }

    private static AntennaMetadata buildCombinedMeta(
            GainTable merged, AntennaMetadata representative,
            String folderPath, String group, String name) {
        double[] freqs = merged.frequenciesMHz();
        AntennaMetadata.Source src = new AntennaMetadata.Source(
                representative.source().kind(), folderPath, null, null);
        AntennaMetadata.Grid grid = new AntennaMetadata.Grid(
                freqs.clone(),
                merged.azimuthCount(),
                merged.elevationCount(),
                merged.azimuthCount() > 1,
                freqs.length > 1);
        double peak = computePeak(merged);
        return new AntennaMetadata(
                name, group, representative.description(),
                src, representative.baker(),
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                grid, peak, freqs[0], freqs[freqs.length - 1], 1);
    }

    private static double computePeak(GainTable t) {
        double peak = Double.NEGATIVE_INFINITY;
        for (short v : t.gainsCentiDb()) {
            if (v != GainTable.SENTINEL_CENTI_DB) {
                double g = v / 100.0;
                if (g > peak) peak = g;
            }
        }
        return Double.isInfinite(peak) ? 0.0 : peak;
    }

    @Override
    protected void process(List<Object[]> chunks) {
        for (Object[] chunk : chunks) {
            tableModel.updateRow((int) chunk[0], (String) chunk[1], (String) chunk[2]);
        }
    }

    @Override
    protected void done() {
        bakeAllButton.setEnabled(true);
        progressBar.setValue(progressBar.getMaximum());
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
