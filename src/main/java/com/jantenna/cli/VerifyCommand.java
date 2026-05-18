package com.jantenna.cli;

import com.jantenna.AntennaMetadata;
import com.jantenna.BakerVersion;
import com.jantenna.MetadataCodec;
import com.jantenna.Sha256;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code jvoacap antennas verify} — drift check per
 * {@code docs/antennas.md} §5.4.  Walks the repository, recomputes
 * SHA-256 of each source file, reports mismatches.  Exits 0 on clean,
 * 2 on any drift detected.
 */
final class VerifyCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    VerifyCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        if (!Files.exists(repoRoot)) {
            out.println("Repository " + repoRoot + " does not exist (nothing to verify)");
            return 0;
        }
        List<String> reports = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".json"))
                  .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                  .forEach(metaFile -> {
                      try {
                          AntennaMetadata m = MetadataCodec.read(metaFile);
                          String label = (m.group() != null ? m.group() + "/" : "") + m.name();
                          if (m.source() != null && m.source().path() != null) {
                              Path src = Path.of(m.source().path());
                              if (!Files.exists(src)) {
                                  reports.add(label + ": source missing (" + src + ")");
                              } else {
                                  String currentSha = Sha256.ofFile(src);
                                  if (m.source().sha256() != null
                                          && !currentSha.equalsIgnoreCase(m.source().sha256())) {
                                      reports.add(label + ": source changed (sha mismatch)");
                                  }
                              }
                          }
                          if (m.baker() != null
                                  && m.baker().sha() != null
                                  && !m.baker().sha().equals(BakerVersion.MODULE_SHA)) {
                              reports.add(label + ": baker changed (recorded "
                                      + m.baker().sha() + ", running " + BakerVersion.MODULE_SHA + ")");
                          }
                      } catch (IOException e) {
                          reports.add(metaFile + ": cannot read metadata (" + e.getMessage() + ")");
                      }
                  });
        }
        if (reports.isEmpty()) {
            out.println("OK: all patterns in " + repoRoot + " are up-to-date");
            return 0;
        }
        err.println("DRIFT DETECTED:");
        for (String r : reports) err.println("  " + r);
        err.println();
        err.println("Re-run `jvoacap-antennas bake` on the affected source files.");
        return 2;
    }
}
