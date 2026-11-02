package com.analysis.tool.impact;

import com.analysis.tool.graph.MemberId;
import com.analysis.tool.parser.MethodInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares two versions of a file's MEMBERS (methods and constructors) and
 * classifies each MemberId as added, removed, modified or unchanged.
 *
 * This is the arity-aware counterpart of MethodChangeDetector, which groups
 * overloads by name. Here each (class, name, arity, kind) is a distinct
 * MemberId, so changing one overload does not mark an unrelated overload as
 * changed. Overloads that share a name AND an arity (e.g. f(int) and
 * f(String)) are still one MemberId (no type resolution); that can only
 * over-select, never hide a change.
 *
 * The comparison recipe (declaration + body, order-insensitive across
 * duplicates of the same MemberId) matches MethodChangeDetector, so a
 * signature-only change is detected here as well.
 */
public class MemberChangeDetector {

    public MemberChangeResult detectMemberChanges(
            List<MethodInfo> oldMembers,
            List<MethodInfo> newMembers) {

        Map<MemberId, String> oldById = toFingerprintMap(oldMembers);
        Map<MemberId, String> newById = toFingerprintMap(newMembers);

        Set<MemberId> added = new HashSet<>();
        Set<MemberId> removed = new HashSet<>();
        Set<MemberId> modified = new HashSet<>();
        Set<MemberId> unchanged = new HashSet<>();

        for (Map.Entry<MemberId, String> entry : newById.entrySet()) {
            MemberId id = entry.getKey();
            if (!oldById.containsKey(id)) {
                added.add(id);
            } else if (oldById.get(id).equals(entry.getValue())) {
                unchanged.add(id);
            } else {
                modified.add(id);
            }
        }
        for (MemberId id : oldById.keySet()) {
            if (!newById.containsKey(id)) {
                removed.add(id);
            }
        }
        return new MemberChangeResult(added, removed, modified, unchanged);
    }

    private Map<MemberId, String> toFingerprintMap(List<MethodInfo> members) {
        Map<MemberId, List<String>> grouped = new HashMap<>();
        for (MethodInfo member : members) {
            MemberId id = toMemberId(member);
            grouped.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(member.getDeclaration() + "\n" + member.getBodyText());
        }
        Map<MemberId, String> fingerprints = new HashMap<>();
        for (Map.Entry<MemberId, List<String>> entry : grouped.entrySet()) {
            List<String> parts = entry.getValue();
            Collections.sort(parts); // order of duplicates in the file must not matter
            fingerprints.put(entry.getKey(), String.join("\n----\n", parts));
        }
        return fingerprints;
    }

    private MemberId toMemberId(MethodInfo member) {
        if (MemberId.CONSTRUCTOR_NAME.equals(member.getName())) {
            return MemberId.forConstructor(member.getClassName(), member.getArity());
        }
        return MemberId.forMethod(member.getClassName(), member.getName(), member.getArity());
    }
}
