package com.analysis.tool.impact;

import com.analysis.tool.graph.MemberId;
import com.analysis.tool.parser.MethodInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H3: member-level change detection. Unlike MethodChangeDetector (which
 * groups by name), this detector keeps overloads with different arities
 * apart, and it also compares constructor declarations.
 */
class MemberChangeDetectorTest {

    private final MemberChangeDetector detector = new MemberChangeDetector();

    private static MethodInfo method(String cls, String name, int arity, String body) {
        return new MethodInfo(cls, name, body, "int " + name + "()", arity, false);
    }

    private static MethodInfo ctor(String cls, int arity, String body) {
        return new MethodInfo(cls, MemberId.CONSTRUCTOR_NAME, body, cls + "()", arity, false);
    }

    @Test
    void separatesOverloadsByArity() {
        List<MethodInfo> oldMembers = Arrays.asList(
                method("Calc", "f", 1, "{ return a; }"),
                method("Calc", "f", 2, "{ return a + b; }"));
        List<MethodInfo> newMembers = Arrays.asList(
                method("Calc", "f", 1, "{ return a + 1; }"),
                method("Calc", "f", 2, "{ return a + b; }"));

        MemberChangeResult result = detector.detectMemberChanges(oldMembers, newMembers);

        assertTrue(result.getModifiedMembers().contains(MemberId.forMethod("Calc", "f", 1)));
        assertTrue(result.getUnchangedMembers().contains(MemberId.forMethod("Calc", "f", 2)));
        assertFalse(result.getModifiedMembers().contains(MemberId.forMethod("Calc", "f", 2)));
    }

    @Test
    void signatureOnlyChangeIsDetected() {
        MethodInfo oldMethod = new MethodInfo("Calc", "f", "{ return a; }", "int f(int a)", 1, false);
        MethodInfo newMethod = new MethodInfo("Calc", "f", "{ return a; }", "long f(int a)", 1, false);

        MemberChangeResult result = detector.detectMemberChanges(
                Collections.singletonList(oldMethod),
                Collections.singletonList(newMethod));

        assertTrue(result.getModifiedMembers().contains(MemberId.forMethod("Calc", "f", 1)));
    }

    @Test
    void addedConstructorIsReportedAsAdded() {
        List<MethodInfo> oldMembers = Collections.singletonList(method("Calc", "g", 0, "{ return 0; }"));
        List<MethodInfo> newMembers = Arrays.asList(
                method("Calc", "g", 0, "{ return 0; }"),
                ctor("Calc", 1, "{ value = seed; }"));

        MemberChangeResult result = detector.detectMemberChanges(oldMembers, newMembers);

        assertTrue(result.getAddedMembers().contains(MemberId.forConstructor("Calc", 1)));
    }

    @Test
    void removedOverloadIsReportedAsRemoved() {
        List<MethodInfo> oldMembers = Arrays.asList(
                method("Calc", "f", 1, "{ return a; }"),
                method("Calc", "f", 2, "{ return a + b; }"));
        List<MethodInfo> newMembers =
                Collections.singletonList(method("Calc", "f", 1, "{ return a; }"));

        MemberChangeResult result = detector.detectMemberChanges(oldMembers, newMembers);

        assertTrue(result.getRemovedMembers().contains(MemberId.forMethod("Calc", "f", 2)));
        assertTrue(result.getUnchangedMembers().contains(MemberId.forMethod("Calc", "f", 1)));
    }

    @Test
    void constructorBodyChangeIsReportedAsModified() {
        MemberChangeResult result = detector.detectMemberChanges(
                Collections.singletonList(ctor("Calc", 1, "{ value = 1; }")),
                Collections.singletonList(ctor("Calc", 1, "{ value = 2; }")));

        assertTrue(result.getModifiedMembers().contains(MemberId.forConstructor("Calc", 1)));
    }
}
