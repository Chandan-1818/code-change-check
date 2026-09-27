package com.analysis.tool.impact;

import com.analysis.tool.graph.DependencyGraph;
import com.analysis.tool.graph.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Performs reverse BFS over a DependencyGraph to find every method
 * impacted, directly or transitively, by a set of changed method names.
 *
 * Matches changed method names against graph nodes by method name only
 * (not by MethodNode equality, which includes class name), because the
 * graph's callee class names are currently recorded as "UNKNOWN" pending
 * Symbol Solver integration (see DependencyGraphBuilder).
 *
 * Cycle-safe: a visited-set prevents infinite loops and re-processing
 * of already-impacted nodes. Depth is capped to avoid unrealistic
 * explosion in highly-connected or cyclic call graphs.
 */
public class ImpactAnalyzer {

    private static final int MAX_DEPTH = 5;

    public ImpactSet analyzeImpact(DependencyGraph graph, Set<String> changedMethodNames) {
        Set<MethodNode> changedNodes = new HashSet<>();
        Set<MethodNode> directlyImpacted = new HashSet<>();
        Map<MethodNode, Integer> transitivelyImpacted = new HashMap<>();

        Set<MethodNode> visited = new HashSet<>();
        Queue<MethodNode> queue = new LinkedList<>();

        for (MethodNode node : graph.getAllNodes()) {
            if (changedMethodNames.contains(node.getMethodName())) {
                changedNodes.add(node);
                visited.add(node);
                queue.add(node);
            }
        }

        int depth = 0;
        while (!queue.isEmpty() && depth < MAX_DEPTH) {
            depth++;
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                MethodNode current = queue.poll();
                for (MethodNode caller : graph.getCallers(current)) {
                    if (!visited.contains(caller)) {
                        visited.add(caller);
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

        return new ImpactSet(changedNodes, directlyImpacted, transitivelyImpacted);
    }
}
