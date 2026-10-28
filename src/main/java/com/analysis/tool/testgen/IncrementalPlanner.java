package com.analysis.tool.testgen;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Phase 8B / G6: compares the supported methods of the previous manifest with
 * those of the new revision (blueprint section 12.8).
 *
 * ADDED     not in the previous manifest (includes a method that was skipped
 *           before and is supported now): generate tests.
 * MODIFIED  hash differs, or the previous hash is unknown (empty): keep the old
 *           oracle and re-check it against the new behaviour.
 * REMOVED   in the previous manifest only (includes a method that is no longer
 *           supported): retire its tests.
 * UNCHANGED same hash: keep.
 */
public class IncrementalPlanner {

    public static final class Plan {
        private final Set<String> added = new TreeSet<>();
        private final Set<String> modified = new TreeSet<>();
        private final Set<String> removed = new TreeSet<>();
        private final Set<String> unchanged = new TreeSet<>();

        public Set<String> getAdded() { return added; }
        public Set<String> getModified() { return modified; }
        public Set<String> getRemoved() { return removed; }
        public Set<String> getUnchanged() { return unchanged; }
    }

    public Plan plan(ManifestReader.Data previous, Map<String, String> newSupportedHashes) {
        Plan plan = new Plan();
        Map<String, String> before = previous.getMethodHashes();

        for (Map.Entry<String, String> entry : newSupportedHashes.entrySet()) {
            String key = entry.getKey();
            if (!before.containsKey(key)) {
                plan.added.add(key);
            } else {
                String oldHash = before.get(key);
                if (oldHash.isEmpty() || !oldHash.equals(entry.getValue())) {
                    plan.modified.add(key);
                } else {
                    plan.unchanged.add(key);
                }
            }
        }
        for (String key : before.keySet()) {
            if (!newSupportedHashes.containsKey(key)) {
                plan.removed.add(key);
            }
        }
        return plan;
    }
}
