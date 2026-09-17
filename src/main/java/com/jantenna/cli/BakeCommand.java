package com.jantenna.cli;

import com.jantenna.PathNames;
import com.jantenna.GainTableCodec;
import com.jantenna.baker.AntennaBaker;
import com.jantenna.baker.BakerDispatcher;
import com.jantenna.baker.ManifestWriter;
import com.jantenna.MetadataCodec;
import com.jantenna.baker.RepositoryLock;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code jvoacap antennas bake <source-file> [group]} — bakes one
 * source file into the repository.  The group defaults to
 * {@code "user"}; the pattern name is the source-file basename without
 * extension.
 */
final class BakeCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    BakeCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        if (args.length < 1) {
            err.println("Usage: bake <source-file> [group]");
            return 1;
        }
        Path source = Path.of(args[0]);
        if (!Files.exists(source)) {
            err.println("Source not found: " + source);
            return 1;
        }
        String group = args.length >= 2 ? args[1] : "user";
        String name = PathNames.stem(source);

        try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
            AntennaBaker baker = BakerDispatcher.forFile(source);
            AntennaBaker.BakeResult result = baker.bake(source, group, name);

            Path groupDir = repoRoot.resolve(group);
            Files.createDirectories(groupDir);
            Path gtable = groupDir.resolve(name + ".gtable");
            Path meta   = groupDir.resolve(name + ".json");
            GainTableCodec.writeAtomic(result.table(), gtable);
            MetadataCodec.writeAtomic(result.metadata(), meta);

            ManifestWriter.regenerate(repoRoot);
            out.println("Baked " + group + "/" + name
                    + " (" + Files.size(gtable) + " B .gtable + "
                    + Files.size(meta) + " B .json)");
            return 0;
        }
    }

}
