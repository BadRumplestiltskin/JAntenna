package com.jantenna.cli;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code jvoacap antennas bake-dir <dir> [group]} — recursively bakes
 * every recognised source file ({@code .voa}, {@code .13}, {@code .t13})
 * under {@code <dir>} into the repository.  Skips unrecognised files
 * with a warning, continues on individual failures, reports summary.
 */
final class BakeDirCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    BakeDirCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        if (args.length < 1) {
            err.println("Usage: bake-dir <dir> [group]");
            return 1;
        }
        Path dir = Path.of(args[0]);
        if (!Files.isDirectory(dir)) {
            err.println("Not a directory: " + dir);
            return 1;
        }
        String group = args.length >= 2 ? args[1] : "user";

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> {
                      String n = p.getFileName().toString().toLowerCase();
                      return n.endsWith(".voa") || n.endsWith(".13") || n.endsWith(".t13");
                  })
                  .forEach(sources::add);
        }

        int ok = 0, failed = 0;
        try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
            Path groupDir = repoRoot.resolve(group);
            Files.createDirectories(groupDir);
            for (Path source : sources) {
                String name = stripExtension(source.getFileName().toString());
                try {
                    AntennaBaker baker = BakerDispatcher.forFile(source);
                    AntennaBaker.BakeResult result = baker.bake(source, group, name);
                    GainTableCodec.writeAtomic(result.table(),    groupDir.resolve(name + ".gtable"));
                    MetadataCodec.writeAtomic(result.metadata(), groupDir.resolve(name + ".json"));
                    ok++;
                } catch (IOException e) {
                    err.println("FAILED " + source + ": " + e.getMessage());
                    failed++;
                }
            }
            ManifestWriter.regenerate(repoRoot);
        }
        out.println("Baked " + ok + " patterns into group '" + group + "' (failed " + failed + ")");
        return failed == 0 ? 0 : 2;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
