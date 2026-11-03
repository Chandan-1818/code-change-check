package com.analysis.tool.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.analysis.tool.execution.IsolatedRevisionCheckout;
import com.analysis.tool.execution.MavenClasspathResolver;
import com.analysis.tool.execution.TestResult;
import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.FileMappingDiscoverer;
import com.analysis.tool.impact.ImpactAnalyzer;
import com.analysis.tool.impact.TestSelectionReport;
import com.analysis.tool.impact.TestSourceProvider;
import com.analysis.tool.metrics.EvaluationMetrics;
import com.analysis.tool.preflight.Preflight;
import com.analysis.tool.preflight.PreflightResult;
import com.analysis.tool.testgen.GeneratedTestWriter;
import com.analysis.tool.testgen.TestEmitter;
import com.analysis.tool.testgen.TestGenerator;

/**
 * Command-line entry point for change-sensitive regression test selection,
 * and the driver of the end-to-end live demo (T3).
 *
 * Two equivalent ways to name the repository and revisions:
 * <pre>
 *   SelectTestsCli --repo=&lt;path&gt; --base=&lt;oldRev&gt; --target=&lt;newRev&gt; [pairs/options]
 *   SelectTestsCli &lt;repoPath&gt; &lt;oldRev&gt; &lt;newRev&gt; [&lt;sourceFile=testFile&gt; ...] [options]
 * </pre>
 *
 * Options: {@code --generated=<revisionDir>}, {@code --generate},
 * {@code --out=<dir>}, {@code --max-depth=<n|-1>}, {@code --timeout=<ms>},
 * {@code --execute}.
 *
 * Workflow (each step reuses the existing services; no second pipeline):
 * <ol>
 *   <li>Validate the repository (H9 preflight) and both revisions.</li>
 *   <li>Run the Git diff and print every changed, added and deleted file with
 *       an A/M/D marker (file-level change detection).</li>
 *   <li>Detect changed classes and methods at member level, run the dependency
 *       graph and the reverse-BFS impact analysis, and print the impacted
 *       classes and methods.</li>
 *   <li>Select the affected regression tests, reporting when none exist.</li>
 *   <li>Optionally generate tests ({@code --generate}) and feed them into the
 *       same analysis ({@code --generated=<dir>} does the same for a previously
 *       written suite).</li>
 *   <li>Optionally execute the selection in an isolated checkout of the target
 *       revision, where any generated tests selected for execution are copied
 *       in so Maven actually runs them.</li>
 *   <li>Print a consolidated final report.</li>
 * </ol>
 *
 * Each sourceFile=testFile argument pairs a production file with its test
 * file, both relative to the repository root. When no pairs are given they are
 * discovered from the files present at either revision, using the naming
 * convention Foo.java -> FooTest.java.
 *
 * A "Selection reasons" section prints, for every selected test, the call chain
 * that led to its selection (H7), so the tool states why each test was chosen.
 * A "Potentially broken tests" section lists tests that reference a method
 * removed between the revisions: they cannot be selected by impact (the method
 * no longer exists) but may no longer compile, so they are reported rather than
 * silently dropped.
 *
 * Existing tests and generated tests are always distinguished in the output:
 * generated tests carry a "[generated]" marker and are listed under their own
 * execution heading. A generated test is reported as executed only when Maven's
 * Surefire reports confirm the individual test ran; otherwise it is reported as
 * "NOT confirmed", never as executed.
 *
 * --max-depth=<n|-1> caps the impact traversal depth: -1 (the default) means
 * unlimited, and a positive integer limits the number of call hops walked.
 * Zero and other negative values are invalid (exit 1). When a finite limit
 * excludes reachable methods, the run prints an explicit truncation warning so
 * a partial result is never presented as complete.
 *
 * --execute runs the full suite and the selected tests as real Maven
 * subprocesses and prints an execution summary (target revision, selected
 * count, passed/failed/errored/skipped/timed-out counts, compilation status and
 * duration) plus a reduction comparison. Execution happens in an isolated
 * temporary checkout of the target revision (H8, GAP-14), so the original
 * repository is never modified. It is skipped, with an explanation, when no
 * tests were selected.
 *
 * --timeout=<ms> bounds each Maven run (default 300000 ms). A run that exceeds
 * it is terminated together with its process tree and reported as timed out,
 * distinctly from a test failure (GAP-15). When execution fails, the Maven log
 * and Surefire XML reports are copied to a preserved directory whose path is
 * printed.
 *
 * Before any analysis, the repository is inspected by the H9 preflight
 * (GAP-19). An unsupported repository (not a Git repository, a {@code .git}
 * file instead of a directory, a multi-module Maven project, or JUnit 4) is
 * refused with an actionable reason and exit code 2. A repository that can be
 * analysed but whose features may hide call edges (Spring, Mockito, Lombok,
 * reflection) prints a "Preflight : REDUCED confidence" section.
 *
 * Exit codes: 0 = success (including runs that print warnings),
 * 1 = invalid arguments (including an unresolvable revision),
 * 2 = analysis failed (including a preflight refusal).
 */
