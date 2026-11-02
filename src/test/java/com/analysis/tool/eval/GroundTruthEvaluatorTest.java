package com.analysis.tool.eval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the evaluation driver. A fake {@link GroundTruthEvaluator.TestSelector}
 * keeps these tests independent of a repository or a Maven run, which is what
 * lets the metric logic be exercised in isolation from production selection.
 */
public class GroundTruthEvaluatorTest {

    private static GroundTruthCase caseWith(String id, String changedRevision, Set<String> required) {
        return new GroundTruthCase(id, "base", changedRevision,
                List.of("src/main/java/" + id + ".java"), List.of(id + ".m"), required, "note");
    }

    @Test
    public void evaluatesMultipleCasesAndAggregatesTheirMetrics() throws Exception {
        List<GroundTruthCase> cases = List.of(
                caseWith("c1", "r1", Set.of("a", "b")),
                caseWith("c2", "r2", Set.of("x", "y")));

        Map<String, Set<String>> selection = new HashMap<>();
        selection.put("r1", Set.of("a"));          // tp=1, fp=0, fn=1
        selection.put("r2", Set.of("x", "y", "z")); // tp=2, fp=1, fn=0

        GroundTruthEvaluator.TestSelector selector =
                (repo, baseline, changed) -> selection.get(changed);

        EvaluationReport report = new GroundTruthEvaluator().evaluate("repo", cases, selector);

        assertEquals(2, report.getCaseCount());
        assertEquals(3, report.getAggregate().getTruePositives());
        assertEquals(1, report.getAggregate().getFalsePositives());
        assertEquals(1, report.getAggregate().getFalseNegatives());
        assertEquals(3.0 / 4.0, report.getAggregate().getPrecision().doubleValue(), 1e-9);
        assertEquals(3.0 / 4.0, report.getAggregate().getRecall().doubleValue(), 1e-9);
    }

    @Test
    public void passesTheReposBaselineAndChangedRevisionsToTheSelector() throws Exception {
        GroundTruthCase c = caseWith("c1", "changed-xyz", Set.of("a"));
        List<String> seen = new ArrayList<>();

        GroundTruthEvaluator.TestSelector selector = (repo, baseline, changed) -> {
            seen.add(repo + "|" + baseline + "|" + changed);
            return Set.of("a");
        };

        new GroundTruthEvaluator().evaluate("my-repo", List.of(c), selector);

        assertEquals(List.of("my-repo|base|changed-xyz"), seen);
    }

    @Test
    public void emptySelectionIsRecordedAsMissingEveryRequiredTest() throws Exception {
        List<GroundTruthCase> cases = List.of(caseWith("c1", "r1", Set.of("a", "b")));
        GroundTruthEvaluator.TestSelector selector = (repo, baseline, changed) -> new TreeSet<>();

        EvaluationReport report = new GroundTruthEvaluator().evaluate("repo", cases, selector);
        CaseResult result = report.getCaseResults().get(0);

        assertEquals(0, result.getMetrics().getTruePositives());
        assertEquals(2, result.getMetrics().getFalseNegatives());
        assertTrue(result.getMetrics().getPrecision() == null, "precision is undefined with no selection");
        assertEquals(0.0, result.getMetrics().getRecall().doubleValue(), 1e-9);
        assertEquals(Set.of("a", "b"), result.getFalseNegativeTests());
    }

    @Test
    public void identifiesTrueAndFalsePositiveAndMissingTests() throws Exception {
        GroundTruthCase c = caseWith("c1", "r1", Set.of("keep", "missing"));
        GroundTruthEvaluator.TestSelector selector = (repo, baseline, changed) -> Set.of("keep", "extra");

        CaseResult result = new GroundTruthEvaluator().evaluate("repo", List.of(c), selector)
                .getCaseResults().get(0);

        assertEquals(Set.of("keep"), result.getTruePositiveTests());
        assertEquals(Set.of("extra"), result.getFalsePositiveTests());
        assertEquals(Set.of("missing"), result.getFalseNegativeTests());
    }

    @Test
    public void evaluatesAWholeDataset() throws Exception {
        GroundTruthDataset dataset = GroundTruthDataset.parse(List.of(
                "c1 | base | r1 | A.m | A.java | a",
                "c2 | base | r2 | B.m | B.java | b"));

        GroundTruthEvaluator.TestSelector selector = (repo, baseline, changed) -> "r1".equals(changed)
                ? Set.of("a")
                : Set.of();

        EvaluationReport report = new GroundTruthEvaluator().evaluate("repo", dataset, selector);

        assertEquals(2, report.getCaseCount());
        assertEquals(1, report.getAggregate().getTruePositives());
        assertEquals(1, report.getAggregate().getFalseNegatives());
    }

    @Test
    public void rejectsNullDatasetOrSelector() {
        GroundTruthEvaluator evaluator = new GroundTruthEvaluator();
        GroundTruthEvaluator.TestSelector selector = (repo, baseline, changed) -> Set.of();

        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate("repo", (GroundTruthDataset) null, selector));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate("repo", List.<GroundTruthCase>of(), null));
    }

    @Test
    public void aSelectorFailurePropagatesAndIsNotSilentlyIgnored() {
        List<GroundTruthCase> cases = List.of(caseWith("c1", "r1", Set.of("a")));
        GroundTruthEvaluator.TestSelector selector = (repo, baseline, changed) -> {
            throw new IllegalStateException("selection failed");
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new GroundTruthEvaluator().evaluate("repo", cases, selector));
        assertEquals("selection failed", ex.getMessage());
    }
}
