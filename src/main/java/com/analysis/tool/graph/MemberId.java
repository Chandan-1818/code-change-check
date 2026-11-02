package com.analysis.tool.graph;

import java.util.Objects;

/**
 * Identity of a declared member (method or constructor) precise enough to
 * separate overloads that have different arities.
 *
 * className  - the class that declares the member (may be "UNKNOWN").
 * memberName - simple method name, or {@link #CONSTRUCTOR_NAME} for a
 *              constructor.
 * arity      - number of declared parameters, or {@link #UNKNOWN_ARITY}
 *              (-1) when the arity must not be used to narrow matching.
 * kind       - METHOD or CONSTRUCTOR.
 *
 * Limitation: no type resolution is available, so two overloads with the same
 * name AND the same arity (e.g. f(int) and f(String)) are the SAME MemberId
 * and cannot be told apart. This can only over-select, never hide a change.
 */
public class MemberId {

    /** Arity value meaning "unknown - do not use arity to narrow matching". */
    public static final int UNKNOWN_ARITY = -1;

    /** Sentinel member name used for constructors (no Java method can have it). */
    public static final String CONSTRUCTOR_NAME = "<init>";

    private final String className;
    private final String memberName;
    private final int arity;
    private final MemberKind kind;

    public MemberId(String className, String memberName, int arity, MemberKind kind) {
        this.className = className;
        this.memberName = memberName;
        this.arity = arity;
        this.kind = kind;
    }

    public static MemberId forMethod(String className, String methodName, int arity) {
        return new MemberId(className, methodName, arity, MemberKind.METHOD);
    }

    public static MemberId forConstructor(String className, int arity) {
        return new MemberId(className, CONSTRUCTOR_NAME, arity, MemberKind.CONSTRUCTOR);
    }

    public String getClassName() {
        return className;
    }

    public String getMemberName() {
        return memberName;
    }

    public int getArity() {
        return arity;
    }

    public MemberKind getKind() {
        return kind;
    }

    /** Returns a copy of this id with a different arity (used to disable filtering). */
    public MemberId withArity(int newArity) {
        return new MemberId(className, memberName, newArity, kind);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberId)) return false;
        MemberId that = (MemberId) o;
        return arity == that.arity
                && Objects.equals(className, that.className)
                && Objects.equals(memberName, that.memberName)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, memberName, arity, kind);
    }

    @Override
    public String toString() {
        return className + "." + memberName + (arity < 0 ? "" : "/" + arity);
    }
}
