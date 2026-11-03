package com.analysis.tool.graph;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1: the graph records the inferred argument types of every call site and can
 * separate same-arity overloads by those types. The matching is deliberately
 * conservative - an unknown argument type, or an edge with no usable type
 * evidence, always keeps the caller - so type narrowing can only remove a
 * caller that provably passes incompatible arguments to a same-arity overload.
 */
class DependencyGraphOverloadTypeTest {

    @Test
    void sameArityOverloadsAreSeparatedByArgumentType() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode numericCaller = new MethodNode("T", "callInt");
        MethodNode stringCaller = new MethodNode("T", "callString");
        MethodNode callee = new MethodNode("UNKNOWN", "f");

        graph.addEdge(numericCaller, callee, 1, List.of("int"));
        graph.addEdge(stringCaller, callee, 1, List.of("String"));

        Set<MethodNode> intCallers =
                graph.getCallersByMethodNameAndTypes("f", 1, List.of("int"));
        assertTrue(intCallers.contains(numericCaller));
        assertFalse(intCallers.contains(stringCaller),
                "a String call site cannot invoke f(int)");

        Set<MethodNode> stringCallers =
                graph.getCallersByMethodNameAndTypes("f", 1, List.of("String"));
        assertTrue(stringCallers.contains(stringCaller));
        assertFalse(stringCallers.contains(numericCaller));
    }

    @Test
    void unknownArgumentTypeNeverRemovesACaller() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode caller = new MethodNode("T", "unknown");
        MethodNode callee = new MethodNode("UNKNOWN", "f");
        graph.addEdge(caller, callee, 1, List.of(MemberId.UNKNOWN_TYPE));

        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("String"))
                .contains(caller));
        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("int"))
                .contains(caller));
    }

    @Test
    void edgeWithoutTypeEvidenceIsKept() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode caller = new MethodNode("T", "caller");
        MethodNode callee = new MethodNode("UNKNOWN", "f");
        graph.addEdge(caller, callee, 1); // no argument types recorded

        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("int"))
                .contains(caller));
    }

    @Test
    void aCallerThatInvokesBothOverloadsIsNotLost() {
        DependencyGraph graph = new DependencyGraph();
        MethodNode caller = new MethodNode("T", "both");
        MethodNode callee = new MethodNode("UNKNOWN", "f");

        graph.addEdge(caller, callee, 1, List.of("int"));
        graph.addEdge(caller, callee, 1, List.of("String")); // second call site

        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("int"))
                .contains(caller));
        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("String"))
                .contains(caller));
    }

    @Test
    void builderInfersLiteralArgumentTypesAtCallSites() {
        String source = "package demo;\n"
                + "public class T {\n"
                + "    void a() { f(1); }\n"
                + "    void b() { f(\"s\"); }\n"
                + "    void f(int x) {}\n"
                + "    void f(String x) {}\n"
                + "}\n";

        DependencyGraph graph = new DependencyGraphBuilder().buildFromSource(source);

        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("int"))
                .contains(new MethodNode("T", "a")));
        assertFalse(graph.getCallersByMethodNameAndTypes("f", 1, List.of("int"))
                .contains(new MethodNode("T", "b")),
                "f(\"s\") must not be selected for a change to f(int)");
        assertTrue(graph.getCallersByMethodNameAndTypes("f", 1, List.of("String"))
                .contains(new MethodNode("T", "b")));
    }
}
