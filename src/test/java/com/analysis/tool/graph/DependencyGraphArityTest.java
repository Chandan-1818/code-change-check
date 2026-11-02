package com.analysis.tool.graph;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * H3: the graph records a call-site arity per edge and resolves constructor
 * callees ("new A(...)"). The arity filter separates different-arity callers
 * but never drops a caller whose call-site arity was not recorded, so recall
 * is preserved (conservative fallback).
 */
class DependencyGraphArityTest {

    @Test
    void arityFilterExcludesDifferentArityCallersButKeepsUnknownArityCallers() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode oneArgCaller = new MethodNode("T", "one");
        MethodNode twoArgCaller = new MethodNode("T", "two");
        MethodNode unknownCaller = new MethodNode("T", "unknown");
        MethodNode callee = new MethodNode("UNKNOWN", "f");

        graph.addEdge(oneArgCaller, callee, 1);
        graph.addEdge(twoArgCaller, callee, 2);
        graph.addEdge(unknownCaller, callee); // arity not recorded (-1)

        Set<MethodNode> callers = graph.getCallersByMethodNameAndArity("f", 1);

        assertTrue(callers.contains(oneArgCaller));
        assertFalse(callers.contains(twoArgCaller));
        assertTrue(callers.contains(unknownCaller),
                "A caller whose call-site arity is unknown must still be included");
    }

    @Test
    void constructorEdgesAreRecordedWithResolvedClass() {
        String source = "package demo;\n"
                + "public class A {\n"
                + "    int x;\n"
                + "    public A(int seed) { x = seed; }\n"
                + "    void call() { new A(1); }\n"
                + "}\n";

        DependencyGraph graph = new DependencyGraphBuilder().buildFromSource(source);

        assertTrue(graph.getCallersByMethodNameAndArity(MemberId.CONSTRUCTOR_NAME, 1)
                        .contains(new MethodNode("A", "call")),
                "new A(1) must create an edge to the resolved A constructor");
    }

    @Test
    void constructorCallersAreClassScopedNotMatchedByTheSharedInitName() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode aCtor = new MethodNode("A", MemberId.CONSTRUCTOR_NAME);
        MethodNode bCtor = new MethodNode("B", MemberId.CONSTRUCTOR_NAME);
        MethodNode aCaller = new MethodNode("T", "callA");
        MethodNode bCaller = new MethodNode("T", "callB");

        graph.addEdge(aCaller, aCtor, 1);
        graph.addEdge(bCaller, bCtor, 1);

        Set<MethodNode> aCallers = graph.getConstructorCallers("A", 1);

        assertTrue(aCallers.contains(aCaller));
        assertFalse(aCallers.contains(bCaller),
                "A change to A's one-argument constructor must not reach a caller of "
                        + "B's one-argument constructor (shared <init> name)");
    }
}
