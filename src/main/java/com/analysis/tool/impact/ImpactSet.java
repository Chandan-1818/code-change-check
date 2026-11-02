package com.analysis.tool.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.analysis.tool.graph.MethodNode;

/**
 * Result of impact analysis: methods that changed (depth 0), methods
 * that directly call a changed method (depth 1), and methods that
 * transitively reach a changed method (depth 2+, with depth recorded).
 *
 * truncated: true when a finite depth limit stopped the traversal before
 * the full reachable set was explored, so this result is a partial view
 * and must not be presented as complete.
 *
 * parents (H7): for each impacted node, the node it calls on the shortest
 * path back to a changed method (the "parent" towards the change). Following
 * the parent links yields an explanation chain such as
 * testCompute -> compute -> multiply, so a selection can state WHY a test was
 * chosen. Changed seeds have no parent. The map is empty when the caller did
 * not request parent tracking.
 */
public class ImpactSet {

    private final Set<MethodNode> changedMethods;
    private final Set<MethodNode> directlyImpacted;
    private final Map<MethodNode, Integer> transitivelyImpacted;
    private final boolean truncated;
    private final Map<MethodNode, MethodNode> parents;

    public ImpactSet(Set<MethodNode> changedMethods,
                      Set<MethodNode> directlyImpacted,
                      Map<MethodNode, Integer> transitivelyImpacted) {
        this(changedMethods, directlyImpacted, transitivelyImpacted, false);
    }

    public ImpactSet(Set<MethodNode> changedMethods,
                      Set<MethodNode> directlyImpacted,
                      Map<MethodNode, Integer> transitivelyImpacted,
                      boolean truncated) {
        this(changedMethods, directlyImpacted, transitivelyImpacted, truncated,
                Collections.<MethodNode, MethodNode>emptyMap());
    }

    public ImpactSet(Set<MethodNode> changedMethods,
                      Set<MethodNode> directlyImpacted,
                      Map<MethodNode, Integer> transitivelyImpacted,
                      boolean truncated,
                      Map<MethodNode, MethodNode> parents) {
        this.changedMethods = changedMethods;
        this.directlyImpacted = directlyImpacted;
        this.transitivelyImpacted = transitivelyImpacted;
        this.truncated = truncated;
        this.parents = parents == null
                ? Collections.<MethodNode, MethodNode>emptyMap()
                : parents;
    }

    public Set<MethodNode> getChangedMethods() {
        return changedMethods;
    }

    public Set<MethodNode> getDirectlyImpacted() {
        return directlyImpacted;
    }

    public Map<MethodNode, Integer> getTransitivelyImpacted() {
        return transitivelyImpacted;
    }

    public boolean isTruncated() {
        return truncated;
    }

    /** For each impacted node, the node it calls towards a changed method. */
    public Map<MethodNode, MethodNode> getParents() {
        return parents;
    }

    /**
     * Returns the call chain from the given impacted node back to the changed
     * method it reaches, nearest node first (e.g. [testCompute, compute,
     * multiply]). The chain ends at a changed seed (no parent) or when a cycle
     * is detected.
     */
    public List<MethodNode> explanationChain(MethodNode node) {
        List<MethodNode> chain = new ArrayList<>();
        Set<MethodNode> guard = new HashSet<>();
        MethodNode current = node;
        while (current != null && guard.add(current)) {
            chain.add(current);
            current = parents.get(current);
        }
        return chain;
    }
}
