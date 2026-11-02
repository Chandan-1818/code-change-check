package com.analysis.tool.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.analysis.tool.execution.IsolatedRevisionCheckout;
import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.FileMappingDiscoverer;
import com.analysis.tool.impact.ImpactAnalyzer;
import com.analysis.tool.impact.TestSelectionReport;
import com.analysis.tool.metrics.EvaluationMetrics;
import com.analysis.tool.preflight.Preflight;
import com.analysis.tool.preflight.PreflightResult;

/**
 * Command-line entry point for change-sensitive regression test selection.
 *
 * Usage:
 *   SelectTestsCli <repoPath> <oldRev> <newRev> [<sourceFile=testFile> ...]
 *       [--generated=<revisionDir>] [--max-depth=<n|-1>] [--timeout=<ms>] [--execute]
 *
 * Each sourceFile=testFile argument pairs a production file with its test
 * file, both relative to the repository root. The tool compares the two
 * revisions and prints the test methods that should be re-run, including
 * tests that reach a changed method through a chain of calls. Changed
 * methods that no test reaches are listed separately as a warning, so an
 * empty selection is not mistaken for "nothing to run".
 *
 * A "Selection reasons" section prints, for every selected test, the call
 * chain that led to its selection (H7), so the tool states why each test was
 * chosen. A "Potentially broken tests" section lists tests that reference a
 * method removed between the revisions: they cannot be selected by impact
 * (the method no longer exists) but may no longer compile, so they are
 * reported rather than silently dropped.
 *
 * --generated=<revisionDir> names a directory written by the test generator
 * (it must contain src/test/java/rts/generated/*.java). Those generated tests
 * take part in the analysis: a changed method reached only by a generated test
 * is selected and not reported as untested. A missing directory or one without
 * generated tests is an invalid argument (exit 1), never silently ignored.
 *
 * --max-depth=<n|-1> caps the impact traversal depth: -1 (the default) means
 * unlimited, and a positive integer limits the number of call hops walked.
 * Zero and other negative values are invalid (exit 1). When a finite limit
 * excludes reachable methods, the run prints an explicit truncation warning
 * so a partial result is never presented as complete.
 *
 * If the optional trailing --execute flag is given, the tool additionally
 * runs the full test suite and the selected tests as real Maven
 * subprocesses, and prints an execution summary (target revision, selected
 * count, passed/failed/errored/skipped/timed-out counts, compilation status
 * and duration) plus a reduction comparison. Execution happens in an
 * isolated temporary checkout of the new revision (H8, GAP-14), so the
 * user's own working tree is never touched and stale sources can never be
 * executed. This is skipped, with an explanation, if no tests were selected.
 * Execution is off by default because it is comparatively slow (launches
 * real Maven builds).
 *
 * --timeout=<ms> bounds each Maven run (default 300000 ms). A run that
 * exceeds it is terminated together with its process tree and reported as
 * timed out, distinctly from a test failure (GAP-15). When execution fails,
 * the Maven log and Surefire XML reports are copied to a preserved
 * directory whose path is printed.
 *
 * Before any analysis, the target repository is inspected by the H9 preflight
 * (GAP-19). An unsupported repository (not a Git repository, a {@code .git}
 * file instead of a directory, a multi-module Maven project, or JUnit 4) is
 * refused with an actionable reason and exit code 2, so it is never silently
 * analysed as if it were fully supported. A repository that can be analysed
 * but whose features may hide call edges (Spring, Mockito, Lombok,
 * reflection) prints a "Preflight : REDUCED confidence" section explaining why.
 *
 * Exit codes: 0 = success (including runs that print warnings),
 * 1 = invalid arguments, 2 = analysis failed (including a preflight refusal).
 *
 * If no sourceFile=testFile pairs are given, they are discovered from the
 * files present at either revision, using the naming convention Foo.java -> FooTest.java.
 */
public class SelectTestsCli {

