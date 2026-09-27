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
 * for reverse-BFS transitive impact analysis.
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
     * Returns the set of methods that directly call the given method,
     * matched by exact MethodNode (class + method name).
     * Returns an empty set if no recorded method calls it.
     */
    public Set<MethodNode> getCallers(MethodNode method) {
        return callers.getOrDefault(method, new HashSet<>());
    }

    /**
     * Returns the set of methods that directly call ANY node whose method
     * name matches the given name, regardless of class.
     *
     * This exists because callee class names are frequently recorded as
     * "UNKNOWN" (no Symbol Solver configured), so a real method's node
     * (e.g. Calculator.compute) and its unresolved callee reference
     * (e.g. UNKNOWN.compute, recorded by a caller elsewhere) are
     * different MethodNode instances even though they refer to the same
     * method. This method bridges that gap for multi-hop traversal.
     */
    public Set<MethodNode> getCallersByMethodName(String methodName) {
        Set<MethodNode> result = new HashSet<>();
        for (MethodNode node : getAllNodes()) {
            if (node.getMethodName().equals(methodName)) {
                result.addAll(getCallers(node));
            }
        }
        return result;
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
