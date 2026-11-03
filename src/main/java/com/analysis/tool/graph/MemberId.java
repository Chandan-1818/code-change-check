package com.analysis.tool.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Identity of a declared member (method or constructor) precise enough to
 * separate overloads that have different arities OR different parameter types.
 *
 * className      - the class that declares the member (may be "UNKNOWN").
 * memberName     - simple method name, or {@link #CONSTRUCTOR_NAME} for a
 *                  constructor.
 * arity          - number of declared parameters, or {@link #UNKNOWN_ARITY}
 *                  (-1) when the arity must not be used to narrow matching.
 * kind           - METHOD or CONSTRUCTOR.
 * parameterTypes - declared parameter type names in order, normalised to their
 *                  simple form. Empty means "unknown - do not narrow by type".
 *
 * Type information (M1): when the declared parameter types are known, two
 * overloads that share a name AND an arity but differ in parameter type
 * (e.g. f(int) and f(String)) are now DISTINCT MemberIds. When the types are
 * unknown (an older caller that used the 4-argument constructor), the identity
 * degrades exactly to the previous (class, name, arity, kind) behaviour, which
 * can only over-select, never hide a change.
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
    private final List<String> parameterTypes;

    public MemberId(String className, String memberName, int arity, MemberKind kind) {
        this(className, memberName, arity, kind, Collections.emptyList());
    }

    public MemberId(String className, String memberName, int arity, MemberKind kind,
                    List<String> parameterTypes) {
        this.className = className;
        this.memberName = memberName;
        this.arity = arity;
        this.kind = kind;
        this.parameterTypes = parameterTypes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(parameterTypes));
    }

    public static MemberId forMethod(String className, String methodName, int arity) {
        return new MemberId(className, methodName, arity, MemberKind.METHOD);
    }

    public static MemberId forMethod(String className, String methodName, int arity,
                                     List<String> parameterTypes) {
        return new MemberId(className, methodName, arity, MemberKind.METHOD, parameterTypes);
    }

    public static MemberId forConstructor(String className, int arity) {
        return new MemberId(className, CONSTRUCTOR_NAME, arity, MemberKind.CONSTRUCTOR);
    }

    public static MemberId forConstructor(String className, int arity, List<String> parameterTypes) {
        return new MemberId(className, CONSTRUCTOR_NAME, arity, MemberKind.CONSTRUCTOR, parameterTypes);
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

    /**
     * Declared parameter type names (simple form) in order, or an empty list
     * when the types are unknown and must not narrow matching.
     */
    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    /** True when the declared parameter types are known and usable. */
    public boolean hasParameterTypes() {
        return !parameterTypes.isEmpty();
    }

    /** Returns a copy of this id with a different arity (used to disable filtering). */
    public MemberId withArity(int newArity) {
        return new MemberId(className, memberName, newArity, kind, parameterTypes);
    }

    /** Returns a copy of this id with no parameter-type information. */
    public MemberId withoutParameterTypes() {
        return new MemberId(className, memberName, arity, kind);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberId)) return false;
        MemberId that = (MemberId) o;
        return arity == that.arity
                && Objects.equals(className, that.className)
                && Objects.equals(memberName, that.memberName)
                && kind == that.kind
                && Objects.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, memberName, arity, kind, parameterTypes);
    }

    @Override
    public String toString() {
        return className + "." + memberName
                + (arity < 0 ? "" : "/" + arity)
                + (parameterTypes.isEmpty() ? "" : parameterTypes);
    }

    // ------------------------------------------------------------------
    // M1: conservative parameter-type matching.
    //
    // The declared type of a parameter and the type inferred for a call-site
    // argument are both reduced to a coarse category. Two types are treated as
    // POSSIBLY compatible (so the caller is kept) except when they belong to
    // two clearly disjoint categories, which in Java cannot be satisfied by any
    // argument expression of that form. This is deliberately conservative: an
    // unknown type is compatible with everything, so recall is never reduced
    // by type narrowing - it can only remove a caller that provably cannot
    // invoke the changed overload.
    // ------------------------------------------------------------------

    /** Normalises a type name to its simple form (strips package, generics, arrays). */
    public static String simpleTypeName(String typeName) {
        if (typeName == null) {
            return "";
        }
        String t = typeName.trim();
        int angle = t.indexOf('<');
        if (angle >= 0) {
            t = t.substring(0, angle);
        }
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2).trim();
        }
        while (t.endsWith("...")) {
            t = t.substring(0, t.length() - 3).trim();
        }
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return t.trim();
    }

    /** Sentinel used for a call-site argument whose type could not be inferred. */
    public static final String UNKNOWN_TYPE = "?";

    private static String categoryOf(String simpleType) {
        switch (simpleType) {
            case "byte": case "short": case "int": case "long":
            case "float": case "double": case "char":
            case "Byte": case "Short": case "Integer": case "Long":
            case "Float": case "Double": case "Character":
                return "NUM";
            case "boolean": case "Boolean":
                return "BOOL";
            case "String": case "CharSequence":
                return "STR";
            default:
                return "REF";
        }
    }

    /**
     * True when an argument whose (simple) type is {@code actual} could be used
     * for a parameter declared as {@code declared}. Unknown/empty values are
     * compatible with anything (conservative).
     */
    public static boolean possiblyCompatible(String declared, String actual) {
        if (declared == null || actual == null
                || declared.isEmpty() || actual.isEmpty()
                || UNKNOWN_TYPE.equals(declared) || UNKNOWN_TYPE.equals(actual)) {
            return true;
        }
        String d = categoryOf(simpleTypeName(declared));
        String a = categoryOf(simpleTypeName(actual));
        if ("REF".equals(d) || "REF".equals(a)) {
            return true;
        }
        return d.equals(a);
    }
}
