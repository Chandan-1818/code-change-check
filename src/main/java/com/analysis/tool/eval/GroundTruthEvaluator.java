package com.analysis.tool.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs a ground-truth dataset through a test-selection function and produces
 * an {@link EvaluationReport}.
 *
 * <p>The evaluator is deliberately decoupled from the production selection
 * code: it depends only on the {@link TestSelector} abstraction, so the
 * metric logic can be exercised without a real repository or Maven run, and
 * the production pipeline plugs in through a selector implementation. This
 * keeps the evaluation harness separate from normal selection behaviour.
 */
public final class GroundTruthEvaluator {

    /**
     * Selects the tests to run for a change between two revisions. The
     * production implementation delegates to {@code ChangeImpactAnalyzer};
     * tests may supply a fake.
     */
    @FunctionalInterface
    public interface TestSelector {
        Set<String> selectTests(String repoPath, String baselineRevision, String changedRevision) throws Exception;
    }

    public EvaluationReport evaluate(String repoPath, GroundTruthDataset dataset, TestSelector selector)
            throws Exception {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset must not be null");
        }
        return evaluate(repoPath, dataset.getCases(), selector);
    }

    public EvaluationReport evaluate(String repoPath, List<GroundTruthCase> cases, TestSelector selector)
            throws Exception {
        if (cases == null) {
            throw new IllegalArgumentException("cases must not be null");
        }
        if (selector == null) {
            throw new IllegalArgumentException("selector must not be null");
        }
        List<CaseResult> results = new ArrayList<>();
        for (GroundTruthCase testCase : cases) {
            Set<String> selected = selector.selectTests(
                    repoPath, testCase.getBaselineRevision(), testCase.getChangedRevision());
            results.add(new CaseResult(testCase, selected));
        }
        return new EvaluationReport(results);
    }
}
