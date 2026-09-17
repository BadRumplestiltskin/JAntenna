package com.jantenna.baker;

import com.jantenna.PathNames;
import com.jantenna.MetadataCodec;

import com.jantenna.GainTableCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Build-time bake of the canonical default catalogue per
 * {@code docs/antennas.md} §5 (locked S5: voacapl's three Type-11
 * antennas — SWWHIP, const5, const17).
 *
 * <p>Invoked by Maven at the {@code process-classes} phase via
 * {@code exec-maven-plugin}.  Output lands under
 * {@code target/generated-resources/antennas/default/} which is
 * declared as an additional resource root in {@code pom.xml}, so
 * the baked patterns ship inside the jar as
 * {@code classpath:antennas/default/*.gtable}.</p>
 *
 * <p>This is the bootstrap that makes the
 * {@link com.voacap.antenna.ClasspathGainTableRepository} non-empty
 * at runtime.  Cards referencing {@code [default/SWWHIP.VOA]} resolve
 * through the {@link com.voacap.antenna.GainTable} path (Tier 1)
 * rather than falling through to legacy {@link com.voacap.antenna.AntennaModel}
 * parsing.</p>
 *
 * <p>If the voacapl source tree isn't checked out at the expected
 * path, this app exits 0 with a warning and the defaults layer ships
 * empty — runtime fall-back still produces the same predictions via
 * Tier 2 (legacy file load from {@code ~/itshfbc/antennas/}).</p>
 *
 * <p>Configuration via system properties:</p>
 * <ul>
 *   <li>{@code -Djantenna.defaults.voacaplRoot=/path/to/voacapl/itshfbc/antennas/default}
 *       — directory containing the source {@code .voa} files</li>
 *   <li>{@code -Djantenna.defaults.outputDir=target/generated-resources/antennas/default}
 *       — where to write the baked {@code .gtable} + {@code .json}</li>
 * </ul>
 */
public final class DefaultsBakeApp {

    /** Voacapl Type-11 antennas to ship in the canonical defaults layer (per locked S5). */
    private static final List<String> DEFAULT_SOURCES = List.of(
            "swwhip.voa",
            "const5.voa",
            "const17.voa");

    /**
     * Curated HFMUFES analytical bakes shipped in the canonical defaults
     * layer per TD-112a (2026-05-17).  These are the patterns the
     * {@link com.voacap.antenna.LegacyAntTypeResolver} maps
     * {@code (ANT_TYPE, ANT_HEIGHT, ...)} tuples to when a card carries
     * no {@code TX_ANT_FILE} reference.
     *
     * <p>Currently just an isotrope @ 0 dBi (Constant KOP 12, ynh=0) —
     * the dominant analytical-only combination in the existing test
     * corpus (16/18 occurrences).  Constant @ 5 dBi already maps to
     * the voacapl {@code default/const5}.  Add entries here as new
     * (kop, params) combinations show up in production cards.</p>
     */
    private record AnalyticalBake(String name, double maxGainDbi, double designFreqMHz) { }

    private static final List<AnalyticalBake> CURATED_ANALYTICAL = List.of(
            new AnalyticalBake("isotrope-0dbi", 0.0, 14.15)
    );

    private DefaultsBakeApp() {
        // CLI entry point — must not invoke System.exit() because the
        // Maven exec-maven-plugin's `java` goal runs us in the SAME JVM
        // as Maven itself; an exit would kill the build before the
        // package phase.  Failures are signalled via RuntimeException
        // which the plugin propagates as a build failure.
    }

    public static void main(String[] args) throws IOException {
        Path sourceRoot = resolveSourceRoot();
        Path outputDir  = resolveOutputDir();

        System.out.println("[jantenna-defaults] source root: " + sourceRoot);
        System.out.println("[jantenna-defaults] output dir:  " + outputDir);

        // The baked defaults are also committed under
        // src/main/resources/antennas/default/, so they are already in
        // target/classes by the time this runs (process-classes follows
        // process-resources).  Re-baking overwrites them with freshly
        // generated copies when the voacapl sources are available; when they
        // are not — any machine but the maintainer's, CI included — the
        // committed copies stand and the jar still ships a populated defaults
        // layer.  Consumers run strict-mode, so an empty layer is fatal there.
        boolean haveSources = Files.isDirectory(sourceRoot);
        if (!haveSources) {
            System.out.println("[jantenna-defaults] voacapl source root not found at "
                    + sourceRoot
                    + " — keeping the committed defaults under "
                    + "src/main/resources/antennas/default/ and baking the analytical ones.");
        }

        Files.createDirectories(outputDir);

        int ok = 0, failed = 0;
        // Curated analytical bakes (TD-112a): no source file needed.
        for (AnalyticalBake bake : CURATED_ANALYTICAL) {
            try {
                IsotropeBaker baker = new IsotropeBaker(bake.maxGainDbi(), bake.designFreqMHz());
                AntennaBaker.BakeResult result = baker.bake(null, "default", bake.name());
                Path gtable = outputDir.resolve(bake.name() + ".gtable");
                Path meta   = outputDir.resolve(bake.name() + ".json");
                GainTableCodec.writeAtomic(result.table(), gtable);
                MetadataCodec.writeAtomic(result.metadata(), meta);
                System.out.printf(Locale.ROOT,
                        "[jantenna-defaults] baked default/%s (analytical, %d B + %d B)%n",
                        bake.name(), Files.size(gtable), Files.size(meta));
                ok++;
            } catch (IOException ioe) {
                System.err.println("[jantenna-defaults] FAILED " + bake.name() + ": " + ioe.getMessage());
                failed++;
            }
        }

        for (String filename : haveSources ? DEFAULT_SOURCES : List.<String>of()) {
            Path source = sourceRoot.resolve(filename);
            String name = PathNames.stem(filename);
            try {
                if (!Files.exists(source)) {
                    System.out.println("[jantenna-defaults] SKIP " + filename + " (not present)");
                    continue;
                }
                Type11Baker baker = new Type11Baker();
                AntennaBaker.BakeResult result = baker.bake(source, "default", name);
                Path gtable = outputDir.resolve(name + ".gtable");
                Path meta   = outputDir.resolve(name + ".json");
                GainTableCodec.writeAtomic(result.table(), gtable);
                MetadataCodec.writeAtomic(result.metadata(), meta);
                System.out.printf(Locale.ROOT,
                        "[jantenna-defaults] baked default/%s (%d B .gtable + %d B .json)%n",
                        name, Files.size(gtable), Files.size(meta));
                ok++;
            } catch (IOException ioe) {
                System.err.println("[jantenna-defaults] FAILED " + filename + ": " + ioe.getMessage());
                failed++;
            }
        }

        System.out.println("[jantenna-defaults] " + ok + " baked, " + failed + " failed");
        if (failed > 0) {
            throw new RuntimeException(failed + " antenna bake(s) failed; see stderr above.");
        }
    }

    private static Path resolveSourceRoot() {
        String prop = System.getProperty("jantenna.defaults.voacaplRoot");
        if (prop != null && !prop.isBlank()) return Path.of(prop);
        // Default: the voacapl checkout's antenna dir, relative to the user's
        // NetBeansProjects layout that we know holds the maintainer's working
        // copy.  Falls back to skip-with-warn when absent.
        String home = System.getProperty("user.home", "");
        return Path.of(home, "NetBeansProjects/voacapl/voacapl/itshfbc/antennas/default");
    }

    private static Path resolveOutputDir() {
        String prop = System.getProperty("jantenna.defaults.outputDir");
        if (prop != null && !prop.isBlank()) return Path.of(prop);
        // Write directly into target/classes/ so the jar plugin picks
        // it up.  process-classes phase runs after compile (target/classes
        // exists) and before package (jar plugin reads target/classes).
        return Path.of("target/classes/antennas/default");
    }

}
