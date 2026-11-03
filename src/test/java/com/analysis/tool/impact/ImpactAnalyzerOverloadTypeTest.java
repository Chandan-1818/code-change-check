package com.analysis.tool.impact;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.MemberId;
import com.analysis.tool.graph.MethodNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1: a change to one same-arity overload must not select tests that only call
 * a different overload, but the narrowing is applied to the FIRST hop only and
 * is conservative, so recall is never reduced.
 */
class ImpactAnalyzerOverloadTypeTest {

    private final ImpactAnalyzer analyzer = new ImpactAnalyzer();

    @Test
    void changedIntOverloadSelectsOnlyTheIntCaller() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode callee = new MethodNode("UNKNOWN", "f");
        MethodNode intCaller = new MethodNode("T", "callInt");
        MethodNode stringCaller = new MethodNode("T", "callString");
        graph.addEdge(intCaller, callee, 1, List.of("int"));
        graph.addEdge(stringCaller, callee, 1, List.of("String"));

        MemberId changed = MemberId.forMethod("T", "f", 1, List.of("int"));
        ImpactSet impact = analyzer.analyzeMemberImpact(
                graph, Set.of(changed), ImpactAnalyzer.UNLIMITED_DEPTH);

        assertTrue(impact.getDirectlyImpacted().contains(intCaller));
        assertFalse(impact.getDirectlyImpacted().contains(stringCaller),
                "a change to f(int) must not select a caller that only calls f(String)");
    }

    @Test
    void unknownArgumentTypeKeepsTheCallerSelected() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode callee = new MethodNode("UNKNOWN", "f");
        MethodNode caller = new MethodNode("T", "callVar");
        graph.addEdge(caller, callee, 1, List.of(MemberId.UNKNOWN_TYPE));

        MemberId changed = MemberId.forMethod("T", "f", 1, List.of("int"));
        ImpactSet impact = analyzer.analyzeMemberImpact(
                graph, Set.of(changed), ImpactAnalyzer.UNLIMITED_DEPTH);

        assertTrue(impact.getDirectlyImpacted().contains(caller),
                "an argument whose type could not be inferred must keep the caller");
    }

    @Test
    void deeperHopsStayNameBasedSoATestTwoLevelsUpIsStillSelected() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode changedCallee = new MethodNode("UNKNOWN", "f");
        MethodNode service = new MethodNode("S", "compute");
        MethodNode test = new MethodNode("S", "test");

        graph.addEdge(service, changedCallee, 1, List.of("int"));
        graph.addEdge(test, new MethodNode("UNKNOWN", "compute"), 0);

        MemberId changed = MemberId.forMethod("S", "f", 1, List.of("int"));
        ImpactSet impact = analyzer.analyzeMemberImpact(
                graph, Set.of(changed), ImpactAnalyzer.UNLIMITED_DEPTH);

        assertTrue(impact.getDirectlyImpacted().contains(service));
        assertTrue(impact.getTransitivelyImpacted().containsKey(test));
    }
}
