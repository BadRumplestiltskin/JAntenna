package com.jantenna.gui.bake;

import com.jantenna.PathNames;
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

    /** Row index signalling "not a source file row" — appended instead of updated. */
    private static final int WARNING_ROW = -1;

    private Path         destination;
    private List<String> warnings = List.of();

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
                        PathNames.stem(source));
                results.put(source, result);
                publish(new Object[]{ rowByPath.get(source), "⏳", "Baked" });
            } catch (Exception ex) {
                publish(new Object[]{ rowByPath.get(source), "✗", ex.getMessage() });
            }
            setProgress((int)(++done * 90.0 / total));
            publishProgressText(done + " of " + total + " \u2014 " + source.getFileName());
        }

        if (results.isEmpty() || isCancelled()) return null;

        // Phase 2: merge all successfully baked tables by frequency and write
        List<Path> successful = new ArrayList<>(results.keySet());
        publishProgressText("Merging " + successful.size() + " table(s)\u2026");
        for (Path p : successful) publish(new Object[]{ rowByPath.get(p), "⏳", "Merging…" });

        try {
            List<GainTable> tables = results.values().stream()
                    .map(AntennaBaker.BakeResult::table).toList();
            List<String> labels = successful.stream()
                    .map(p -> p.getFileName().toString()).toList();
            MergeResult mergeResult = mergeByFrequency(tables, labels);
            GainTable merged = mergeResult.table();

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

                // Show the full destination so the group subdirectory is never a surprise.
                String msg = "→ " + gtablePath.toAbsolutePath();
                for (Path p : successful) publish(new Object[]{ rowByPath.get(p), "✓", msg });

                for (String warning : mergeResult.warnings()) {
                    publish(new Object[]{ WARNING_ROW, "⚠", warning });
                }
                destination = gtablePath.toAbsolutePath();
                warnings    = mergeResult.warnings();
            }
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            for (Path p : successful) publish(new Object[]{ rowByPath.get(p), "✗", msg });
        }

        setProgress(100);
        return null;
    }

    /**
     * Result of merging source tables: the combined table plus any non-fatal
     * warnings the user should see (e.g. duplicate frequencies).
     */
    record MergeResult(GainTable table, List<String> warnings) {}

    /**
     * Merges single-frequency GainTables into one multi-frequency table, sorted
     * ascending by frequency. All tables must share the same azimuth and
     * elevation axes.
     *
     * <p>When two sources carry the same frequency only the last one survives;
     * that is reported as a warning rather than dropped silently.
     *
     * @param tables  source tables, parallel to {@code labels}
     * @param labels  display names for the sources, used in warning messages
     */
    static MergeResult mergeByFrequency(List<GainTable> tables, List<String> labels) {
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
                        "Incompatible grid shapes: expected " + A + "\u00d7" + E
                        + ", got " + t.azimuthCount() + "\u00d7" + t.elevationCount());
        }

        TreeMap<Double, short[]> byFreq  = new TreeMap<>();
        TreeMap<Double, String>  ownerOf = new TreeMap<>();
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < tables.size(); i++) {
            GainTable t = tables.get(i);
            String label = i < labels.size() ? labels.get(i) : "source " + i;
            double freq = t.frequenciesMHz()[0];
            String previous = ownerOf.put(freq, label);
            if (previous != null) {
                warnings.add(String.format(
                        "%.4f MHz: %s overrides %s (duplicate frequency)",
                        freq, label, previous));
            }
            byFreq.put(freq, t.gainsCentiDb());
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
        return new MergeResult(new GainTable(freqs, azimuths, elevations, flat), warnings);
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
        double peak = merged.peakDbi();
        return new AntennaMetadata(
                name, group, representative.description(),
                src, representative.baker(),
                BakerVersion.JANTENNA_VERSION, Instant.now(),
                grid, peak, freqs[0], freqs[freqs.length - 1], 1);
    }


    @Override
    protected void process(List<Object[]> chunks) {
        for (Object[] chunk : chunks) {
            int row = (int) chunk[0];
            if (row == WARNING_ROW) {
                tableModel.addRow("(merge)", (String) chunk[1], (String) chunk[2]);
            } else {
                tableModel.updateRow(row, (String) chunk[1], (String) chunk[2]);
            }
        }
    }

    @Override
    protected void done() {
        bakeAllButton.setEnabled(true);
        progressBar.setValue(progressBar.getMaximum());
        progressBar.setString(destination != null
                ? "Done \u2014 " + destination.getFileName()
                : "Failed");
    }

/** Absolute path of the combined table written, or {@code null} if the write failed. */
    public Path destination() { return destination; }

    /** Non-fatal merge warnings, e.g. duplicate frequencies. Never {@code null}. */
    public List<String> warnings() { return warnings; }

/** Updates the progress bar caption on the EDT. */
    private void publishProgressText(String text) {
        SwingUtilities.invokeLater(() -> progressBar.setString(text));
    }

}