public class SelectTestsCli {

    private static final String GENERATED_PREFIX = "--generated=";
    private static final String MAX_DEPTH_PREFIX = "--max-depth=";
    private static final String TIMEOUT_PREFIX = "--timeout=";
    private static final String REPO_PREFIX = "--repo=";
    private static final String BASE_PREFIX = "--base=";
    private static final String TARGET_PREFIX = "--target=";
    private static final String OUT_PREFIX = "--out=";
    private static final String GENERATE_FLAG = "--generate";
    private static final String EXECUTE_FLAG = "--execute";

    private static final String DEFAULT_GENERATED_OUTPUT = "generated-tests";

    private static final String USAGE = "Usage: SelectTestsCli --repo=<path> --base=<oldRev> --target=<newRev> "
            + "[<sourceFile=testFile> ...] [--generated=<revisionDir>] [--max-depth=<n|-1>] [--timeout=<ms>] "
            + "[--generate] [--out=<dir>] [--execute]\n"
            + "   or: SelectTestsCli <repoPath> <oldRev> <newRev> [<sourceFile=testFile> ...] [options]";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String generatedDir = null;
        String repoOption = null;
        String baseOption = null;
        String targetOption = null;
        String outDir = DEFAULT_GENERATED_OUTPUT;
        boolean generate = false;
        boolean execute = false;
        int maxDepth = ImpactAnalyzer.UNLIMITED_DEPTH;
        long timeoutMillis = TestRunner.DEFAULT_TIMEOUT_MILLIS;
        List<String> remaining = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith(GENERATED_PREFIX)) {
                generatedDir = arg.substring(GENERATED_PREFIX.length());
            } else if (arg.startsWith(REPO_PREFIX)) {
                repoOption = arg.substring(REPO_PREFIX.length());
            } else if (arg.startsWith(BASE_PREFIX)) {
                baseOption = arg.substring(BASE_PREFIX.length());
            } else if (arg.startsWith(TARGET_PREFIX)) {
                targetOption = arg.substring(TARGET_PREFIX.length());
            } else if (arg.startsWith(OUT_PREFIX)) {
                outDir = arg.substring(OUT_PREFIX.length());
            } else if (GENERATE_FLAG.equals(arg)) {
                generate = true;
            } else if (EXECUTE_FLAG.equals(arg)) {
                execute = true;
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

        boolean named = repoOption != null || baseOption != null || targetOption != null;
        String repoPath;
        String oldRevision;
        String newRevision;
        List<String> pairArgs;

        if (named) {
            if (repoOption == null || baseOption == null || targetOption == null
                    || repoOption.isEmpty() || baseOption.isEmpty() || targetOption.isEmpty()) {
                err.println("--repo, --base and --target must all be given, and must not be empty.");
                err.println(USAGE);
                return 1;
            }
            repoPath = repoOption;
            oldRevision = baseOption;
            newRevision = targetOption;
            pairArgs = remaining;
        } else {
            if (remaining.size() < 3) {
                err.println(USAGE);
                return 1;
            }
            repoPath = remaining.get(0);
            oldRevision = remaining.get(1);
            newRevision = remaining.get(2);
            pairArgs = remaining.subList(3, remaining.size());
        }

        if (generatedDir != null && generatedDir.isEmpty()) {
            err.println(USAGE);
            return 1;
        }
        if (outDir.isEmpty()) {
            err.println("Invalid --out (expected a non-empty directory): " + outDir);
            return 1;
        }

        List<FileMapping> mappings = new ArrayList<>();
        for (String pair : pairArgs) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || separator == pair.length() - 1) {
                err.println("Invalid file pair (expected sourceFile=testFile): " + pair);
                return 1;
            }
            mappings.add(new FileMapping(
                    pair.substring(0, separator),
                    pair.substring(separator + 1)));
        }

        // file name -> source, so generated tests can both take part in the
        // analysis and be copied into the isolated checkout for execution.
        Map<String, String> generatedTestFiles = new TreeMap<>();
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
                    generatedTestFiles.put(file.getFileName().toString(),
                            Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                err.println("Could not read generated tests: " + e.getMessage());
                return 2;
            }
            if (generatedTestFiles.isEmpty()) {
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

        // Validate both revisions up front, so a typo produces an actionable
        // message instead of an obscure failure deep in the diff.
        try {
            GitAnalyzer revisionCheck = new GitAnalyzer(repoPath);
            try {
                if (!revisionCheck.canResolve(oldRevision)) {
                    err.println("Invalid base revision (cannot be resolved in " + repoPath + "): " + oldRevision);
                    return 1;
                }
                if (!revisionCheck.canResolve(newRevision)) {
                    err.println("Invalid target revision (cannot be resolved in " + repoPath + "): " + newRevision);
                    return 1;
                }
            } finally {
                revisionCheck.close();
            }
        } catch (IOException e) {
            err.println("Analysis failed: " + e.getMessage());
            return 2;
        }

        try {
            // Step 2: file-level Git diff, with change types.
            List<GitAnalyzer.ChangedFile> diffEntries = changedFileEntries(repoPath, oldRevision, newRevision);
            List<String> changedFilePaths = new ArrayList<>();
            for (GitAnalyzer.ChangedFile file : diffEntries) {
                changedFilePaths.add(file.getPath());
            }

            // Step 5 (optional): reuse the existing test generator.
            String generationSummary = null;
            if (generate) {
                Path outRoot = Paths.get(outDir);
                String projectName = new File(repoPath).getAbsoluteFile().getName();
                List<String> productionSources = GenerateTestsCli.readProductionSources(repoPath, newRevision);
                if (productionSources.isEmpty()) {
                    err.println("Generation failed: no .java files under src/main/java at " + newRevision);
                    return 2;
                }
                // Resolve the target revision's own Maven classpath, so the oracle
                // capture compiles production sources that use Spring/Hibernate.
                String projectClasspath;
                try {
                    projectClasspath = new MavenClasspathResolver().resolve(repoPath, newRevision);
                } catch (IOException e) {
                    err.println("Generation failed: " + e.getMessage());
                    return 2;
                }
                TestEmitter.Result genResult =
                        new TestGenerator().generate(productionSources, projectName, newRevision, projectClasspath);
                Path written = new GeneratedTestWriter().write(outRoot, projectName, newRevision, genResult);
                for (Map.Entry<String, String> entry : genResult.getFiles().entrySet()) {
                    generatedTestFiles.put(fileName(entry.getKey()), entry.getValue());
                }
                generationSummary = genResult.getGeneratedCount() + " generated test method(s) from "
                        + productionSources.size() + " production file(s), written to " + written;
            }

            List<String> generatedSources = new ArrayList<>(generatedTestFiles.values());

            boolean autoDiscovered = mappings.isEmpty();
            if (autoDiscovered) {
                mappings = discoverMappings(repoPath, oldRevision, newRevision);
            }

            // Steps 3-4: reuse the single analysis pipeline.
            TestSelectionReport report = new ChangeImpactAnalyzer()
                    .analyzeTransitively(repoPath, oldRevision, newRevision, mappings, generatedSources,
                            maxDepth, changedFilePaths);

            Set<String> selectedTestIdentifiers = new TreeSet<>(report.getSelectedTestIdentifiers());
            Set<String> untestedMethods = new TreeSet<>(report.getUntestedChangedMethods());
            Set<String> removedMethods = new TreeSet<>(report.getRemovedMethods());
            Set<String> unparsedFiles = new TreeSet<>(report.getUnparsedFiles());
            Set<String> buildOrResourceChanges = new TreeSet<>(report.getBuildOrResourceChanges());
            Set<String> potentiallyBrokenTests = new TreeSet<>(report.getPotentiallyBrokenTests());
            Set<String> changedMembers = new TreeSet<>(report.getChangedMembers());
            Set<String> impactedMethods = new TreeSet<>(report.getImpactedMethods());

            Set<String> generatedClassNames = new TreeSet<>();
            for (String name : generatedTestFiles.keySet()) {
                generatedClassNames.add(stripJavaExtension(name));
            }
            Set<String> generatedSelected = new TreeSet<>();
            Set<String> existingSelected = new TreeSet<>();
            for (String identifier : selectedTestIdentifiers) {
                if (isGeneratedTest(identifier, generatedClassNames)) {
                    generatedSelected.add(identifier);
                } else {
                    existingSelected.add(identifier);
                }
            }

            int existingTestFileCount = countTestSourceFiles(repoPath, newRevision);

            // ---- Report -------------------------------------------------
            out.println("Repository     : " + repoPath);
            out.println("Compared       : " + oldRevision + " -> " + newRevision);
            out.println("File pairs     : " + mappings.size() + (autoDiscovered ? " (auto-discovered)" : ""));
            if (generatedDir != null) {
                out.println("Generated tests: " + generatedTestFiles.size() + " file(s) from " + generatedDir);
            }
            if (generationSummary != null) {
                out.println("Generation     : " + generationSummary);
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

            out.println("Git diff       : " + diffEntries.size() + " file(s) changed between "
                    + oldRevision + " and " + newRevision + describeChangeTypes(diffEntries));
            for (GitAnalyzer.ChangedFile file : diffEntries) {
                String suffix = "";
                if ((file.getChangeType() == GitAnalyzer.ChangeType.RENAMED
                        || file.getChangeType() == GitAnalyzer.ChangeType.COPIED)
                        && file.getOldPath() != null
                        && !file.getOldPath().equals(file.getPath())) {
                    suffix = " (from " + file.getOldPath() + ")";
                }
                out.println("  " + file.getMarker() + " " + file.getPath() + suffix);
            }

            out.println("Changed members (" + changedMembers.size() + "):");
            for (String member : changedMembers) {
                out.println("  - " + member);
            }
            out.println("Impacted methods (" + impactedMethods.size() + "):");
            for (String method : impactedMethods) {
                out.println("  - " + method);
            }

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

            if (existingTestFileCount == 0 && generatedTestFiles.isEmpty()) {
                out.println("No existing tests found at " + newRevision
                        + " (no .java files under src/test/java).");
            }

            if (!selectedTestIdentifiers.isEmpty()) {
                out.println("Selected tests (" + selectedTestIdentifiers.size() + "):");
                for (String identifier : selectedTestIdentifiers) {
                    out.println("  - " + identifier
                            + (generatedSelected.contains(identifier) ? " [generated]" : ""));
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

            // Step 6: optional isolated execution.
            String executionStatus;
            if (execute) {
                if (selectedTestIdentifiers.isEmpty()) {
                    out.println("Execution skipped: no tests were selected, so there is nothing to run.");
                    executionStatus = "skipped (no tests selected)";
                } else {
                    executionStatus = runExecution(out, repoPath, newRevision, selectedTestIdentifiers,
                            generatedTestFiles, generatedClassNames, timeoutMillis);
                }
            } else {
                executionStatus = "not requested (add --execute to run the selected tests)";
            }

            // Step 7: consolidated final report.
            out.println();
            out.println("================================================================================");
            out.println(" Final report");
            out.println("================================================================================");
            out.println("Repository       : " + repoPath);
            out.println("Revisions        : " + oldRevision + " -> " + newRevision);
            out.println("Git diff         : " + diffEntries.size() + " file(s) changed"
                    + describeChangeTypes(diffEntries));
            out.println("Changed members  : " + changedMembers.size());
            out.println("Impacted methods : " + impactedMethods.size());
            out.println("Selected tests   : " + selectedTestIdentifiers.size()
                    + " (" + existingSelected.size() + " existing, " + generatedSelected.size() + " generated)");
            out.println("Untested methods : " + untestedMethods.size());
            out.println("Confidence       : " + report.getConfidence());
            out.println("Execution        : " + executionStatus);

            return 0;
        } catch (Exception e) {
            err.println("Analysis failed: " + e.getMessage());
            return 2;
        }
    }

    /**
     * Runs the full suite and the selected tests in an isolated checkout of the
     * requested target revision. Any generated tests that were selected for
     * execution are copied into that checkout first, so Maven actually compiles
     * and runs them. The original repository is never touched (the checkout is
     * a temporary clone).
     *
     * @return a one-line status for the final report.
     */
    private static String runExecution(PrintStream out, String repoPath, String newRevision,
                                       Set<String> selectedTestIdentifiers,
                                       Map<String, String> generatedTestFiles,
                                       Set<String> generatedClassNames,
                                       long timeoutMillis) throws Exception {

        out.println();
        out.println("Executing in an isolated checkout of " + newRevision
                + " (this launches real Maven builds)...");

        try (IsolatedRevisionCheckout checkout = IsolatedRevisionCheckout.open(repoPath, newRevision)) {
            if (!generatedTestFiles.isEmpty()) {
                Path destination = checkout.getDirectory().resolve("src/test/java/rts/generated");
                Files.createDirectories(destination);
                for (Map.Entry<String, String> entry : generatedTestFiles.entrySet()) {
                    Files.writeString(destination.resolve(entry.getKey()), entry.getValue(),
                            StandardCharsets.UTF_8);
                }
                out.println("Included " + generatedTestFiles.size()
                        + " generated test file(s) in the isolated checkout (original repository untouched).");
            }

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

            Set<String> generatedSelected = new TreeSet<>();
            for (String identifier : selectedTestIdentifiers) {
                if (isGeneratedTest(identifier, generatedClassNames)) {
                    generatedSelected.add(identifier);
                }
            }
            int generatedConfirmed = 0;
            if (!generatedSelected.isEmpty()) {
                out.println("Generated tests selected for execution (" + generatedSelected.size() + "):");
                List<TestResult> results = selectedRun.getTestResults();
                boolean perTestAvailable = selectedRun.hasPerTestResults();
                for (String identifier : generatedSelected) {
                    if (perTestAvailable && wasExecuted(identifier, results)) {
                        generatedConfirmed++;
                        out.println("  - " + identifier + " : EXECUTED (confirmed by Maven Surefire)");
                    } else {
                        out.println("  - " + identifier
                                + " : NOT confirmed by Maven (absent from Surefire results; not reported as executed)");
                    }
                }
                out.println("Generated tests executed: " + generatedConfirmed + " / "
                        + generatedSelected.size() + " confirmed by Maven");
            }

            boolean failed = !fullRun.isBuildSucceeded() || !selectedRun.isBuildSucceeded()
                    || fullRun.isTimedOut() || selectedRun.isTimedOut()
                    || fullRun.getFailedOrErrors() > 0 || selectedRun.getFailedOrErrors() > 0
                    || fullRun.isCompilationFailed() || selectedRun.isCompilationFailed();

            String status;
            if (failed) {
                Path preserved = preserveLogs(checkout.getDirectory());
                out.println("Execution had failures; logs preserved at: "
                        + (preserved != null ? preserved.toString() : "(could not be copied)"));
                status = "ran with failures";
            } else {
                status = "ran successfully";
            }
            if (!generatedSelected.isEmpty()) {
                status += " (" + generatedConfirmed + "/" + generatedSelected.size() + " generated confirmed)";
            }
            return status;
        }
    }

    /** True when the identifier's class is one of the generated test classes. */
    private static boolean isGeneratedTest(String identifier, Set<String> generatedClassNames) {
        if (generatedClassNames.isEmpty()) {
            return false;
        }
        return generatedClassNames.contains(simpleClassName(identifier));
    }

    /** True when a Surefire report names this exact class#method (i.e. Maven ran it). */
    private static boolean wasExecuted(String identifier, List<TestResult> results) {
        String className = simpleClassName(identifier);
        int hash = identifier.indexOf('#');
        String method = hash < 0 ? "" : identifier.substring(hash + 1);
        for (TestResult result : results) {
            String resultClass = result.getClassName();
            int dot = resultClass.lastIndexOf('.');
            if (dot >= 0) {
                resultClass = resultClass.substring(dot + 1);
            }
            if (resultClass.equals(className) && result.getMethodName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    /** "Outer$Inner#method" -> "Outer"; "pkg.Class#method" -> "Class". */
    private static String simpleClassName(String identifier) {
        int hash = identifier.indexOf('#');
        String className = hash < 0 ? identifier : identifier.substring(0, hash);
        int dollar = className.indexOf('$');
        if (dollar >= 0) {
            className = className.substring(0, dollar);
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static String stripJavaExtension(String fileName) {
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - ".java".length()) : fileName;
    }

    private static String fileName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }

    /** e.g. " (2 added, 1 modified, 1 deleted)"; empty when nothing changed. */
    private static String describeChangeTypes(List<GitAnalyzer.ChangedFile> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        int added = 0;
        int deleted = 0;
        int renamed = 0;
        int modified = 0;
        for (GitAnalyzer.ChangedFile file : entries) {
            switch (file.getChangeType()) {
                case ADDED:
                    added++;
                    break;
                case DELETED:
                    deleted++;
                    break;
                case RENAMED:
                case COPIED:
                    renamed++;
                    break;
                default:
                    modified++;
                    break;
            }
        }
        StringBuilder summary = new StringBuilder(" (");
        summary.append(added).append(" added, ")
                .append(modified).append(" modified, ")
                .append(deleted).append(" deleted");
        if (renamed > 0) {
            summary.append(", ").append(renamed).append(" renamed/copied");
        }
        return summary.append(')').toString();
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

    private static List<GitAnalyzer.ChangedFile> changedFileEntries(String repoPath, String oldRevision,
                                                                   String newRevision) throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            return git.getChangedFileEntries(oldRevision, newRevision);
        } finally {
            git.close();
        }
    }

    private static int countTestSourceFiles(String repoPath, String revision) throws IOException {
        GitAnalyzer git = new GitAnalyzer(repoPath);
        try {
            int count = 0;
            for (String path : git.listFilesAtRevision(revision)) {
                if (TestSourceProvider.isTestSourceFile(path)) {
                    count++;
                }
            }
            return count;
        } finally {
            git.close();
        }
    }
}
