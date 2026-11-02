package com.analysis.tool.eval;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * One reproducible ground-truth evaluation case: a source-code change between
 * two Git revisions and the tests that genuinely need to run for it.
 *
 * <p>The required tests are <em>not</em> hand-invented. In this project they
 * are obtained by execution: a change (a real commit pair, or an injected
 * fault committed as a mutant) is applied, the full suite is run at the
 * changed revision, and the tests that actually fail are recorded as the
 * ground truth (decision D-16). The {@code note} field records that
 * provenance so the numbers can be traced back to the run that produced them.
 *
 * <p>Constructing a case validates it: an incomplete case (blank id or
 * revision, no changed file or method) is rejected with an actionable message
 * rather than being silently evaluated.
 */
public final class GroundTruthCase {

    private final String id;
    private final String baselineRevision;
    private final String changedRevision;
    private final List<String> changedFiles;
    private final List<String> changedMethods;
    private final Set<String> requiredTests;
    private final String note;

    public GroundTruthCase(String id,
                           String baselineRevision,
                           String changedRevision,
                           List<String> changedFiles,
                           List<String> changedMethods,
                           Set<String> requiredTests,
                           String note) {
        this.id = require(id, "id");
        this.baselineRevision = require(baselineRevision, "baselineRevision");
        this.changedRevision = require(changedRevision, "changedRevision");
        this.changedFiles = copyNonEmpty(changedFiles, "changedFiles", id);
        this.changedMethods = copyNonEmpty(changedMethods, "changedMethods", id);
        this.requiredTests = requiredTests == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new TreeSet<>(requiredTests));
        this.note = note == null ? "" : note;
    }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static List<String> copyNonEmpty(List<String> values, String field, String id) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty for case '" + id + "'");
        }
        return List.copyOf(values);
    }

    public String getId() {
        return id;
    }

    public String getBaselineRevision() {
        return baselineRevision;
    }

    public String getChangedRevision() {
        return changedRevision;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public List<String> getChangedMethods() {
        return changedMethods;
    }

    /** The ground-truth set of tests that must run; empty means no test detects the change. */
    public Set<String> getRequiredTests() {
        return requiredTests;
    }

    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return "GroundTruthCase{id=" + id + ", " + baselineRevision + " -> " + changedRevision
                + ", required=" + requiredTests + "}";
    }
}
