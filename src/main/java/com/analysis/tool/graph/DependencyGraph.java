package com.analysis.tool.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
     * Call-site arities per edge: caller -> callee -> the set of argument
     * counts seen at every call site between the two nodes. A caller that
     * invokes the same method name more than once accumulates every arity, so
     * no earlier call site is lost. {@link MemberId#UNKNOWN_ARITY} (-1) means
     * an arity that was never recorded and matches every query.
     */
    private final Map<MethodNode, Map<MethodNode, Set<Integer>>> edgeArities = new HashMap<>();

    /**
     * Call-site argument type names per edge: caller -> callee -> the set of
     * per-call-site argument type lists (M1). Each list holds the inferred
     * (simple) type of every argument, {@link MemberId#UNKNOWN_TYPE} ("?")
     * when it could not be inferred. Accumulating one list per call site means
     * a caller that invokes the same name with different argument types still
     * matches, so an incomplete or partial inference can only over-select,
     * never hide a change.
     */
    private final Map<MethodNode, Map<MethodNode, Set<List<String>>>> edgeArgTypes = new HashMap<>();

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
        addEdge(caller, callee, callSiteArity, Collections.<String>emptyList());
    }

    /**
     * Records a call edge together with the inferred type of every argument at
     * the call site (M1). Both the arity and the argument types are accumulated
     * per edge, so several call sites between the same two nodes cannot erase
     * one another.
     */
    public void addEdge(MethodNode caller, MethodNode callee, int callSiteArity,
                        List<String> callSiteArgTypes) {
        callees.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
        callers.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
        edgeArities.computeIfAbsent(caller, k -> new HashMap<>())
                .computeIfAbsent(callee, k -> new HashSet<>())
                .add(callSiteArity);
        edgeArgTypes.computeIfAbsent(caller, k -> new HashMap<>())
                .computeIfAbsent(callee, k -> new HashSet<>())
                .add(new ArrayList<>(callSiteArgTypes));
    }

    /** Recorded call-site arities for one edge (empty when none was recorded). */
    private Set<Integer> aritiesOf(MethodNode caller, MethodNode callee) {
        return edgeArities.getOrDefault(caller, Collections.<MethodNode, Set<Integer>>emptyMap())
                .getOrDefault(callee, Collections.<Integer>emptySet());
    }

    /**
     * True when an edge whose recorded arities are {@code recorded} can carry a
     * call to a member of the given arity. Unknown arities (no record, or a
     * recorded -1) match every query, so a caller is only excluded when every
     * call site it makes provably has a different arity.
     */
    private boolean arityMatches(Set<Integer> recorded, int arity) {
        if (recorded.isEmpty() || arity < 0) {
            return true;
        }
        return recorded.contains(arity) || recorded.contains(MemberId.UNKNOWN_ARITY);
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
                if (arityMatches(aritiesOf(caller, node), arity)) {
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
                if (arityMatches(aritiesOf(caller, node), arity)) {
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

    /**
     * Returns the callers of any node named {@code methodName} whose call-site
     * arity matches AND whose inferred call-site argument types are possibly
     * compatible (M1) with the changed member's declared parameter types.
     *
     * A caller is kept whenever its argument types cannot be proven disjoint
     * from the declared ones (an unknown argument is always compatible), so
     * this can only remove a caller that provably cannot invoke the changed
     * overload - recall is never reduced by type narrowing.
     */
    public Set<MethodNode> getCallersByMethodNameAndTypes(
            String methodName, int arity, List<String> declaredParameterTypes) {

        Set<MethodNode> result = new HashSet<>();
        for (MethodNode node : getAllNodes()) {
            if (!node.getMethodName().equals(methodName)) {
                continue;
            }
            for (MethodNode caller : getCallers(node)) {
                if (edgeMatches(caller, node, arity, declaredParameterTypes)) {
                    result.add(caller);
                }
            }
        }
        return result;
    }

    private boolean edgeMatches(MethodNode caller, MethodNode callee, int arity,
                                List<String> declaredParameterTypes) {
        if (!arityMatches(aritiesOf(caller, callee), arity)) {
            return false;
        }
        if (declaredParameterTypes == null || declaredParameterTypes.isEmpty()) {
            return true;
        }
        Set<List<String>> recorded = edgeArgTypes
                .getOrDefault(caller, Collections.<MethodNode, Set<List<String>>>emptyMap())
                .getOrDefault(callee, Collections.<List<String>>emptySet());
        return typesMatch(recorded, declaredParameterTypes);
    }

    /**
     * True when at least one recorded call-site argument-type list is possibly
     * compatible with the changed member's declared parameter types.
     *
     * Only call sites with the same arity as the declaration carry evidence;
     * if none of them does, the edge is kept (an unknown overload cannot be
     * excluded). This guarantees type narrowing can only remove a caller that
     * provably passes incompatible arguments to a same-arity overload.
     */
    private boolean typesMatch(Set<List<String>> recorded, List<String> declared) {
        if (recorded.isEmpty()) {
            return true;
        }
        boolean sawComparable = false;
        for (List<String> argTypes : recorded) {
            if (argTypes == null || argTypes.size() != declared.size()) {
                continue;
            }
            sawComparable = true;
            boolean allCompatible = true;
            for (int i = 0; i < declared.size(); i++) {
                if (!MemberId.possiblyCompatible(declared.get(i), argTypes.get(i))) {
                    allCompatible = false;
                    break;
                }
            }
            if (allCompatible) {
                return true;
            }
        }
        return !sawComparable;
    }
}
