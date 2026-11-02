package com.analysis.tool.cli;

import java.io.PrintStream;

import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.impact.ImpactAnalyzer;
import com.analysis.tool.remote.RemoteRepository;
import com.analysis.tool.remote.RemoteRepositoryException;
import com.analysis.tool.workflow.AnalysisWorkflow;

/**
 * PHASE 12 - non-interactive remote workflow.
 *
 * <p>It accepts a Git repository URL, clones it into a dedicated temporary
 * workspace, and runs the complete pipeline (clone &rarr; detect structure &rarr;
 * classify &rarr; identify Java changes &rarr; build graph &rarr; analyse impact
 * &rarr; select tests &rarr; optionally generate JUnit tests &rarr; optionally
 * execute them in an isolated checkout &rarr; verify with Surefire &rarr; final
 * report) through the shared {@link AnalysisWorkflow}.
 *
 * <p>Supported forms:
 * <pre>
 *   RemoteWorkflowCli --url=&lt;GIT_URL&gt;
 *   RemoteWorkflowCli --url=&lt;GIT_URL&gt; --base=&lt;rev&gt; --target=&lt;rev&gt;
 *   RemoteWorkflowCli --url=&lt;GIT_URL&gt; --generate --execute --yes
 * </pre>
 *
 * <p>Exit codes (extends the existing contract; the T3 local CLI keeps 0/1/2):
 * <ul>
 *   <li>{@code 0} success (including runs that print warnings)</li>
 *   <li>{@code 1} invalid arguments or an unresolvable revision</li>
 *   <li>{@code 2} analysis or preflight failure</li>
 *   <li>{@code 3} test execution failure</li>
 *   <li>{@code 4} repository cloning or network failure</li>
 * </ul>
 *
 * <p>Security (PHASE 4/13): running Maven build scripts and tests from an
 * external repository executes third-party code, so it only happens after the
 * user is shown a warning and explicitly confirms it (via {@code --yes} or an
 * interactive prompt when a console is attached). Credentials are never
 * prompted for, stored or logged; a URL that embeds them is redacted before it
 * is printed.
 */
public class RemoteWorkflowCli {

    private static final String URL_PREFIX = "--url=";
    private static final String BASE_PREFIX = "--base=";
    private static final String TARGET_PREFIX = "--target=";
    private static final String OUT_PREFIX = "--out=";
    private static final String GENERATED_PREFIX = "--generated=";
    private static final String MAX_DEPTH_PREFIX = "--max-depth=";
    private static final String TIMEOUT_PREFIX = "--timeout=";
    private static final String GENERATE_FLAG = "--generate";
    private static final String EXECUTE_FLAG = "--execute";
    private static final String YES_FLAG = "--yes";

