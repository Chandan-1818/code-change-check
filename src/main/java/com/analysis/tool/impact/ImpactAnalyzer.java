package com.analysis.tool.impact;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.MemberId;
import com.analysis.tool.graph.MethodNode;

/**
 * Performs reverse BFS over a DependencyGraph to find every method
 * impacted, directly or transitively, by a set of changed methods.
 *
 * Two entry points:
 * - analyzeImpact(graph, changedMethodNames[, maxDepth]) matches by method
 *   NAME only at every level of the traversal. This is required because
 *   callee class names are frequently recorded as "UNKNOWN" (no Symbol
 *   Solver configured): without name-based matching at every hop, a node like
 *   UNKNOWN.compute (an unresolved callee reference) and the real
 *   Calculator.compute node would be treated as unrelated, silently
 *   truncating multi-hop chains at the first unresolved boundary.
 *
 * - analyzeImpact(graph, changedMembers, maxDepth) takes MemberIds, so the
 *   changed member's class and arity are known. The FIRST hop can then be
 *   narrowed to callers whose call-site arity matches the changed member,
 *   which stops a change to one overload from selecting tests that only call
 *   a different-arity overload. Deeper hops stay name-based (node identity
 *   is name-only), so recall is never reduced beyond that first hop.
 *
 * Cycle-safe: a visited-set prevents infinite loops and re-processing of
 * already-impacted nodes, so traversal always terminates.
 *
 * Depth is unlimited by default (D-04): the whole reverse-reachable set is
 * returned. A caller may instead request a maximum depth; when such a limit
 * is reached before the reachable set is exhausted, the result is flagged as
 * truncated so it is never mistaken for a complete answer.
 */
public class ImpactAnalyzer {

    /** Sentinel depth meaning "no limit"; the default for all callers. */
    public static final int UNLIMITED_DEPTH = -1;

    public ImpactSet analyzeImpact(DependencyGraph graph, Set<String> changedMethodNames) {
        return analyzeImpact(graph, changedMethodNames, UNLIMITED_DEPTH);
    }

    /**
     * Reverse BFS with an explicit depth limit, matching by method name at
     * every hop.
     *
     * @param maxDepth {@link #UNLIMITED_DEPTH} (-1) for unlimited traversal, or a
     *                 positive integer for the maximum number of hops. Zero and
     *                 any other negative value are invalid configurations.
     */
    public ImpactSet analyzeImpact(
            DependencyGraph graph,
            Set<String> changedMethodNames,
            int maxDepth) {

        validateDepth(maxDepth);

        Set<MethodNode> seeds = new HashSet<>();
        for (MethodNode node : graph.getAllNodes()) {
            if (changedMethodNames.contains(node.getMethodName())) {
                seeds.add(node);
            }
        }
        return analyze(graph, seeds, Collections.<MethodNode, Integer>emptyMap(),
                Collections.<MethodNode, List<String>>emptyMap(), maxDepth);
    }

    /**
     * Reverse BFS seeded from MemberIds. The changed member's arity is used
     * to narrow the FIRST hop only (see the class comment); members with
     * arity {@link MemberId#UNKNOWN_ARITY} are matched by name, exactly like
     * the name-based entry point.
     *
     * Named distinctly (not an overload of the Set&lt;String&gt; form) because
     * both would erase to the same signature.
     */
    public ImpactSet analyzeMemberImpact(
            DependencyGraph graph,
            Set<MemberId> changedMembers,
            int maxDepth) {

        validateDepth(maxDepth);

        Set<MethodNode> seeds = new HashSet<>();
        Map<MethodNode, Integer> seedArities = new HashMap<>();
        Map<MethodNode, List<String>> seedParameterTypes = new HashMap<>();

        for (MemberId member : changedMembers) {
            Set<MethodNode> matches = matchNodes(graph, member);
            for (MethodNode node : matches) {
                seeds.add(node);
                int arity = member.getArity();
                Integer existing = seedArities.get(node);
                if (existing == null) {
                    seedArities.put(node, arity);
                } else if (existing != arity) {
                    // The same node stands for several members with different
                    // arities, so arity cannot narrow the first hop.
                    seedArities.put(node, MemberId.UNKNOWN_ARITY);
                }

                List<String> types = member.getParameterTypes();
                List<String> existingTypes = seedParameterTypes.get(node);
                if (existingTypes == null) {
                    seedParameterTypes.put(node, types);
                } else if (!existingTypes.equals(types)) {
                    // The node stands for members with different parameter
                    // types, so type information cannot narrow the first hop.
                    seedParameterTypes.put(node, Collections.<String>emptyList());
                }
            }
        }

        return analyze(graph, seeds, seedArities, seedParameterTypes, maxDepth);
    }

