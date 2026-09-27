package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.DependencyGraphBuilder;
import com.analysis.tool.graph.MethodNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test: proves that ImpactAnalyzer correctly identifies
 * Calculator.compute() as a direct (depth-1) impact when "multiply"
 * is the changed method, using a real dependency graph built from
 * the real Calculator.java (Version 4) source.
 *
 * This is the Phase 7 proof: the tool reasons that compute() is
 * impacted purely by walking the graph's reverse edges, not because
 * compute() itself was flagged as changed.
 */
public class ImpactAnalyzerIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";

    private static final String VERSION_4_COMMIT = "a3d0163";

    @Test
    public void shouldIdentifyComputeAsDirectlyImpactedWhenMultiplyChanges() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String calculatorSource = gitAnalyzer.getFileContentAtRevision(VERSION_4_COMMIT, CALCULATOR_PATH);
        gitAnalyzer.close();

        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DependencyGraph graph = builder.buildFromSource(calculatorSource);

        ImpactAnalyzer impactAnalyzer = new ImpactAnalyzer();
        ImpactSet impactSet = impactAnalyzer.analyzeImpact(graph, Collections.singleton("multiply"));

        MethodNode computeNode = new MethodNode("Calculator", "compute");
        MethodNode addNode = new MethodNode("Calculator", "add");

        assertTrue(impactSet.getDirectlyImpacted().contains(computeNode),
                "Expected compute() to be directly impacted since it calls multiply()");
        assertFalse(impactSet.getDirectlyImpacted().contains(addNode),
                "Expected add() NOT to be impacted since it has no relation to multiply()");
    }
}
