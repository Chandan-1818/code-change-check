package com.analysis.tool.impact;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.analysis.tool.graph.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H7 (GAP-24): the parent links recorded during reverse BFS must yield an
 * explanation chain from a selected test back to the changed method, nearest
 * node first, so a selection can state why it was made.
 */
class ImpactSetExplanationChainTest {

    private static final MethodNode CHANGED = new MethodNode("Calculator", "compute");
    private static final MethodNode MID = new MethodNode("Calculator", "multiply");
    private static final MethodNode TEST = new MethodNode("CalculatorTest", "testCompute");

    @Test
    void followsParentsNearestFirstUpToTheChangedSeed() {
        Map<MethodNode, MethodNode> parents = new HashMap<>();
        parents.put(TEST, MID);
        parents.put(MID, CHANGED);

        ImpactSet impactSet = new ImpactSet(
                Set.of(CHANGED), Set.of(MID),
                Collections.singletonMap(TEST, 2), false, parents);

        assertEquals(List.of(TEST, MID, CHANGED), impactSet.explanationChain(TEST));
        assertEquals(List.of(MID, CHANGED), impactSet.explanationChain(MID));
        assertEquals(List.of(CHANGED), impactSet.explanationChain(CHANGED),
                "A changed seed has no parent, so its chain is just itself");
    }

    @Test
    void stopsOnACycle() {
        MethodNode x = new MethodNode("A", "x");
        MethodNode y = new MethodNode("B", "y");
        Map<MethodNode, MethodNode> parents = new HashMap<>();
        parents.put(x, y);
        parents.put(y, x);

        ImpactSet impactSet = new ImpactSet(
                Set.of(x), Collections.emptySet(),
                Collections.emptyMap(), false, parents);

        assertEquals(List.of(x, y), impactSet.explanationChain(x),
                "A parent cycle must terminate rather than loop forever");
    }

    @Test
    void withoutParentTrackingTheChainIsJustTheNode() {
        ImpactSet impactSet = new ImpactSet(
                Set.of(CHANGED), Set.of(TEST),
                Collections.<MethodNode, Integer>emptyMap());

        assertEquals(List.of(TEST), impactSet.explanationChain(TEST));
        assertTrue(impactSet.getParents().isEmpty());
    }

    @Test
    void nullParentsAreTreatedAsEmpty() {
        ImpactSet impactSet = new ImpactSet(
                Set.of(CHANGED), Set.of(TEST),
                Collections.<MethodNode, Integer>emptyMap(), false, null);

        assertTrue(impactSet.getParents().isEmpty());
        assertEquals(List.of(TEST), impactSet.explanationChain(TEST));
    }
}
