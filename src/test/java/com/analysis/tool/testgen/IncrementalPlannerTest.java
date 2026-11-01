package com.analysis.tool.testgen;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IncrementalPlannerTest {

    private static String entry(String cls, String sig, String hash) {
        return "{\"targetClass\": \"" + cls + "\", \"targetSignature\": \"" + sig
                + "\", \"methodBodyHash\": \"" + hash + "\"}";
    }

    private static final String PREVIOUS = "{\"revision\": \"r1\", \"methods\": ["
            + entry("C", "keep()", "h1") + ","
            + entry("C", "edit()", "h2") + ","
            + entry("C", "gone()", "h3") + ","
            + entry("C", "unknown()", "") + "]}";

    @Test
    public void classifiesAddedModifiedRemovedAndUnchanged() {
        ManifestReader.Data previous = new ManifestReader().read(PREVIOUS);

        IncrementalPlanner.Plan plan = new IncrementalPlanner().plan(previous, Map.of(
                "C\tkeep()", "h1",
                "C\tedit()", "h2-new",
                "C\tfresh()", "h9",
                "C\tunknown()", "h4"));

        assertEquals(Set.of("C\tkeep()"), plan.getUnchanged());
        assertEquals(Set.of("C\tfresh()"), plan.getAdded());
        assertEquals(Set.of("C\tgone()"), plan.getRemoved());
        assertEquals(Set.of("C\tedit()", "C\tunknown()"), plan.getModified(),
                "an unknown previous hash must be treated as modified, not as unchanged");
    }

    @Test
    public void emptyPreviousManifestMakesEverythingAdded() {
        IncrementalPlanner.Plan plan = new IncrementalPlanner().plan(
                ManifestReader.Data.empty(), Map.of("C\ta()", "h"));

        assertEquals(Set.of("C\ta()"), plan.getAdded());
        assertEquals(Set.of(), plan.getRemoved());
    }
}
