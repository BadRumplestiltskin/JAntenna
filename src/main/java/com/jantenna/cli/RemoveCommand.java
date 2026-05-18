package com.jantenna.cli;

import com.jantenna.repository.FilesystemGainTableRepository;
import com.jantenna.baker.ManifestWriter;
import com.jantenna.baker.RepositoryLock;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code jvoacap antennas remove <pattern-name>} — delete a pattern's
 * {@code .gtable} + {@code .json} from the repository, regenerate the
 * manifest.
 */
final class RemoveCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    RemoveCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        if (args.length < 1) {
            err.println("Usage: remove <pattern-name>");
            return 1;
        }
        String name = args[0];
        String canon = FilesystemGainTableRepository.canonicalise(name);
        Path gtable = repoRoot.resolve(canon + ".gtable");
        Path meta   = repoRoot.resolve(canon + ".json");
        if (!Files.exists(gtable)) {
            err.println("Pattern not in repository: " + name);
            return 1;
        }
        try (RepositoryLock lock = RepositoryLock.acquire(repoRoot)) {
            Files.deleteIfExists(gtable);
            Files.deleteIfExists(meta);
            ManifestWriter.regenerate(repoRoot);
            out.println("Removed " + canon);
            return 0;
        }
    }
}
