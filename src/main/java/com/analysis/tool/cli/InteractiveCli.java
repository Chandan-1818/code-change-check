package com.analysis.tool.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.analysis.tool.impact.ImpactAnalyzer;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.remote.RemoteRepository;
import com.analysis.tool.remote.RemoteRepositoryException;
import com.analysis.tool.workflow.AnalysisWorkflow;

/**
 * PHASE 1 - interactive, repository-independent console workflow.
 *
 * <p>It prompts for a Git repository URL, clones it into an isolated temporary
 * workspace, shows the detected repository information, lets the user choose
 * whether to compare the latest two commits or two custom revisions, and then
 * offers the operations menu:
 * <ol>
 *   <li>Analyze repository changes</li>
 *   <li>Analyze and select regression tests</li>
 *   <li>Generate automatic JUnit tests</li>
 *   <li>Analyze, generate and execute tests</li>
 *   <li>Full regression testing workflow</li>
 *   <li>Exit</li>
 * </ol>
 *
 * <p>Every operation runs through the shared {@link AnalysisWorkflow}, so no
 * analysis or execution engine is duplicated. The session is deliberately
 * defensive: empty input, an invalid URL, an invalid menu selection,
 * unexpected input, cancellation (end of input) and network failure are all
 * handled with a message and either a re-prompt or a return to the menu, never
 * a crash. After an operation finishes the user is returned to the menu.
 *
 * <p>Input is injectable ({@link #run(PrintStream, PrintStream, BufferedReader)})
 * so the whole interaction can be tested without a real terminal.
 */
public class InteractiveCli {

    /** Exact banner line required by PHASE 1. */
    public static final String TITLE = "CHANGE-SENSITIVE REGRESSION TEST SELECTION SYSTEM";

    private static final String BORDER =
            "================================================================";

