package com.analysis.tool.impact;

import java.util.List;

/**
 * Result of comparing two sets of methods (old version vs new version)
 * from the same source file.
 */
public class MethodChangeResult {

    private final List<String> addedMethods;
    private final List<String> removedMethods;
    private final List<String> modifiedMethods;
    private final List<String> unchangedMethods;

    public MethodChangeResult(List<String> addedMethods,
                               List<String> removedMethods,
                               List<String> modifiedMethods,
                               List<String> unchangedMethods) {
        this.addedMethods = addedMethods;
        this.removedMethods = removedMethods;
        this.modifiedMethods = modifiedMethods;
        this.unchangedMethods = unchangedMethods;
    }

    public List<String> getAddedMethods() {
        return addedMethods;
    }

    public List<String> getRemovedMethods() {
        return removedMethods;
    }

    public List<String> getModifiedMethods() {
        return modifiedMethods;
    }

    public List<String> getUnchangedMethods() {
        return unchangedMethods;
    }
}
