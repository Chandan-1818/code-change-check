package com.analysis.tool.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main live-demo and application entry point for Change-Sensitive Regression Test Selection (RTS).
 *
 * <p>Usage:
 * <pre>
 *   # 1. Zero-argument live demo against default or configured sample repository:
 *   java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar
 *
 *   # 2. Live demo with real test execution and reduction measurement:
 *   java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --execute
 *
 *   # 3. Analyze any target repository across two Git revisions:
 *   java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar &lt;repoPath&gt; &lt;baseRev&gt; &lt;targetRev&gt; [options...]
 * </pre>
 *
 * <p>Sample repository resolution order (when no &lt;repoPath&gt; is given):
 * <ol>
 *   <li>System property {@code rts.sample} (e.g. {@code -Drts.sample=/path/to/repo})</li>
 *   <li>Environment variable {@code RTS_SAMPLE_PROJECT}</li>
 *   <li>Default fallback: {@code D:/PROJECT/SAMPLE-PROJECT/sample-project}</li>
 * </ol>
 *
 * <p>Reuses {@link SelectTestsCli} for analysis and execution to guarantee exact compatibility
 * and algorithm behavior without duplication.
 */
public class DemoCli {

    public static final String PROPERTY = "rts.sample";
    public static final String ENVIRONMENT_VARIABLE = "RTS_SAMPLE_PROJECT";
    public static final String DEFAULT_SAMPLE_PATH = "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    public static final String DEFAULT_BASE_REVISION = "a3d0163";
    public static final String DEFAULT_TARGET_REVISION = "268a165";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        List<String> options = new ArrayList<>();
        List<String> positional = new ArrayList<>();

        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelp(out);
                return 0;
            }
            if ("-v".equals(arg) || "--version".equals(arg)) {
                out.println("CODE-CHANGE-ANALYSIS 1.0-SNAPSHOT (Change-Sensitive Regression Test Selection)");
                return 0;
            }
            if (arg.startsWith("--")) {
                options.add(arg);
            } else {
                positional.add(arg);
            }
        }

        // Mode 1: No positional arguments provided -> run canonical live demo
        if (positional.isEmpty()) {
            String repoPath = resolveSampleRepositoryPath();
            if (repoPath == null) {
                err.println("Error: No sample repository specified and default sample project was not found.");
                err.println("Checked locations:");
                err.println("  1. System property (-Drts.sample)           : "
                        + formatLocation(System.getProperty(PROPERTY)));
                err.println("  2. Environment variable (RTS_SAMPLE_PROJECT): "
                        + formatLocation(System.getenv(ENVIRONMENT_VARIABLE)));
                err.println("  3. Default fallback directory               : " + DEFAULT_SAMPLE_PATH + " (not found)");
                err.println();
                err.println("To run the live demo, either:");
                err.println("  - Pass arguments: java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar <repoPath> <baseRev> <targetRev> [--execute]");
                err.println("  - Set environment variable: set RTS_SAMPLE_PROJECT=<path-to-repo>");
                err.println("  - Set system property: -Drts.sample=<path-to-repo>");
                err.println();
                err.println("Run with --help for full usage instructions.");
                return 1;
            }

            out.println("================================================================================");
            out.println(" Change-Sensitive Regression Test Selection (RTS) - Live Demonstration");
            out.println("================================================================================");
            out.println("Demo Scenario : Transitive Impact & Regression Test Selection (V4 -> V5)");
            out.println("Target Repo   : " + repoPath);
            out.println("Base Commit   : " + DEFAULT_BASE_REVISION + " (Version 4: add Calculator.compute() calling Multiplier.multiply())");
            out.println("Target Commit : " + DEFAULT_TARGET_REVISION + " (Version 5: refactor Multiplier.multiply())");
            out.println("--------------------------------------------------------------------------------");

            List<String> forwarded = new ArrayList<>();
            forwarded.add(repoPath);
            forwarded.add(DEFAULT_BASE_REVISION);
            forwarded.add(DEFAULT_TARGET_REVISION);

            // Append non-execute options first, and --execute at the end if requested
            boolean hasExecute = false;
            for (String opt : options) {
                if ("--execute".equals(opt)) {
                    hasExecute = true;
                } else {
                    forwarded.add(opt);
                }
            }
            if (hasExecute) {
                forwarded.add("--execute");
            }

            return SelectTestsCli.run(forwarded.toArray(new String[0]), out, err);
        }

        // Mode 2: Incomplete positional arguments
        if (positional.size() < 3) {
            err.println("Error: Incomplete arguments. Expected: <repoPath> <baseRev> <targetRev> [<sourceFile=testFile>...] [options]");
            err.println("Provided: " + String.join(" ", positional));
            err.println("Run with --help for full usage instructions.");
            return 1;
        }

        // Mode 3: Custom repository and revisions provided -> delegate directly to SelectTestsCli
        return SelectTestsCli.run(args, out, err);
    }

    /**
     * Resolves the sample repository path using system property, environment variable,
     * or the default fallback directory. Returns null if none of the candidates is an
     * existing directory.
     */
    public static String resolveSampleRepositoryPath() {
        String prop = System.getProperty(PROPERTY);
        if (isValidDirectory(prop)) {
            return Paths.get(prop.trim()).toAbsolutePath().toString();
        }

        String env = System.getenv(ENVIRONMENT_VARIABLE);
        if (isValidDirectory(env)) {
            return Paths.get(env.trim()).toAbsolutePath().toString();
        }

        if (isValidDirectory(DEFAULT_SAMPLE_PATH)) {
            return Paths.get(DEFAULT_SAMPLE_PATH).toAbsolutePath().toString();
        }

        return null;
    }

    private static boolean isValidDirectory(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            return false;
        }
        try {
            Path p = Paths.get(pathStr.trim());
            return Files.isDirectory(p);
        } catch (Exception e) {
            return false;
        }
    }

    private static String formatLocation(String val) {
        return (val == null || val.trim().isEmpty()) ? "(not set)" : val.trim();
    }

    private static void printHelp(PrintStream out) {
        out.println("Change-Sensitive Regression Test Selection (RTS) - Live Demo CLI");
        out.println();
        out.println("Usage:");
        out.println("  java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar [options]");
        out.println("  java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar <repoPath> <baseRev> <targetRev> [pairs...] [options]");
        out.println("  mvn exec:java [-Dexec.args=\"<arguments>\"]");
        out.println();
        out.println("Modes:");
        out.println("  1. Zero-Argument Live Demo:");
        out.println("     Automatically runs regression test selection on the sample repository comparing");
        out.println("     V4 (" + DEFAULT_BASE_REVISION + ") -> V5 (" + DEFAULT_TARGET_REVISION + ").");
        out.println("     Sample repository is resolved in order:");
        out.println("       a. System property   : -Drts.sample=<path>");
        out.println("       b. Environment var   : RTS_SAMPLE_PROJECT=<path>");
        out.println("       c. Fallback default  : " + DEFAULT_SAMPLE_PATH);
        out.println();
        out.println("  2. Custom Repository Analysis:");
        out.println("     <repoPath> <baseRev> <targetRev> [<sourceFile=testFile>...]");
        out.println("     Analyzes code changes between two Git commits and selects affected tests.");
        out.println("     If no sourceFile=testFile pairs are given, they are auto-discovered.");
        out.println();
        out.println("Options:");
        out.println("  --execute              Run both the full test suite and the selected tests in an isolated");
        out.println("                         temporary checkout via Maven to measure test and time reduction.");
        out.println("  --max-depth=<n|-1>     Cap impact traversal call hops (-1 for unlimited, default: -1).");
        out.println("  --timeout=<ms>         Timeout for isolated Maven test runs (default: 300000 ms).");
        out.println("  --generated=<dir>      Include generated tests from the specified output directory.");
        out.println("  -v, --version          Print version information and exit.");
        out.println("  -h, --help             Show this help message and exit.");
        out.println();
        out.println("Exit Codes:");
        out.println("  0 : Success (test selection / execution completed)");
        out.println("  1 : Invalid arguments or missing sample repository");
        out.println("  2 : Analysis failed or preflight validation refused repository");
    }
}