    private static final String GENERATED_PREFIX = "--generated=";
    private static final String MAX_DEPTH_PREFIX = "--max-depth=";
    private static final String TIMEOUT_PREFIX = "--timeout=";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String generatedDir = null;
        int maxDepth = ImpactAnalyzer.UNLIMITED_DEPTH;
        long timeoutMillis = TestRunner.DEFAULT_TIMEOUT_MILLIS;
        List<String> remaining = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(GENERATED_PREFIX)) {
                generatedDir = arg.substring(GENERATED_PREFIX.length());
            } else if (arg.startsWith(MAX_DEPTH_PREFIX)) {
                String value = arg.substring(MAX_DEPTH_PREFIX.length());
                try {
                    maxDepth = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    err.println("Invalid --max-depth (expected -1 for unlimited or a positive integer): " + value);
                    return 1;
                }
                if (maxDepth == 0 || maxDepth < ImpactAnalyzer.UNLIMITED_DEPTH) {
                    err.println("Invalid --max-depth (expected -1 for unlimited or a positive integer): " + value);
                    return 1;
                }
            } else if (arg.startsWith(TIMEOUT_PREFIX)) {
                String value = arg.substring(TIMEOUT_PREFIX.length());
                try {
                    timeoutMillis = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    err.println("Invalid --timeout (expected a positive number of milliseconds): " + value);
                    return 1;
                }
                if (timeoutMillis <= 0) {
                    err.println("Invalid --timeout (expected a positive number of milliseconds): " + value);
                    return 1;
                }
            } else {
                remaining.add(arg);
            }
        }
        String[] filteredArgs = remaining.toArray(new String[0]);

        boolean execute = filteredArgs.length > 0 && "--execute".equals(filteredArgs[filteredArgs.length - 1]);
        String[] positionalArgs = execute ? Arrays.copyOf(filteredArgs, filteredArgs.length - 1) : filteredArgs;

        if (positionalArgs.length < 3 || (generatedDir != null && generatedDir.isEmpty())) {
            err.println("Usage: SelectTestsCli <repoPath> <oldRev> <newRev> [<sourceFile=testFile> ...] "
                    + "[--generated=<revisionDir>] [--max-depth=<n|-1>] [--timeout=<ms>] [--execute]");
            return 1;
        }

        String repoPath = positionalArgs[0];
        String oldRevision = positionalArgs[1];
        String newRevision = positionalArgs[2];

        List<FileMapping> mappings = new ArrayList<>();
        for (int i = 3; i < positionalArgs.length; i++) {
            int separator = positionalArgs[i].indexOf('=');
            if (separator <= 0 || separator == positionalArgs[i].length() - 1) {
                err.println("Invalid file pair (expected sourceFile=testFile): " + positionalArgs[i]);
                return 1;
            }
            mappings.add(new FileMapping(
                    positionalArgs[i].substring(0, separator),
                    positionalArgs[i].substring(separator + 1)));
        }

        List<String> generatedSources = new ArrayList<>();
        if (generatedDir != null) {
            Path generatedTests = Paths.get(generatedDir).resolve("src/test/java/rts/generated");
            if (!Files.isDirectory(generatedTests)) {
                err.println("Invalid --generated (no src/test/java/rts/generated under it): " + generatedDir);
                return 1;
            }
            try (Stream<Path> files = Files.list(generatedTests)) {
                List<Path> javaFiles = files.filter(p -> p.getFileName().toString().endsWith(".java"))
                        .sorted().collect(Collectors.toList());
                for (Path file : javaFiles) {
                    generatedSources.add(Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                err.println("Could not read generated tests: " + e.getMessage());
                return 2;
            }
            if (generatedSources.isEmpty()) {
                err.println("Invalid --generated (no .java files in " + generatedTests + ")");
                return 1;
            }
        }

        // H9 preflight (GAP-19): inspect the target repository before any
        // analysis. An unsupported repository is refused with an actionable
        // reason instead of being silently analysed.
        PreflightResult preflight = Preflight.validate(repoPath);
        if (preflight.isUnsupported()) {
            err.println("Analysis failed: preflight validation refused this repository.");
            for (String reason : preflight.getRejections()) {
                err.println("  - " + reason);
            }
            return 2;
        }

        try {
            boolean autoDiscovered = mappings.isEmpty();
            if (autoDiscovered) {
                mappings = discoverMappings(repoPath, oldRevision, newRevision);
            }

            TestSelectionReport report = new ChangeImpactAnalyzer()
                    .analyzeTransitively(repoPath, oldRevision, newRevision, mappings, generatedSources,
                            maxDepth, changedFiles(repoPath, oldRevision, newRevision));

            Set<String> selectedTests = new TreeSet<>(report.getSelectedTests());
            Set<String> selectedTestIdentifiers = new TreeSet<>(report.getSelectedTestIdentifiers());
            Set<String> untestedMethods = new TreeSet<>(report.getUntestedChangedMethods());
            Set<String> removedMethods = new TreeSet<>(report.getRemovedMethods());
            Set<String> unparsedFiles = new TreeSet<>(report.getUnparsedFiles());
            Set<String> buildOrResourceChanges = new TreeSet<>(report.getBuildOrResourceChanges());
            Set<String> potentiallyBrokenTests = new TreeSet<>(report.getPotentiallyBrokenTests());

            out.println("Repository     : " + repoPath);
            out.println("Compared       : " + oldRevision + " -> " + newRevision);
            out.println("File pairs     : " + mappings.size() + (autoDiscovered ? " (auto-discovered)" : ""));
            if (generatedDir != null) {
                out.println("Generated tests: " + generatedSources.size() + " file(s) from " + generatedDir);
            }
            out.println("Confidence     : " + report.getConfidence());
            if (preflight.isReduced()) {
                out.println("Preflight      : REDUCED confidence - detected usage may hide call edges:");
                for (String reason : preflight.getReductions()) {
                    out.println("  - " + reason);
                }
            }
            out.println("Max depth      : " + (maxDepth == ImpactAnalyzer.UNLIMITED_DEPTH
                    ? "unlimited" : String.valueOf(maxDepth)));
            if (!unparsedFiles.isEmpty()) {
                out.println("Unparsed files (" + unparsedFiles.size()
                        + ") - could not be parsed, all discovered tests were selected:");
                for (String file : unparsedFiles) {
                    out.println("  - " + file);
                }
            }
            if (!buildOrResourceChanges.isEmpty()) {
                out.println("Build/resource changes (" + buildOrResourceChanges.size()
                        + ") - not analysable at member level, all discovered tests were selected:");
                for (String file : buildOrResourceChanges) {
                    out.println("  - " + file);
                }
            }
            if (report.isTruncated()) {
                out.println("WARNING - impact analysis truncated at max depth " + maxDepth
                        + "; deeper impacted methods were excluded, so this selection may be incomplete.");
            }

            if (!selectedTests.isEmpty()) {
                out.println("Selected tests (" + selectedTests.size() + "):");
                for (String test : selectedTests) {
                    out.println("  - " + test);
                }
            } else if (untestedMethods.isEmpty()) {
                out.println("Selected tests : none (no impacted tests found)");
            } else {
                out.println("Selected tests : none");
            }

            if (!untestedMethods.isEmpty()) {
                out.println("WARNING - changed methods not reached by any test (" + untestedMethods.size() + "):");
                for (String method : untestedMethods) {
                    out.println("  - " + method);
                }
            }

            if (!removedMethods.isEmpty()) {
                out.println("Removed methods (" + removedMethods.size() + "):");
                for (String method : removedMethods) {
                    out.println("  - " + method);
                }
            }

            if (!potentiallyBrokenTests.isEmpty()) {
                out.println("Potentially broken tests (" + potentiallyBrokenTests.size()
                        + ") - reference a method removed between the revisions:");
                for (String test : potentiallyBrokenTests) {
                    out.println("  - " + test);
                }
            }

            Map<String, List<String>> explanations = report.getExplanations();
            if (!explanations.isEmpty()) {
                out.println("Selection reasons:");
                for (Map.Entry<String, List<String>> entry
                        : new TreeMap<>(explanations).entrySet()) {
                    out.println("  - " + entry.getKey() + " -> " + String.join(" -> ", entry.getValue()));
                }
            }

            if (execute) {
                if (selectedTestIdentifiers.isEmpty()) {
                    out.println("Execution skipped: no tests were selected, so there is nothing to run.");
                } else {
                    runExecution(out, repoPath, newRevision, selectedTestIdentifiers, timeoutMillis);
                }
            }

            return 0;
        } catch (Exception e) {
            err.println("Analysis failed: " + e.getMessage());
            return 2;
        }
    }

    /**
     * Runs the full suite and the selected tests in an isolated checkout of the
     * requested new revision, then prints the execution summary and, on any
     * failure, preserves the Maven log and Surefire reports for inspection.
     */
    private static void runExecution(PrintStream out, String repoPath, String newRevision,
                                     Set<String> selectedTestIdentifiers, long timeoutMillis)
            throws Exception {
        out.println();
        out.println("Executing in an isolated checkout of " + newRevision
                + " (this launches real Maven builds)...");

        try (IsolatedRevisionCheckout checkout = IsolatedRevisionCheckout.open(repoPath, newRevision)) {
            TestRunner runner = new TestRunner(timeoutMillis);
            TestRunResult fullRun = runner.runFullSuite(checkout.getPath());
            TestRunResult selectedRun = runner.runSelectedTests(checkout.getPath(), selectedTestIdentifiers);
            EvaluationMetrics metrics = EvaluationMetrics.compute(fullRun, selectedRun);

            out.println("Target revision : " + newRevision);
            out.println("Selected tests  : " + selectedTestIdentifiers.size() + " selected");
            out.println("Full suite      : " + describe(fullRun));
            out.println("Selected run    : " + describe(selectedRun));
            out.println("Timed out       : "
                    + ((fullRun.isTimedOut() || selectedRun.isTimedOut()) ? "yes" : "no"));
            out.println("Test reduction  : " + String.format("%.1f", metrics.getTestReductionPercent()) + "%");
            out.println("Time reduction  : " + String.format("%.1f", metrics.getTimeReductionPercent())
                    + "% (informational - unreliable on small suites due to Maven/JVM startup overhead)");

            boolean failed = !fullRun.isBuildSucceeded() || !selectedRun.isBuildSucceeded()
                    || fullRun.isTimedOut() || selectedRun.isTimedOut()
                    || fullRun.getFailedOrErrors() > 0 || selectedRun.getFailedOrErrors() > 0
                    || fullRun.isCompilationFailed() || selectedRun.isCompilationFailed();
            if (failed) {
                Path preserved = preserveLogs(checkout.getDirectory());
                out.println("Execution had failures; logs preserved at: "
                        + (preserved != null ? preserved.toString() : "(could not be copied)"));
            }
        }
    }

    private static String describe(TestRunResult run) {
        return run.getTotalTests() + " tests, " + run.getPassed() + " passed, "
                + run.getFailed() + " failed, " + run.getErrors() + " errored, "
                + run.getSkipped() + " skipped, " + run.getExecutionTimeMillis() + " ms, build "
                + (run.isBuildSucceeded() ? "succeeded" : "FAILED")
                + ", compile " + (run.isCompilationFailed() ? "FAILED" : "ok");
    }

    /**
     * Copies the Maven execution log and the Surefire XML reports out of a
     * checkout that is about to be deleted, so evidence from a failed run is
     * not lost. Returns null if nothing could be preserved.
     */
    private static Path preserveLogs(Path checkoutDir) {
        try {
            Path logsDir = Files.createTempDirectory("rts-execution-logs-");
            Path log = checkoutDir.resolve("target").resolve("maven-execution.log");
            if (Files.exists(log)) {
                Files.copy(log, logsDir.resolve("maven-execution.log"));
            }
            Path reports = checkoutDir.resolve("target").resolve("surefire-reports");
            if (Files.isDirectory(reports)) {
                Path destination = Files.createDirectories(logsDir.resolve("surefire-reports"));
                try (Stream<Path> files = Files.list(reports)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (Files.isRegularFile(file)) {
                            Files.copy(file, destination.resolve(file.getFileName()));
                        }
                    }
                }
            }
            return logsDir;
        } catch (IOException e) {
            return null;
        }
    }

    private static List<FileMapping> discoverMappings(String repoPath, String oldRevision, String newRevision)
            throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            Set<String> allFiles = new TreeSet<>(git.listFilesAtRevision(oldRevision));
            allFiles.addAll(git.listFilesAtRevision(newRevision));
            return new FileMappingDiscoverer().discover(new ArrayList<>(allFiles));
        } finally {
            git.close();
        }
    }

    private static List<String> changedFiles(String repoPath, String oldRevision, String newRevision)
            throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            return git.getChangedFiles(oldRevision, newRevision);
        } finally {
            git.close();
        }
    }
}
