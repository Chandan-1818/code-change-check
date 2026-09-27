package com.analysis.tool.impact;

import com.analysis.tool.graph.MethodNode;

import java.util.Map;
import java.util.Set;

/**
 * Result of impact analysis: methods that changed (depth 0), methods
 * that directly call a changed method (depth 1), and methods that
 * transitively reach a changed method (depth 2+, with depth recorded).
 */
public class ImpactSet {

    private final Set<MethodNode> changedMethods;
    private final Set<MethodNode> directlyImpacted;
    private final Map<MethodNode, Integer> transitivelyImpacted;

    public ImpactSet(Set<MethodNode> changedMethods,
                      Set<MethodNode> directlyImpacted,
                      Map<MethodNode, Integer> transitivelyImpacted) {
        this.changedMethods = changedMethods;
        this.directlyImpacted = directlyImpacted;
        this.transitivelyImpacted = transitivelyImpacted;
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
}
