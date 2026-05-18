package com.jantenna.cli;

import com.jantenna.repository.FilesystemGainTableRepository;
import com.jantenna.GainTable;
import com.jantenna.AntennaMetadata;
import com.jantenna.MetadataCodec;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code jvoacap antennas show <pattern-name>} — print metadata + grid
 * summary + a handful of sample gains for one pattern.
 */
final class ShowCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    ShowCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        if (args.length < 1) {
            err.println("Usage: show <pattern-name>");
            return 1;
        }
        String name = args[0];
        // canonical resolution mirrors FilesystemGainTableRepository
        String canon = FilesystemGainTableRepository.canonicalise(name);
        Path meta   = repoRoot.resolve(canon + ".json");
        Path gtable = repoRoot.resolve(canon + ".gtable");
        if (!Files.exists(gtable)) {
            err.println("Pattern not in repository: " + name);
            return 1;
        }
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(repoRoot);
        GainTable table = repo.load(name);
        out.println("Pattern:    " + canon);
        out.println("Tensor:     F=" + table.frequencyCount()
                + "  A=" + table.azimuthCount()
                + "  E=" + table.elevationCount());
        out.println("Bytes:      " + (table.gainsCentiDb().length * 2L) + " (in-memory)");
        if (Files.exists(meta)) {
            AntennaMetadata m = MetadataCodec.read(meta);
            out.println("Kind:       " + (m.source() != null ? m.source().kind() : "?"));
            out.println("Description:" + (m.description() != null ? " " + m.description() : ""));
            out.println("Peak gain:  " + m.peakGainDbi() + " dBi");
            out.println("Freq range: " + m.minFreqMhz() + " .. " + m.maxFreqMhz() + " MHz");
            if (m.baker() != null) {
                out.println("Baker:      " + m.baker().module() + " (v" + m.baker().version()
                        + " sha=" + m.baker().sha() + ")");
            }
            if (m.bakedAt() != null) out.println("Baked at:   " + m.bakedAt());
            if (m.source() != null && m.source().sha256() != null) {
                out.println("Source sha: " + m.source().sha256());
            }
        }
        out.println();
        out.println("Sample gains (freq=" + table.frequenciesMHz()[0] + " MHz):");
        out.println(String.format("  el= 0°  %+.2f dBi", table.gainDbi(table.frequenciesMHz()[0], 0, 0)));
        out.println(String.format("  el=10°  %+.2f dBi", table.gainDbi(table.frequenciesMHz()[0], 0, 10)));
        out.println(String.format("  el=30°  %+.2f dBi", table.gainDbi(table.frequenciesMHz()[0], 0, 30)));
        out.println(String.format("  el=60°  %+.2f dBi", table.gainDbi(table.frequenciesMHz()[0], 0, 60)));
        out.println(String.format("  el=90°  %+.2f dBi", table.gainDbi(table.frequenciesMHz()[0], 0, 90)));
        return 0;
    }
}
