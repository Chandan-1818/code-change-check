package com.analysis.tool.cli;

import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.TestSelectionReport;
import com.analysis.tool.metrics.EvaluationMetrics;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Command-line entry point for change-sensitive regression test selection.
 *
 * Usage:
 *   SelectTestsCli <repoPath> <oldRev> <newRev> <sourceFile=testFile> [<sourceFile=testFile> ...] [--execute]
 *
 * Each sourceFile=testFile argument pairs a production file with its test
 * file, both relative to the repository root. The tool compares the two
 * revisions and prints the test methods that should be re-run, including
 * tests that reach a changed method through a chain of calls. Changed
 * methods that no test reaches are listed separately as a warning, so an
 * empty selection is not mistaken for "nothing to run".
 *
 * If the optional trailing --execute flag is given, the tool additionally
 * runs the full test suite and the selected tests as real Maven
 * subprocesses in the target repository, and prints a comparison
 * (counts, wall-clock time, and test reduction %). This is skipped, with
 * an explanation, if no tests were selected. Execution is off by default
 * because it is comparatively slow (launches real Maven builds).
 *
 * Exit codes: 0 = success (including runs that print warnings),
 * 1 = invalid arguments, 2 = analysis failed.
 *
 * Current limitation: the source/test file pairs must be supplied by the
 * caller; they are not yet discovered automatically.
 */
public class SelectTestsCli {

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        boolean execute = args.length > 0 && "--execute".equals(args[args.length - 1]);
        String[] positionalArgs = execute ? Arrays.copyOf(args, args.length - 1) : args;

        if (positionalArgs.length < 4) {
            err.println("Usage: SelectTestsCli <repoPath> <oldRev> <newRev> <sourceFile=testFile> [<sourceFile=testFile> ...] [--execute]");
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

        try {
            TestSelectionReport report = new ChangeImpactAnalyzer()
                    .analyzeTransitively(repoPath, oldRevision, newRevision, mappings);

            Set<String> selectedTests = new TreeSet<>(report.getSelectedTests());
            Set<String> selectedTestIdentifiers = new TreeSet<>(report.getSelectedTestIdentifiers());
            Set<String> untestedMethods = new TreeSet<>(report.getUntestedChangedMethods());
            Set<String> removedMethods = new TreeSet<>(report.getRemovedMethods());

            out.println("Repository     : " + repoPath);
            out.println("Compared       : " + oldRevision + " -> " + newRevision);
            out.println("File pairs     : " + mappings.size());

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

            if (execute) {
                if (selectedTestIdentifiers.isEmpty()) {
                    out.println("Execution skipped: no tests were selected, so there is nothing to run.");
                } else {
                    out.println();
                    out.println("Executing full suite and selected tests (this launches real Maven builds)...");

                    TestRunner runner = new TestRunner();
                    TestRunResult fullRun = runner.runFullSuite(repoPath);
                    TestRunResult selectedRun = runner.runSelectedTests(repoPath, selectedTestIdentifiers);
                    EvaluationMetrics metrics = EvaluationMetrics.compute(fullRun, selectedRun);

                    out.println("Full suite     : " + fullRun.getTotalTests() + " tests, "
                            + fullRun.getPassed() + " passed, " + fullRun.getFailed() + " failed, "
                            + fullRun.getExecutionTimeMillis() + " ms, build "
                            + (fullRun.isBuildSucceeded() ? "succeeded" : "FAILED"));
                    out.println("Selected tests : " + selectedRun.getTotalTests() + " tests, "
                            + selectedRun.getPassed() + " passed, " + selectedRun.getFailed() + " failed, "
                            + selectedRun.getExecutionTimeMillis() + " ms, build "
                            + (selectedRun.isBuildSucceeded() ? "succeeded" : "FAILED"));
                    out.println("Test reduction : " + String.format("%.1f", metrics.getTestReductionPercent()) + "%");
                    out.println("Time reduction : " + String.format("%.1f", metrics.getTimeReductionPercent())
                            + "% (informational - unreliable on small suites due to Maven/JVM startup overhead)");
                }
            }

            return 0;
        } catch (Exception e) {
            err.println("Analysis failed: " + e.getMessage());
            return 2;
        }
    }
}
