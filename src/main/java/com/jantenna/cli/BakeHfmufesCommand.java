package com.jantenna.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jantenna.GainTableCodec;
import com.jantenna.baker.AntennaBaker;
import com.jantenna.baker.HfmufesAnalyticalBaker;
import com.jantenna.baker.HfmufesBakeSpec;
import com.jantenna.baker.HfmufesCalculatorFactory;
import com.jantenna.baker.ManifestWriter;
import com.jantenna.MetadataCodec;
import com.jantenna.baker.RepositoryLock;
import com.jantenna.physics.AntennaGainCalculator;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code jvoacap antennas bake-hfmufes <name> <params-json> [group]} —
 * bakes one analytical HFMUFES antenna (KOP 1..17 — currently KOP 12
 * Constant is wired in Phase D pass 1 to demonstrate the harness; full
 * 17-KOP coverage lands in subsequent passes per
 * {@code docs/antennas.md} §9.4).
 *
 * <p>The JSON params shape mirrors {@link HfmufesBakeSpec} fields:</p>
 * <pre>
 *   {
 *     "kop":         12,
 *     "description": "Constant 5 dBi reference",
 *     "sigmaSm":     0.005,
 *     "epsilonR":    15.0,
 *     "antTiltDeg":   0.0,
 *     "antLengthWl":  0.0,
 *     "antHeightWl":  5.0,
 *     "tex":         [],
 *     "userGainDb":   0.0
 *   }
 * </pre>
 *
 * <p>For KOP 12, {@code antHeightWl} carries the constant gain in dB
 * (per {@code ConstantGainCalculator}'s convention of returning
 * {@code params.ynh()} directly).</p>
 */
final class BakeHfmufesCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;
    private final HfmufesCalculatorFactory calculatorFactory;

    BakeHfmufesCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
        this.calculatorFactory = new HfmufesCalculatorFactory();
    }

    int run(String[] args) throws IOException {
        if (args.length < 2) {
            err.println("Usage: bake-hfmufes <name> <params-json> [group]");
            err.println("Example: bake-hfmufes const5 '{\"kop\":12,\"antHeightWl\":5}'");
            return 1;
        }
        String name = args[0];
        String paramsJson = args[1];
        String group = args.length >= 3 ? args[2] : "hfmufes";

        HfmufesBakeSpec spec = parseSpec(paramsJson);
        AntennaGainCalculator calc = calculatorFactory.create(spec.kop());
        if (calc == null) {
            err.println("KOP " + spec.kop() + " is not supported by bake-hfmufes. "
                    + (spec.kop() == 10
                        ? "KOP 10 is the legacy Pre-Stored pattern — use "
                          + "`bake <source.voa>` to bake the underlying .voa file directly."
                        : "Valid KOPs: 1..9, 11..17."));
            return 1;
        }

        try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
            AntennaBaker baker = new HfmufesAnalyticalBaker(calc, spec);
            AntennaBaker.BakeResult result = baker.bake(null, group, name);

            Path groupDir = repoRoot.resolve(group);
            Files.createDirectories(groupDir);
            Path gtable = groupDir.resolve(name + ".gtable");
            Path meta   = groupDir.resolve(name + ".json");
            GainTableCodec.writeAtomic(result.table(),    gtable);
            MetadataCodec.writeAtomic(result.metadata(), meta);
            ManifestWriter.regenerate(repoRoot);

            out.println("Baked " + group + "/" + name
                    + " (KOP " + spec.kop() + " " + HfmufesCalculatorFactory.describe(spec.kop())
                    + ", " + Files.size(gtable) + " B .gtable + "
                    + Files.size(meta) + " B .json)");
            return 0;
        }
    }

    private HfmufesBakeSpec parseSpec(String json) throws IOException {
        JsonNode node = MAPPER.readTree(json);
        int kop = node.path("kop").asInt(0);
        if (kop < 1 || kop > 17) {
            throw new IOException("KOP must be 1..17, got " + kop);
        }
        String description = node.path("description").asText("HFMUFES KOP " + kop);
        double sigma = node.path("sigmaSm").asDouble(0.005);
        double eps   = node.path("epsilonR").asDouble(15.0);
        double tilt  = node.path("antTiltDeg").asDouble(0.0);
        double len   = node.path("antLengthWl").asDouble(0.0);
        double hgt   = node.path("antHeightWl").asDouble(0.0);
        double user  = node.path("userGainDb").asDouble(0.0);
        double[] tex;
        if (node.has("tex") && node.get("tex").isArray()) {
            JsonNode arr = node.get("tex");
            tex = new double[arr.size()];
            for (int i = 0; i < tex.length; i++) tex[i] = arr.get(i).asDouble();
        } else {
            tex = new double[0];
        }
        return new HfmufesBakeSpec(kop, description, sigma, eps, tilt, len, hgt, tex, user);
    }

}