    /** Exit code for a repository that could not be cloned. */
    public static final int EXIT_CLONE_FAILURE = 4;

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String url = null;
        String base = null;
        String target = null;
        String outDir = null;
        String generatedDir = null;
        int maxDepth = ImpactAnalyzer.UNLIMITED_DEPTH;
        long timeoutMillis = TestRunner.DEFAULT_TIMEOUT_MILLIS;
        boolean generate = false;
        boolean execute = false;
        boolean yes = false;

        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelp(out);
                return 0;
            }
            if ("-v".equals(arg) || "--version".equals(arg)) {
                out.println("CODE-CHANGE-ANALYSIS 1.0-SNAPSHOT (Change-Sensitive Regression Test Selection)");
                return 0;
            }
            if (arg.startsWith(URL_PREFIX)) {
                url = arg.substring(URL_PREFIX.length()).trim();
            } else if (arg.startsWith(BASE_PREFIX)) {
                base = arg.substring(BASE_PREFIX.length()).trim();
            } else if (arg.startsWith(TARGET_PREFIX)) {
                target = arg.substring(TARGET_PREFIX.length()).trim();
            } else if (arg.startsWith(OUT_PREFIX)) {
                outDir = arg.substring(OUT_PREFIX.length()).trim();
            } else if (arg.startsWith(GENERATED_PREFIX)) {
                generatedDir = arg.substring(GENERATED_PREFIX.length()).trim();
            } else if (arg.startsWith(MAX_DEPTH_PREFIX)) {
                Integer parsed = parsePositiveInt(arg.substring(MAX_DEPTH_PREFIX.length()).trim());
                if (parsed == null) {
                    err.println("Error: --max-depth must be -1 (unlimited) or a positive integer.");
                    return 1;
                }
                maxDepth = parsed;
            } else if (arg.startsWith(TIMEOUT_PREFIX)) {
                Integer parsed = parsePositiveInt(arg.substring(TIMEOUT_PREFIX.length()).trim());
                if (parsed == null) {
                    err.println("Error: --timeout must be a positive integer (milliseconds).");
                    return 1;
                }
                timeoutMillis = parsed;
            } else if (GENERATE_FLAG.equals(arg)) {
                generate = true;
            } else if (EXECUTE_FLAG.equals(arg)) {
                execute = true;
            } else if (YES_FLAG.equals(arg)) {
                yes = true;
            } else {
                err.println("Error: Unrecognised argument: " + arg);
                err.println("Run with --help for usage instructions.");
                return 1;
            }
        }

        if (url == null || url.isEmpty()) {
            err.println("Error: Missing required argument --url=<GIT_URL>.");
            err.println("Run with --help for usage instructions.");
            return 1;
        }
        if (!RemoteRepository.isValidUrl(url)) {
            err.println("Error: Invalid Git repository URL: " + RemoteRepository.redactCredentials(url));
            err.println("Expected a https://, ssh://, git:// or file:// URL (or user@host:path).");
            return 1;
        }
        if ((base == null) != (target == null)) {
            err.println("Error: --base and --target must be given together.");
            return 1;
        }
        if (base != null && base.isEmpty()) {
            err.println("Error: --base must not be empty.");
            return 1;
        }
        if (target != null && target.isEmpty()) {
            err.println("Error: --target must not be empty.");
            return 1;
        }

        RemoteRepository repository = null;
        try {
            out.println("Repository URL received: " + RemoteRepository.redactCredentials(url));
            out.println("Cloning into a dedicated temporary workspace...");
            repository = RemoteRepository.open(url, RemoteRepository.DEFAULT_CLONE_TIMEOUT_SECONDS);
        } catch (RemoteRepositoryException e) {
            err.println("Error: Repository cloning/network failure: " + e.getMessage());
            return EXIT_CLONE_FAILURE;
        }

        try {
            String resolvedBase = base;
            String resolvedTarget = target;
            if (resolvedBase == null) {
                if (!repository.hasPreviousCommit()) {
                    err.println("Error: " + repository.getProjectName()
                            + " contains only one commit; at least two revisions are required.");
                    err.println("Provide --base=<rev> --target=<rev> explicitly.");
                    return 1;
                }
                resolvedBase = repository.getPreviousCommit();
                resolvedTarget = repository.getLatestCommit();
            }

            out.println("Repository      : " + repository.getProjectName());
            out.println("Default branch  : " + repository.getDefaultBranch());
            out.println("Latest commit   : " + repository.getLatestCommit());
            out.println("Previous commit : "
                    + (repository.hasPreviousCommit() ? repository.getPreviousCommit() : "(none)"));
            out.println("Base revision   : " + resolvedBase);
            out.println("Target revision : " + resolvedTarget);
            out.println();

            boolean confirmed = false;
            if (execute) {
                confirmed = confirmExecution(repository, yes, out, err);
                if (!confirmed) {
                    return 1;
                }
            }

            AnalysisWorkflow.Options options = new AnalysisWorkflow.Options();
            options.repoPath = repository.getPath();
            options.baseRevision = resolvedBase;
            options.targetRevision = resolvedTarget;
            options.repositoryLabel = repository.getProjectName();
            options.branch = repository.getDefaultBranch();
            options.generate = generate;
            options.execute = execute;
            options.executionConfirmed = confirmed;
            options.generatedDir = generatedDir;
            options.maxDepth = maxDepth;
            options.timeoutMillis = timeoutMillis;
            if (outDir != null && !outDir.isEmpty()) {
                options.outDir = outDir;
            }

            AnalysisWorkflow.Result result = AnalysisWorkflow.analyze(options, out);
            return result.exitCode;
        } finally {
            repository.close();
        }
    }

    /**
     * PHASE 4: shows the security warning and obtains explicit confirmation
     * before Maven build scripts or tests from an external repository are run.
     *
     * <p>This CLI is non-interactive, so the only confirmation it accepts is
     * the explicit {@code --yes} flag; it never blocks on a console prompt.
     * The interactive session ({@link InteractiveCli}) asks the user directly.
     */
    private static boolean confirmExecution(RemoteRepository repository, boolean yes,
                                            PrintStream out, PrintStream err) {
        out.println("SECURITY WARNING");
        out.println("Running Maven build scripts and tests from an external repository executes");
        out.println("code from that repository on this machine. Only continue if you trust:");
        out.println("  " + RemoteRepository.redactCredentials(repository.getUrl()));
        out.println();

        if (yes) {
            out.println("Execution confirmed by --yes.");
            out.println();
            return true;
        }

        err.println("Error: --execute runs tests from a remote repository and requires explicit");
        err.println("confirmation. Re-run with --yes once you trust the repository.");
        return false;
    }

    /** Parses -1 or a positive integer; anything else is rejected. */
    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed == -1 || parsed > 0) {
                return parsed;
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void printHelp(PrintStream out) {
        out.println("Change-Sensitive Regression Test Selection (RTS) - Remote Repository CLI");
        out.println();
        out.println("Usage:");
        out.println("  java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --url=<GIT_URL> [options]");
        out.println();
        out.println("Examples:");
        out.println("  --url=https://github.com/user/project.git");
        out.println("  --url=https://github.com/user/project.git --base=<rev> --target=<rev>");
        out.println("  --url=https://github.com/user/project.git --generate --execute --yes");
        out.println();
        out.println("Modes:");
        out.println("  1. Default: the latest two commits on the default branch are compared.");
        out.println("  2. Custom: --base and --target select the two revisions to compare.");
        out.println();
        out.println("Options:");
        out.println("  --url=<GIT_URL>        Git repository URL (https/ssh/git/file, or user@host:path).");
        out.println("  --base=<rev>           Older revision to compare (requires --target).");
        out.println("  --target=<rev>         Newer revision to compare (requires --base).");
        out.println("  --generate             Generate JUnit 5 tests for the target revision.");
        out.println("  --out=<dir>            Where --generate writes its suite (default: generated-tests).");
        out.println("  --generated=<dir>      Include an existing generated suite from this directory.");
        out.println("  --max-depth=<n|-1>     Cap impact traversal call hops (-1 for unlimited, default: -1).");
        out.println("  --timeout=<ms>         Timeout for isolated Maven test runs (default: 300000 ms).");
        out.println("  --execute              Run the selected and generated tests in an isolated checkout.");
        out.println("  --yes                  Confirm execution of build scripts/tests from this repository.");
        out.println("  -v, --version          Print version information and exit.");
        out.println("  -h, --help             Show this help message and exit.");
        out.println();
        out.println("Exit Codes:");
        out.println("  0 : Success (test selection / execution completed)");
        out.println("  1 : Invalid arguments or unresolvable revision");
        out.println("  2 : Analysis failed or preflight validation refused repository");
        out.println("  3 : Test execution failed");
        out.println("  4 : Repository cloning or network failure");
    }

    private RemoteWorkflowCli() {
    }
}
