package com.analysis.tool.graph;

import com.analysis.tool.git.GitAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: builds a dependency graph from the real
 * Calculator.java (Version 4), which contains a genuine one-hop
 * transitive call chain: compute() calls Multiplier.multiply().
 *
 * Proves the graph correctly records this edge, and that the
 * reverse lookup (getCallers) correctly identifies compute() as
 * a caller of multiply() - the mechanism Phase 7's transitive
 * impact analysis will depend on.
 */
public class DependencyGraphBuilderIntegrationTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";

    private static final String CALCULATOR_PATH =
            "src/main/java/com/sample/Calculator.java";

    private static final String VERSION_4_COMMIT = "a3d0163";

    @Test
    public void shouldRecordComputeCallsMultiplyEdge() throws Exception {
        GitAnalyzer gitAnalyzer = new GitAnalyzer(SAMPLE_PROJECT_PATH);
        String calculatorSource = gitAnalyzer.getFileContentAtRevision(VERSION_4_COMMIT, CALCULATOR_PATH);
        gitAnalyzer.close();

        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DependencyGraph graph = builder.buildFromSource(calculatorSource);

        MethodNode computeNode = new MethodNode("Calculator", "compute");
        MethodNode multiplyNode = new MethodNode("UNKNOWN", "multiply");

        Set<MethodNode> calleesOfCompute = graph.getCallees(computeNode);
        assertTrue(calleesOfCompute.contains(multiplyNode),
                "Expected compute() to have an edge to multiply()");

        Set<MethodNode> callersOfMultiply = graph.getCallers(multiplyNode);
        assertTrue(callersOfMultiply.contains(computeNode),
                "Expected multiply() to have compute() recorded as a caller (reverse lookup)");
    }
}
