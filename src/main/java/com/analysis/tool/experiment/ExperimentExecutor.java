package com.analysis.tool.experiment;

import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.TestSelectionReport;
import com.analysis.tool.metrics.EvaluationMetrics;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Runs the selection analysis AND the real test suites for each consecutive
 * pair of revisions, so every reduction figure comes from the suite that
 * actually existed at that revision.
 *
 * The repository is cloned once into a temporary directory and each row's
 * new revision is checked out there. Maven is never run in the original
 * working tree, whose checked-out version would otherwise decide the
 * numbers. The checkout's target/ directory is deleted before each run so
 * classes from a previous revision cannot leak into the next one.
 *
 * A row whose selection is empty is not executed (an empty -Dtest filter
 * would be misleading); it is reported as skipped.
 */
public class ExperimentExecutor {

    public static class ExecutedRow {
        private final String oldRevision;
        private final String newRevision;
        private final Set<String> selectedTests;
        private final boolean executed;
        private final int fullTotal;
        private final int fullPassed;
        private final int fullFailed;
        private final int selectedTotal;
        private final int selectedPassed;
        private final int selectedFailed;
        private final boolean buildsSucceeded;
        private final double testReductionPercent;

        public ExecutedRow(String oldRevision, String newRevision, Set<String> selectedTests,
                           boolean executed,
                           int fullTotal, int fullPassed, int fullFailed,
                           int selectedTotal, int selectedPassed, int selectedFailed,
                           boolean buildsSucceeded, double testReductionPercent) {
            this.oldRevision = oldRevision;
            this.newRevision = newRevision;
            this.selectedTests = new TreeSet<>(selectedTests);
            this.executed = executed;
            this.fullTotal = fullTotal;
            this.fullPassed = fullPassed;
            this.fullFailed = fullFailed;
            this.selectedTotal = selectedTotal;
            this.selectedPassed = selectedPassed;
            this.selectedFailed = selectedFailed;
            this.buildsSucceeded = buildsSucceeded;
            this.testReductionPercent = testReductionPercent;
        }

        public static ExecutedRow skipped(String oldRevision, String newRevision, Set<String> selectedTests) {
            return new ExecutedRow(oldRevision, newRevision, selectedTests, false,
                    0, 0, 0, 0, 0, 0, true, 0.0);
        }

        public String getOldRevision() { return oldRevision; }
        public String getNewRevision() { return newRevision; }
        public Set<String> getSelectedTests() { return selectedTests; }
        public boolean isExecuted() { return executed; }
        public int getFullTotal() { return fullTotal; }
        public int getFullPassed() { return fullPassed; }
        public int getFullFailed() { return fullFailed; }
        public int getSelectedTotal() { return selectedTotal; }
        public int getSelectedPassed() { return selectedPassed; }
        public int getSelectedFailed() { return selectedFailed; }
        public boolean isBuildsSucceeded() { return buildsSucceeded; }
        public double getTestReductionPercent() { return testReductionPercent; }

        public boolean isHealthy() {
            return executed && buildsSucceeded && fullFailed == 0 && selectedFailed == 0;
        }
    }

    public List<ExecutedRow> run(String repoPath, List<String> revisions) throws Exception {
        Path workDir = Files.createTempDirectory("experiment-checkout-");
        try (Git git = Git.cloneRepository()
                .setURI(new File(repoPath).getAbsoluteFile().toURI().toString())
                .setDirectory(workDir.toFile())
                .call()) {

            TestRunner runner = new TestRunner();
            List<ExecutedRow> rows = new ArrayList<>();

            for (int i = 0; i + 1 < revisions.size(); i++) {
                String oldRevision = revisions.get(i);
                String newRevision = revisions.get(i + 1);

                List<FileMapping> mappings =
                        ExperimentRunner.discoverMappings(repoPath, oldRevision, newRevision);
                TestSelectionReport report = new ChangeImpactAnalyzer()
                        .analyzeTransitively(repoPath, oldRevision, newRevision, mappings);

                Set<String> selectedTests = new TreeSet<>(report.getSelectedTests());
                Set<String> identifiers = new TreeSet<>(report.getSelectedTestIdentifiers());

                if (identifiers.isEmpty()) {
                    rows.add(ExecutedRow.skipped(oldRevision, newRevision, selectedTests));
                    continue;
                }

                git.checkout().setName(newRevision).call();
                deleteRecursively(workDir.resolve("target"));

                TestRunResult full = runner.runFullSuite(workDir.toString());
                TestRunResult selected = runner.runSelectedTests(workDir.toString(), identifiers);
                EvaluationMetrics metrics = EvaluationMetrics.compute(full, selected);

                rows.add(new ExecutedRow(oldRevision, newRevision, selectedTests, true,
                        full.getTotalTests(), full.getPassed(), full.getFailed(),
                        selected.getTotalTests(), selected.getPassed(), selected.getFailed(),
                        full.isBuildSucceeded() && selected.isBuildSucceeded(),
                        metrics.getTestReductionPercent()));
            }
            return rows;
        } finally {
            deleteRecursively(workDir);
        }
    }

    public static String toMarkdown(List<ExecutedRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Range | Selected tests | Full suite | Selected run | Test reduction | Status |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (ExecutedRow row : rows) {
            sb.append("| ").append(row.getOldRevision()).append(" -> ").append(row.getNewRevision());
            sb.append(" | ").append(row.getSelectedTests().isEmpty()
                    ? "-" : String.join(", ", row.getSelectedTests()));
            if (row.isExecuted()) {
                sb.append(" | ").append(row.getFullTotal()).append(" (")
                  .append(row.getFullPassed()).append(" passed)");
                sb.append(" | ").append(row.getSelectedTotal()).append(" (")
                  .append(row.getSelectedPassed()).append(" passed)");
                sb.append(" | ").append(String.format(Locale.ROOT, "%.1f%%", row.getTestReductionPercent()));
                sb.append(" | ").append(row.isHealthy() ? "OK" : "FAIL");
            } else {
                sb.append(" | - | - | - | skipped (nothing selected)");
            }
            sb.append(" |\n");
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                path.toFile().setWritable(true);
                path.toFile().delete();
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory
        }
    }
}