    /** Runs the interactive session on the real console. */
    public static int run(PrintStream out, PrintStream err) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return run(out, err, reader);
    }

    /** Runs the non-interactive entry points ({@code --help}, {@code --version}). */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelp(out);
                return 0;
            }
            if ("-v".equals(arg) || "--version".equals(arg)) {
                out.println("CODE-CHANGE-ANALYSIS 1.0-SNAPSHOT (Change-Sensitive Regression Test Selection)");
                return 0;
            }
        }
        return run(out, err);
    }

    public static int run(PrintStream out, PrintStream err, BufferedReader in) {
        printBanner(out);

        while (true) {
            out.println();
            out.print("Enter Git repository URL (or 'exit' to quit): ");
            out.flush();
            String url = readLine(in);
            if (url == null) {
                return 0;
            }
            url = url.trim();

            if (url.isEmpty()) {
                out.println("No repository URL entered. Please enter a Git repository URL.");
                continue;
            }
            if ("exit".equalsIgnoreCase(url) || "quit".equalsIgnoreCase(url) || "q".equalsIgnoreCase(url)) {
                out.println("Exiting. Goodbye.");
                return 0;
            }
            if (!RemoteRepository.isValidUrl(url)) {
                err.println("Invalid Git repository URL: " + RemoteRepository.redactCredentials(url));
                err.println("Expected https://, ssh://, git:// or file:// (or user@host:path).");
                continue;
            }

            out.println("Repository URL received.");
            out.println("Cloning into a dedicated temporary workspace...");

            RemoteRepository repository;
            try {
                repository = RemoteRepository.open(url, RemoteRepository.DEFAULT_CLONE_TIMEOUT_SECONDS);
            } catch (RemoteRepositoryException e) {
                err.println("Repository cloning/network failure: " + e.getMessage());
                err.println("Returning to the repository prompt.");
                continue;
            }

            try {
                printRepositoryInfo(repository, out);

                String[] revisions = selectComparison(repository, out, err, in);
                if (revisions == null) {
                    return 0;
                }
                if (revisions[0].isEmpty()) {
                    out.println("Returning to the repository prompt.");
                    continue;
                }

                int exitCode = operationsMenu(repository, revisions[0], revisions[1], out, err, in);
                if (exitCode >= 0) {
                    return exitCode;
                }
            } finally {
                repository.close();
            }
        }
    }

    private static void printBanner(PrintStream out) {
        out.println(BORDER);
        out.println(" " + TITLE);
        out.println(BORDER);
    }

    private static void printRepositoryInfo(RemoteRepository repository, PrintStream out) {
        out.println();
        out.println("Repository      : " + repository.getProjectName());
        out.println("URL             : " + RemoteRepository.redactCredentials(repository.getUrl()));
        out.println("Default branch  : " + repository.getDefaultBranch());
        out.println("Latest commit   : " + repository.getLatestCommit());
        out.println("Previous commit : "
                + (repository.hasPreviousCommit() ? repository.getPreviousCommit() : "(none)"));
    }

    /**
     * Asks whether to compare the latest two commits or two custom revisions.
     *
     * @return a two-element array {base, target}, an array {"", ""} to signal
     *         "restart from the repository prompt", or null at end of input
     */
    private static String[] selectComparison(RemoteRepository repository, PrintStream out,
                                             PrintStream err, BufferedReader in) {
        out.println();
        out.println("Select comparison:");
        out.println("  1. Automatically compare the latest two commits.");
        out.println("  2. Enter custom base and target commits.");
        out.print("Select [1-2] (default 1): ");
        out.flush();

        String choice = readLine(in);
        if (choice == null) {
            return null;
        }
        choice = choice.trim();

        if ("2".equals(choice)) {
            out.print("Base revision   : ");
            out.flush();
            String base = readLine(in);
            if (base == null) {
                return null;
            }
            out.print("Target revision : ");
            out.flush();
            String target = readLine(in);
            if (target == null) {
                return null;
            }
            base = base.trim();
            target = target.trim();
            if (base.isEmpty() || target.isEmpty()) {
                err.println("Both a base and a target revision are required.");
                return automatic(repository, out, err);
            }
            out.println("Custom comparison: " + base + " -> " + target);
            return new String[] {base, target};
        }

        if (!choice.isEmpty() && !"1".equals(choice)) {
            out.println("Unrecognised selection '" + choice + "'; using the latest two commits.");
        }
        return automatic(repository, out, err);
    }

    private static String[] automatic(RemoteRepository repository, PrintStream out, PrintStream err) {
        if (repository.hasPreviousCommit()) {
            out.println("Comparing the latest two commits: "
                    + repository.getPreviousCommit() + " -> " + repository.getLatestCommit());
            return new String[] {repository.getPreviousCommit(), repository.getLatestCommit()};
        }
        err.println("Repository " + repository.getProjectName() + " contains only one commit;");
        err.println("at least two revisions are required to compare changes.");
        err.println("Provide custom base and target revisions (which must both exist).");
        return new String[] {"", ""};
    }

    /**
     * The operations menu. Returns {@code -1} to continue with a new repository
     * or {@code 0} when the user chooses Exit.
     */
    private static int operationsMenu(RemoteRepository repository, String base, String target,
                                      PrintStream out, PrintStream err, BufferedReader in) {
        while (true) {
            out.println();
            out.println("Operations:");
            out.println("  1. Analyze repository changes");
            out.println("  2. Analyze and select regression tests");
            out.println("  3. Generate automatic JUnit tests");
            out.println("  4. Analyze, generate and execute tests");
            out.println("  5. Full regression testing workflow");
            out.println("  6. Exit");
            out.print("Select an operation [1-6]: ");
            out.flush();

            String choice = readLine(in);
            if (choice == null) {
                return 0;
            }
            choice = choice.trim();

            if (choice.isEmpty()) {
                out.println("No selection entered; please choose an operation between 1 and 6.");
                continue;
            }
            if ("6".equals(choice)) {
                out.println("Exiting. Goodbye.");
                return 0;
            }

            boolean generate;
            boolean execute;
            switch (choice) {
                case "1":
                case "2":
                    generate = false;
                    execute = false;
                    break;
                case "3":
                    generate = true;
                    execute = false;
                    break;
                case "4":
                case "5":
                    generate = true;
                    execute = true;
                    break;
                default:
                    out.println("Invalid selection '" + choice + "'; please choose an operation between 1 and 6.");
                    continue;
            }

            boolean confirmed = false;
            if (execute) {
                confirmed = confirmExecution(repository, out, err, in);
                if (confirmed) {
                    out.println("Execution confirmed.");
                } else {
                    out.println("Execution not confirmed; the analysis will still be produced "
                            + "but no tests will be run.");
                }
            }

            AnalysisWorkflow.Options options = new AnalysisWorkflow.Options();
            options.repoPath = repository.getPath();
            options.baseRevision = base;
            options.targetRevision = target;
            options.repositoryLabel = repository.getProjectName();
            options.branch = repository.getDefaultBranch();
            options.generate = generate;
            options.execute = execute;
            options.executionConfirmed = confirmed;
            options.maxDepth = ImpactAnalyzer.UNLIMITED_DEPTH;
            options.timeoutMillis = TestRunner.DEFAULT_TIMEOUT_MILLIS;

            try {
                AnalysisWorkflow.analyze(options, out);
            } catch (RuntimeException e) {
                err.println("Operation failed: " + e.getMessage());
            }
            out.println();
            out.println("Returning to the operations menu.");
        }
    }

    /** PHASE 4 security warning and explicit confirmation before execution. */
    private static boolean confirmExecution(RemoteRepository repository, PrintStream out,
                                            PrintStream err, BufferedReader in) {
        out.println();
        out.println("SECURITY WARNING");
        out.println("Running Maven build scripts and tests from an external repository executes");
        out.println("code from that repository on this machine. Only continue if you trust:");
        out.println("  " + RemoteRepository.redactCredentials(repository.getUrl()));
        out.print("Proceed with building and running tests? [y/N]: ");
        out.flush();

        String answer = readLine(in);
        if (answer == null) {
            return false;
        }
        answer = answer.trim();
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    private static String readLine(BufferedReader in) {
        try {
            return in.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    static void printHelp(PrintStream out) {
        out.println("Change-Sensitive Regression Test Selection (RTS) - Interactive CLI");
        out.println();
        out.println("Usage:");
        out.println("  java -jar target/CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar --interactive");
        out.println();
        out.println("Starts an interactive session that prompts for a Git repository URL,");
        out.println("clones it into a temporary workspace, and offers the operations menu:");
        out.println("  1. Analyze repository changes");
        out.println("  2. Analyze and select regression tests");
        out.println("  3. Generate automatic JUnit tests");
        out.println("  4. Analyze, generate and execute tests");
        out.println("  5. Full regression testing workflow");
        out.println("  6. Exit");
        out.println();
        out.println("Exit Codes:");
        out.println("  0 : Session ended normally");
        out.println("  1 : Invalid arguments or unresolvable revision");
        out.println("  2 : Analysis failed or preflight validation refused repository");
        out.println("  3 : Test execution failed");
        out.println("  4 : Repository cloning or network failure");
    }

    private InteractiveCli() {
    }
}
