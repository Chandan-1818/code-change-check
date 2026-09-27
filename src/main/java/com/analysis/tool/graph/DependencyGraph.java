package com.analysis.tool.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a method-level call graph: an edge from caller to callee
 * means the caller method contains a call to the callee method.
 *
 * Supports both forward lookup (what does this method call?) and
 * reverse lookup (what calls this method?) - the latter is required
 * for reverse-BFS transitive impact analysis (a future phase).
 */
public class DependencyGraph {

    private final Map<MethodNode, Set<MethodNode>> callees = new HashMap<>();
    private final Map<MethodNode, Set<MethodNode>> callers = new HashMap<>();

    /**
     * Records that the caller method contains a call to the callee method.
     */
    public void addEdge(MethodNode caller, MethodNode callee) {
        callees.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
        callers.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
    }

    /**
     * Returns the set of methods directly called by the given method.
     * Returns an empty set if the method calls nothing recorded in this graph.
     */
    public Set<MethodNode> getCallees(MethodNode method) {
        return callees.getOrDefault(method, new HashSet<>());
    }

    /**
     * Returns the set of methods that directly call the given method.
     * Returns an empty set if no recorded method calls it.
     */
    public Set<MethodNode> getCallers(MethodNode method) {
        return callers.getOrDefault(method, new HashSet<>());
    }

    /**
     * Returns all methods that appear in this graph, either as a caller
     * or as a callee.
     */
    public Set<MethodNode> getAllNodes() {
        Set<MethodNode> allNodes = new HashSet<>();
        allNodes.addAll(callees.keySet());
        allNodes.addAll(callers.keySet());
        return allNodes;
    }
}
