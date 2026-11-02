package com.analysis.tool.impact;

import com.analysis.tool.graph.MemberId;

import java.util.Set;

/**
 * Result of comparing two sets of members (old version vs new version) from
 * the same source file at MemberId granularity, so overloads with different
 * arities are no longer collapsed into a single name.
 */
public class MemberChangeResult {

    private final Set<MemberId> addedMembers;
    private final Set<MemberId> removedMembers;
    private final Set<MemberId> modifiedMembers;
    private final Set<MemberId> unchangedMembers;

    public MemberChangeResult(Set<MemberId> addedMembers,
                              Set<MemberId> removedMembers,
                              Set<MemberId> modifiedMembers,
                              Set<MemberId> unchangedMembers) {
        this.addedMembers = addedMembers;
        this.removedMembers = removedMembers;
        this.modifiedMembers = modifiedMembers;
        this.unchangedMembers = unchangedMembers;
    }

    public Set<MemberId> getAddedMembers() { return addedMembers; }

    public Set<MemberId> getRemovedMembers() { return removedMembers; }

    public Set<MemberId> getModifiedMembers() { return modifiedMembers; }

    public Set<MemberId> getUnchangedMembers() { return unchangedMembers; }
}
