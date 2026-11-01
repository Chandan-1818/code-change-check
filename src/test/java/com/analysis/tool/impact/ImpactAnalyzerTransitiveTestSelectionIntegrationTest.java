package com.analysis.tool.impact;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.DependencyGraphBuilder;
import com.analysis.tool.graph.MethodNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the fix for the gap documented in TestImpactMapperGapIntegrationTest:
 * by building ONE combined dependency graph spanning both CalculatorTest.java
 * and Calculator.java, ImpactAnalyzer's reverse BFS correctly identifies
 * testCompute() as impacted when "multiply" changes - even though
 * testCompute() only calls compute() directly, not multiply().
 *
 * This is the "after" state: transitive test selection now works via the
 * dependency graph, closing the gap that direct-call-only TestImpactMapper
 * could not handle.
 */
public class ImpactAnalyzerTransitiveTestSelectionIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";

    private static final String CALCULATOR_TEST_PATH =
            "src/test/java/com/sample/CalculatorTest.java";

    private static final String VERSION_4_COMMIT = "a3d0163";

    @Test
    public void shouldIdentifyTestComputeAsImpactedWhenMultiplyChangesViaTransitiveChain() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String calculatorSource = gitAnalyzer.getFileContentAtRevision(VERSION_4_COMMIT, CALCULATOR_PATH);
        String calculatorTestSource = gitAnalyzer.getFileContentAtRevision(VERSION_4_COMMIT, CALCULATOR_TEST_PATH);
        gitAnalyzer.close();

        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DependencyGraph combinedGraph = builder.buildFromSources(
                Arrays.asList(calculatorSource, calculatorTestSource));

        ImpactAnalyzer impactAnalyzer = new ImpactAnalyzer();
        ImpactSet impactSet = impactAnalyzer.analyzeImpact(combinedGraph, Collections.singleton("multiply"));

        MethodNode testComputeNode = new MethodNode("CalculatorTest", "testCompute");
        MethodNode testAddNode = new MethodNode("CalculatorTest", "testAdd");

        boolean testComputeImpacted =
                impactSet.getDirectlyImpacted().contains(testComputeNode)
                        || impactSet.getTransitivelyImpacted().containsKey(testComputeNode);

        assertTrue(testComputeImpacted,
                "Expected testCompute() to be identified as impacted (directly or transitively) " +
                "since it calls compute(), which calls multiply() - the gap TestImpactMapper could not close");

        boolean testAddImpacted =
                impactSet.getDirectlyImpacted().contains(testAddNode)
                        || impactSet.getTransitivelyImpacted().containsKey(testAddNode);

        assertFalse(testAddImpacted,
                "Expected testAdd() NOT to be impacted since it has no relation to multiply()");
    }
}
