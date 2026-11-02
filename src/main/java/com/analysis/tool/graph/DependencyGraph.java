package com.analysis.tool.graph;

import java.util.Collections;
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
     * Call-site arity per edge: caller -> callee -> number of arguments at the
     * call site. Arity is -1 when it is not known. Used to tell overloads with
     * different arities apart at the first impact hop.
     */
    private final Map<MethodNode, Map<MethodNode, Integer>> edgeArities = new HashMap<>();

    /**
     * Records that the caller method contains a call to the callee method.
     */
    public void addEdge(MethodNode caller, MethodNode callee) {
        addEdge(caller, callee, -1);
    }

    /**
     * Records that the caller method contains a call to the callee method,
     * with the number of arguments seen at that call site.
     */
    public void addEdge(MethodNode caller, MethodNode callee, int callSiteArity) {
        callees.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
        callers.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
        edgeArities.computeIfAbsent(caller, k -> new HashMap<>()).put(callee, callSiteArity);
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
     * Returns the set of methods that directly call ANY node whose method
     * name matches the given name AND whose recorded call-site arity equals
     * the given arity.
     *
     * Used to separate overloads with different arities at the first impact
     * hop, so changing one overload does not select tests for an unrelated
     * overload with a different arity. A caller whose call-site arity is
     * unknown (-1, e.g. an edge added without an argument count) is still
     * returned, because excluding it could hide a genuinely affected test.
     */
    public Set<MethodNode> getCallersByMethodNameAndArity(String methodName, int arity) {
        Set<MethodNode> result = new HashSet<>();
        for (MethodNode node : getAllNodes()) {
            if (!node.getMethodName().equals(methodName)) {
                continue;
            }
            for (MethodNode caller : getCallers(node)) {
                Integer recorded = edgeArities
                        .getOrDefault(caller, Collections.<MethodNode, Integer>emptyMap())
                        .get(node);
                if (recorded == null || recorded == arity || recorded < 0) {
                    result.add(caller);
                }
            }
        }
        return result;
    }

    /**
     * Returns the methods that directly call the CONSTRUCTOR of the given
     * class, optionally narrowed by call-site arity.
     *
     * Constructors must not be matched by the shared "&lt;init&gt;" name
     * across every class: unlike ordinary method callees (whose class is
     * unresolved and therefore matched, correctly, by name), a constructor
     * node's class is resolved syntactically from "new X(...)". Matching the
     * name alone would make a change to any one-argument constructor select
     * every test that constructs any OTHER class with one argument.
     *
     * Callers of the exact class are returned. Callers of the unresolved
     * "UNKNOWN.&lt;init&gt;" node (produced by super(...) invocations) are also
     * returned: this is conservative - it may over-select, but it never
     * hides a subclass constructor reached through super(...).
     *
     * @param arity the changed constructor's arity, or
     *              {@link MemberId#UNKNOWN_ARITY} (-1) to include every arity.
     */
    public Set<MethodNode> getConstructorCallers(String className, int arity) {
        Set<MethodNode> result = new HashSet<>();
        for (MethodNode node : getAllNodes()) {
            if (!MemberId.CONSTRUCTOR_NAME.equals(node.getMethodName())) {
                continue;
            }
            if (!node.getClassName().equals(className)
                    && !"UNKNOWN".equals(node.getClassName())) {
                continue;
            }
            for (MethodNode caller : getCallers(node)) {
                Integer recorded = edgeArities
                        .getOrDefault(caller, Collections.<MethodNode, Integer>emptyMap())
                        .get(node);
                if (arity < 0 || recorded == null || recorded == arity || recorded < 0) {
                    result.add(caller);
                }
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
