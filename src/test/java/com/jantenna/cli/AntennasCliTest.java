package com.jantenna.cli;

import com.jantenna.repository.FilesystemGainTableRepository;
import com.jantenna.GainTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the {@code jvoacap antennas} CLI per
 * {@code docs/antennas.md} §5.  Drives each sub-command via the
 * router and checks side effects on the filesystem repository.
 */
@DisplayName("AntennasCli — end-to-end sub-commands")
class AntennasCliTest {

    @Test
    @DisplayName("bake-isotrope → list → show → verify → remove round-trip")
    void isotropeRoundTrip(@TempDir Path repo) throws IOException {
        // bake-isotrope
        Captured cap = run(repo, "bake-isotrope", "iso5", "5.0", "14.15");
        assertEquals(0, cap.exit);
        assertTrue(Files.exists(repo.resolve("default/iso5.gtable")));
        assertTrue(Files.exists(repo.resolve("default/iso5.json")));
        assertTrue(Files.exists(repo.resolve("manifest.json")));

        // list
        cap = run(repo, "list");
        assertEquals(0, cap.exit);
        assertTrue(cap.stdout.contains("default/iso5"), cap.stdout);

        // show
        cap = run(repo, "show", "default/iso5");
        assertEquals(0, cap.exit);
        assertTrue(cap.stdout.contains("ANALYTICAL_ISOTROPE"), cap.stdout);
        assertTrue(cap.stdout.contains("+5.00 dBi"), cap.stdout);

        // verify (clean — no source files to drift)
        cap = run(repo, "verify");
        assertEquals(0, cap.exit);
        assertTrue(cap.stdout.contains("OK"), cap.stdout);

        // load via the runtime API works
        FilesystemGainTableRepository fsRepo = new FilesystemGainTableRepository(repo);
        GainTable t = fsRepo.load("default/iso5");
        assertEquals(5.0, t.gainDbi(14.15, 0.0, 0.0), 0.005);

        // remove
        cap = run(repo, "remove", "default/iso5");
        assertEquals(0, cap.exit);
        assertFalse(Files.exists(repo.resolve("default/iso5.gtable")));
        assertFalse(Files.exists(repo.resolve("default/iso5.json")));
    }

    @Test
    @DisplayName("bake on a Type-11 .voa source produces a working .gtable")
    void bakeType11(@TempDir Path repo, @TempDir Path src) throws IOException {
        Path source = src.resolve("const5.voa");
        Files.writeString(source, """
                Constant 5 dBi reference antenna
                 3     3 parameters
                  5.00  [ 1] Max Gain dBi..:
                  11    [ 2] Antenna Type..: 91 values gain in elevation angle follows
                  0.0   [ 3] Efficiency (for IONCAP)
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0      .0      .0      .0      .0      .0      .0      .0      .0      .0
                     .0
                """);
        Captured cap = run(repo, "bake", source.toString(), "default");
        assertEquals(0, cap.exit, cap.stderr);
        assertTrue(Files.exists(repo.resolve("default/const5.gtable")));

        // round-trip via runtime
        FilesystemGainTableRepository fsRepo = new FilesystemGainTableRepository(repo);
        GainTable t = fsRepo.load("default/const5");
        assertEquals(5.0, t.gainDbi(0.0, 0.0, 45.0), 0.05);
    }

    @Test
    @DisplayName("verify reports drift after the source file changes")
    void driftDetection(@TempDir Path repo, @TempDir Path src) throws IOException {
        Path source = src.resolve("test.voa");
        String original = """
                Test antenna
                 3     3 parameters
                  5.00  [ 1] Max Gain dBi..:
                  11    [ 2] Antenna Type..: 91 values gain in elevation angle follows
                  0.0   [ 3] Efficiency (for IONCAP)
                """ + " 0.0".repeat(91) + "\n";
        Files.writeString(source, original);
        Captured cap = run(repo, "bake", source.toString(), "default");
        assertEquals(0, cap.exit, cap.stderr);

        // verify clean
        cap = run(repo, "verify");
        assertEquals(0, cap.exit);

        // edit the source file
        Files.writeString(source, original + " // edited\n");

        // verify now reports drift
        cap = run(repo, "verify");
        assertEquals(2, cap.exit);
        assertTrue(cap.stderr.contains("source changed") || cap.stderr.contains("DRIFT"), cap.stderr);
    }

    @Test
    @DisplayName("Unknown sub-command prints usage + exits non-zero")
    void unknownSubcommand(@TempDir Path repo) {
        Captured cap = run(repo, "nope");
        assertEquals(1, cap.exit);
        assertTrue(cap.stderr.contains("Unknown") || cap.stdout.contains("Usage"), cap.stderr + cap.stdout);
    }

    @Test
    @DisplayName("--help prints usage and exits 0")
    void help(@TempDir Path repo) {
        Captured cap = run(repo, "--help");
        assertEquals(0, cap.exit);
        assertTrue(cap.stdout.contains("Sub-commands"), cap.stdout);
    }

    @Test
    @DisplayName("bake-dir batch-bakes every recognised file under a directory")
    void bakeDir(@TempDir Path repo, @TempDir Path src) throws IOException {
        for (String name : new String[]{ "a.voa", "b.voa" }) {
            Files.writeString(src.resolve(name), """
                    Antenna %s
                     3     3 parameters
                      0.00  [ 1] Max Gain dBi..:
                      11    [ 2] Antenna Type..: 91 values gain in elevation angle follows
                      0.0   [ 3] Efficiency (for IONCAP)
                    """.formatted(name) + " 0.0".repeat(91) + "\n");
        }
        Captured cap = run(repo, "bake-dir", src.toString(), "default");
        assertEquals(0, cap.exit, cap.stderr);
        assertTrue(Files.exists(repo.resolve("default/a.gtable")));
        assertTrue(Files.exists(repo.resolve("default/b.gtable")));
    }

    // ── Test helpers ────────────────────────────────────────────────────

    private record Captured(int exit, String stdout, String stderr) { }

    private static Captured run(Path repoRoot, String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
        AntennasCli cli = new AntennasCli(out, err, repoRoot);
        int exit = cli.run(args);
        return new Captured(exit, outBuf.toString(StandardCharsets.UTF_8),
                                  errBuf.toString(StandardCharsets.UTF_8));
    }
}
