package com.jantenna.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Top-level sub-command dispatcher for {@code jvoacap antennas ...}.
 * Hand-rolled (no picocli dependency) since the sub-command set is
 * small and stable.  Per {@code docs/antennas.md} §5.2.
 *
 * <p>Routing entry point lives in {@code com.voacap.VoacapApplication}:
 * when the first JVM arg is {@code "antennas"}, control is delegated
 * here with the remaining args, bypassing Spring Boot.</p>
 */
public final class AntennasCli {

    private final PrintStream out;
    private final PrintStream err;
    private final Path        repoRoot;

    /** Construct with the default repository path (system property or {@code ./antennas/}). */
    public AntennasCli() {
        this(System.out, System.err, defaultRepoRoot());
    }

    /** Construct with custom output streams + repo path — used by tests. */
    public AntennasCli(PrintStream out, PrintStream err, Path repoRoot) {
        this.out = out;
        this.err = err;
        this.repoRoot = repoRoot;
    }

    /** Run a single CLI invocation.  Returns the process exit code. */
    public int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 1;
        }
        String sub = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            return switch (sub) {
                case "bake"              -> new BakeCommand(out, err, repoRoot).run(rest);
                case "bake-dir"          -> new BakeDirCommand(out, err, repoRoot).run(rest);
                case "bake-isotrope"     -> new BakeIsotropeCommand(out, err, repoRoot).run(rest);
                case "bake-hfmufes"      -> new BakeHfmufesCommand(out, err, repoRoot).run(rest);
                case "list"              -> new ListCommand(out, err, repoRoot).run(rest);
                case "show"              -> new ShowCommand(out, err, repoRoot).run(rest);
                case "verify"            -> new VerifyCommand(out, err, repoRoot).run(rest);
                case "remove"            -> new RemoveCommand(out, err, repoRoot).run(rest);
                case "-h", "--help", "help" -> { printUsage(); yield 0; }
                default -> {
                    err.println("Unknown sub-command: " + sub);
                    printUsage();
                    yield 1;
                }
            };
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void printUsage() {
        out.println("Usage: jvoacap-antennas <sub-command> [args...]");
        out.println();
        out.println("Sub-commands:");
        out.println("  bake <source-file> [group]      Bake one .voa/.13/.t13 source into the repo.");
        out.println("  bake-dir <dir> [group]          Bake every recognised source file under <dir>.");
        out.println("  bake-isotrope <name> <gain-dbi> <freq-mhz> [group]");
        out.println("                                  Bake an analytical isotrope.");
        out.println("  bake-hfmufes <name> <params-json> [group]");
        out.println("                                  Bake an analytical HFMUFES antenna (KOP 1..17).");
        out.println("  list [--group <name>]           List patterns in the repository.");
        out.println("  show <pattern-name>             Print metadata + grid summary for one pattern.");
        out.println("  verify                          SHA-256 drift check across all baked patterns.");
        out.println("  remove <pattern-name>           Delete a pattern's .gtable + .json from the repo.");
        out.println();
        out.println("Repository: " + repoRoot.toAbsolutePath());
        out.println("Override via -Djvoacap.antennas.path=/some/dir");
    }

    /** Resolves {@code -Djvoacap.antennas.path} or defaults to {@code ./antennas/}. */
    public static Path defaultRepoRoot() {
        String prop = System.getProperty("jvoacap.antennas.path");
        return prop != null ? Path.of(prop) : Path.of("antennas");
    }
}