    private void validateDepth(int maxDepth) {
        if (maxDepth == 0 || maxDepth < UNLIMITED_DEPTH) {
            throw new IllegalArgumentException(
                    "maxDepth must be -1 (unlimited) or a positive integer, but was " + maxDepth);
        }
    }

    /**
     * Finds the graph nodes a member refers to. Prefers an exact class match
     * and falls back to name-only matching (conservative) when the class is
     * not present in the graph.
     */
    private Set<MethodNode> matchNodes(DependencyGraph graph, MemberId member) {
        Set<MethodNode> exact = new HashSet<>();
        Set<MethodNode> byName = new HashSet<>();

        for (MethodNode node : graph.getAllNodes()) {
            if (!node.getMethodName().equals(member.getMemberName())) {
                continue;
            }
            byName.add(node);
            if (node.getClassName().equals(member.getClassName())) {
                exact.add(node);
            }
        }
        return exact.isEmpty() ? byName : exact;
    }

    private ImpactSet analyze(
            DependencyGraph graph,
            Set<MethodNode> seeds,
            Map<MethodNode, Integer> seedArities,
            Map<MethodNode, List<String>> seedParameterTypes,
            int maxDepth) {

        Set<MethodNode> changedNodes = new HashSet<>(seeds);
        Set<MethodNode> directlyImpacted = new HashSet<>();
        Map<MethodNode, Integer> transitivelyImpacted = new HashMap<>();

        Set<MethodNode> visited = new HashSet<>(seeds);
        Queue<MethodNode> queue = new LinkedList<>(seeds);
        // Records, for each reached caller, the node it calls towards the
        // change (H7 explanation chain). The first parent found wins, which is
        // the shortest path because the traversal is breadth-first.
        Map<MethodNode, MethodNode> parents = new HashMap<>();

        int depth = 0;
        while (!queue.isEmpty() && (maxDepth < 0 || depth < maxDepth)) {
            depth++;
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                MethodNode current = queue.poll();
                for (MethodNode caller : callersOf(graph, current, depth, seedArities, seedParameterTypes)) {
                    if (!visited.contains(caller)) {
                        visited.add(caller);
                        parents.put(caller, current);
                        if (depth == 1) {
                            directlyImpacted.add(caller);
                        } else {
                            transitivelyImpacted.put(caller, depth);
                        }
                        queue.add(caller);
                    }
                }
            }
        }

        // If the loop stopped because the depth limit was reached while the
        // frontier still holds nodes, those nodes are impacted but their own
        // callers were never explored. Truncation is reported only when such
        // an unexplored caller actually exists, so the flag never fires when
        // the reachable set was in fact complete.
        // (Unlimited traversal drains the queue, so it never truncates.)
        boolean truncated = maxDepth > 0 && depth >= maxDepth && hasUnexploredCaller(graph, queue, visited);

        return new ImpactSet(changedNodes, directlyImpacted, transitivelyImpacted, truncated, parents);
    }

    private Set<MethodNode> callersOf(
            DependencyGraph graph,
            MethodNode current,
            int depth,
            Map<MethodNode, Integer> seedArities,
            Map<MethodNode, List<String>> seedParameterTypes) {

        if (depth == 1) {
            Integer arity = seedArities.get(current);
            // A constructor's class is resolved ("new X(...)"), so it must be
            // matched by class, not by the "<init>" name shared by every class.
            if (MemberId.CONSTRUCTOR_NAME.equals(current.getMethodName())
                    && !"UNKNOWN".equals(current.getClassName())) {
                return graph.getConstructorCallers(
                        current.getClassName(),
                        arity == null ? MemberId.UNKNOWN_ARITY : arity);
            }
            if (arity != null && arity >= 0) {
                List<String> declaredTypes = seedParameterTypes.get(current);
                if (declaredTypes != null && !declaredTypes.isEmpty()) {
                    // Same-arity overloads: narrow by the changed member's
                    // declared parameter types (M1).
                    return graph.getCallersByMethodNameAndTypes(
                            current.getMethodName(), arity, declaredTypes);
                }
                return graph.getCallersByMethodNameAndArity(current.getMethodName(), arity);
            }
        }
        return graph.getCallersByMethodName(current.getMethodName());
    }

    private boolean hasUnexploredCaller(
            DependencyGraph graph,
            Queue<MethodNode> frontier,
            Set<MethodNode> visited) {

        for (MethodNode pending : frontier) {
            for (MethodNode caller : graph.getCallersByMethodName(pending.getMethodName())) {
                if (!visited.contains(caller)) {
                    return true;
                }
            }
        }
        return false;
    }
}
