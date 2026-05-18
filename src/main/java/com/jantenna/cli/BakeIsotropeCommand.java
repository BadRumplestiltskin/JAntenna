package com.jantenna.cli;

import com.jantenna.GainTableCodec;
import com.jantenna.baker.AntennaBaker;
import com.jantenna.baker.IsotropeBaker;
import com.jantenna.baker.ManifestWriter;
import com.jantenna.MetadataCodec;
import com.jantenna.baker.RepositoryLock;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code jvoacap antennas bake-isotrope <name> <gain-dbi> <freq-mhz> [group]} —
 * bakes an analytical isotrope.  Produces a 1×1×1 {@code .gtable}.
 *
 * <p>Useful for testing the pipeline end-to-end without external source
 * files, and as the day-1 default antenna for cards that don't specify
 * one.</p>
 */
final class BakeIsotropeCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    BakeIsotropeCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        if (args.length < 3) {
            err.println("Usage: bake-isotrope <name> <gain-dbi> <freq-mhz> [group]");
            return 1;
        }
        String name = args[0];
        double gain = Double.parseDouble(args[1]);
        double freq = Double.parseDouble(args[2]);
        String group = args.length >= 4 ? args[3] : "default";

        try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
            AntennaBaker baker = new IsotropeBaker(gain, freq);
            AntennaBaker.BakeResult result = baker.bake(null, group, name);

            Path groupDir = repoRoot.resolve(group);
            Files.createDirectories(groupDir);
            GainTableCodec.writeAtomic(result.table(),    groupDir.resolve(name + ".gtable"));
            MetadataCodec.writeAtomic(result.metadata(), groupDir.resolve(name + ".json"));
            ManifestWriter.regenerate(repoRoot);

            out.println("Baked isotrope " + group + "/" + name
                    + " at " + gain + " dBi / " + freq + " MHz");
            return 0;
        }
    }
}
