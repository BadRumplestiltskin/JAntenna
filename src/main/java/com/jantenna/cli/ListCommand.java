package com.jantenna.cli;

import com.jantenna.repository.FilesystemGainTableRepository;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code jvoacap antennas list [--group <name>]} — print every pattern
 * in the repository, optionally filtered to one group.
 */
final class ListCommand {

    private final PrintStream out;
    private final PrintStream err;
    private final Path repoRoot;

    ListCommand(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    int run(String[] args) throws IOException {
        String groupFilter = null;
        for (int i = 0; i < args.length; i++) {
            if ("--group".equals(args[i]) && i + 1 < args.length) {
                groupFilter = args[i + 1];
                i++;
            }
        }
        FilesystemGainTableRepository repo = new FilesystemGainTableRepository(repoRoot);
        List<String> names = repo.list();
        if (groupFilter != null) {
            String prefix = groupFilter + "/";
            names = names.stream().filter(n -> n.startsWith(prefix)).toList();
        }
        if (names.isEmpty()) {
            out.println("(repository empty" + (groupFilter != null ? " for group " + groupFilter : "") + ")");
            return 0;
        }
        for (String n : names) out.println(n);
        return 0;
    }
}
