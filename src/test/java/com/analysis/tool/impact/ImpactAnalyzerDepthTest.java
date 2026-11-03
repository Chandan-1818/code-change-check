package com.analysis.tool.impact;

import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.DependencyGraphBuilder;
import com.analysis.tool.graph.MethodNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the H6 configurable impact depth and truncation reporting
 * (D-04: unlimited depth is the default; GAP-07: the old silent cap of 5).
 *
 * Depth semantics: {@link ImpactAnalyzer#UNLIMITED_DEPTH} (-1) means no
 * limit; a positive integer is the maximum number of hops; 0 and any
 * other negative value are invalid configurations.
 */
public class ImpactAnalyzerDepthTest {

    private final ImpactAnalyzer analyzer = new ImpactAnalyzer();

    private static DependencyGraph graphOf(String classBody) {
        String source = "class Chain {\n" + classBody + "\n}";
        return new DependencyGraphBuilder().buildFromSource(source);
    }

    private static Set<String> reachedMethodNames(ImpactSet impact) {
        Set<String> names = new TreeSet<>();
        for (MethodNode node : impact.getChangedMethods()) {
            names.add(node.getMethodName());
        }
        for (MethodNode node : impact.getDirectlyImpacted()) {
            names.add(node.getMethodName());
        }
        for (MethodNode node : impact.getTransitivelyImpacted().keySet()) {
            names.add(node.getMethodName());
        }
        return names;
    }

    private static Set<String> names(String... methodNames) {
        Set<String> set = new TreeSet<>();
        Collections.addAll(set, methodNames);
        return set;
    }

    @Test
    public void fourMethodChainReturnsAllFourWithDefault() {
        // d calls c calls b calls a; change a -> the whole chain is impacted.
        DependencyGraph graph = graphOf(
                "void a() {}\n"
                        + "void b() { a(); }\n"
                        + "void c() { b(); }\n"
                        + "void d() { c(); }");

        ImpactSet impact = analyzer.analyzeImpact(graph, Collections.singleton("a"));

        assertEquals(names("a", "b", "c", "d"), reachedMethodNames(impact),
                "Default (unlimited) traversal must return every method in the chain");
        assertFalse(impact.isTruncated(),
                "Unlimited traversal must never report truncation");
    }

    @Test
    public void chainLongerThanOldCapIsFullyTraversed() {
        // Seven-method chain: the old hard-coded MAX_DEPTH = 5 would stop
        // before reaching g.
        DependencyGraph graph = graphOf(
                "void a() {}\n"
                        + "void b() { a(); }\n"
                        + "void c() { b(); }\n"
                        + "void d() { c(); }\n"
                        + "void e() { d(); }\n"
                        + "void f() { e(); }\n"
                        + "void g() { f(); }");

        ImpactSet impact = analyzer.analyzeImpact(graph, Collections.singleton("a"));

        assertEquals(names("a", "b", "c", "d", "e", "f", "g"), reachedMethodNames(impact),
                "The old depth cap of 5 must be gone; all seven methods must be reached");
    }

    @Test
    @Timeout(2)
    public void cycleTerminatesWithoutInfiniteTraversal() {
        // x calls y and y calls x: a cycle the visited-set must break.
        DependencyGraph graph = graphOf(
                "void x() { y(); }\n"
                        + "void y() { x(); }");

        ImpactSet impact = analyzer.analyzeImpact(graph, Collections.singleton("x"));

        assertEquals(names("x", "y"), reachedMethodNames(impact),
                "A cyclic dependency must terminate and still report both methods");
        assertFalse(impact.isTruncated(),
                "A terminating cycle under unlimited depth must not report truncation");
    }

    @Test
    public void configuredDepthLimitIsRespected() {
        DependencyGraph graph = graphOf(
                "void a() {}\n"
                        + "void b() { a(); }\n"
                        + "void c() { b(); }\n"
                        + "void d() { c(); }");

        ImpactSet impact = analyzer.analyzeImpact(graph, Collections.singleton("a"), 2);

        assertEquals(names("a", "b", "c"), reachedMethodNames(impact),
                "maxDepth = 2 must reach only a, b and c, excluding d");
    }

    @Test
    public void truncationReportedWhenLimitExcludesReachableMethods() {
        DependencyGraph graph = graphOf(
                "void a() {}\n"
                        + "void b() { a(); }\n"
                        + "void c() { b(); }\n"
                        + "void d() { c(); }");

        ImpactSet impact = analyzer.analyzeImpact(graph, Collections.singleton("a"), 2);

        assertTrue(impact.isTruncated(),
                "Excluding reachable method d by the depth limit must be reported as truncation");
    }

    @Test
    public void noTruncationWhenLimitCoversReachableMethods() {
        DependencyGraph graph = graphOf(
                "void a() {}\n"
                        + "void b() { a(); }\n"
                        + "void c() { b(); }\n"
                        + "void d() { c(); }");

        ImpactSet limited = analyzer.analyzeImpact(graph, Collections.singleton("a"), 3);
        assertFalse(limited.isTruncated(),
                "A limit that still covers the whole reachable set must not report truncation");
        assertEquals(names("a", "b", "c", "d"), reachedMethodNames(limited));

        ImpactSet unlimited = analyzer.analyzeImpact(graph, Collections.singleton("a"));
        assertFalse(unlimited.isTruncated(),
                "Unlimited traversal must not report truncation");
    }

    @Test
    public void invalidDepthConfigurationsAreRejected() {
        DependencyGraph graph = graphOf("void a() {}");

        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeImpact(graph, Collections.singleton("a"), 0),
                "Zero must be rejected, not treated as unlimited");
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeImpact(graph, Collections.singleton("a"), -2),
                "Negative values other than -1 must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeImpact(graph, Collections.singleton("a"), Integer.MIN_VALUE),
                "Integer.MIN_VALUE must be rejected");
    }

    @Test
    public void backwardsCompatibleConstructorIsNotTruncated() {
        ImpactSet impact = new ImpactSet(
                new java.util.HashSet<MethodNode>(),
                new java.util.HashSet<MethodNode>(),
                new java.util.HashMap<MethodNode, Integer>());

        assertFalse(impact.isTruncated(),
                "The three-argument constructor must default to not truncated");
    }
}
