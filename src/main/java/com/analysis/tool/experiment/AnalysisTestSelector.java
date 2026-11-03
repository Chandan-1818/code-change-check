package com.analysis.tool.experiment;

import com.analysis.tool.eval.GroundTruthEvaluator;
import com.analysis.tool.impact.ChangeImpactAnalyzer;
import com.analysis.tool.impact.FileMapping;
import com.analysis.tool.impact.TestSelectionReport;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The production test-selection function used by the ground-truth evaluation.
 *
 * <p>It runs exactly the same analysis the tool ships ({@code
 * ChangeImpactAnalyzer} over auto-discovered file mappings), so the evaluated
 * selection is the real one, not a stand-in. It is an adapter only: the metric
 * and reporting logic lives in the {@code eval} package and is independent of
 * this class.
 */
public class AnalysisTestSelector implements GroundTruthEvaluator.TestSelector {

    @Override
    public Set<String> selectTests(String repoPath, String baselineRevision, String changedRevision)
            throws Exception {
        List<FileMapping> mappings =
                ExperimentRunner.discoverMappings(repoPath, baselineRevision, changedRevision);
        TestSelectionReport report = new ChangeImpactAnalyzer()
                .analyzeTransitively(repoPath, baselineRevision, changedRevision, mappings);
        return new TreeSet<>(report.getSelectedTests());
    }
}
