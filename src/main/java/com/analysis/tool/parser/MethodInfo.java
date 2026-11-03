package com.analysis.tool.parser;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single method or constructor declaration extracted from parsed Java source.
 *
 * className      - simple name of the declaring class ("UNKNOWN" if not found).
 * name           - simple method name; constructors use "&lt;init&gt;".
 * bodyText       - pretty-printed body, comments stripped.
 * declaration    - modifiers, return type, parameters, throws clause and
 *                  annotations. Lets the change detector see signature-only
 *                  changes and tell overloads apart.
 * arity          - number of declared parameters (-1 if unknown).
 * varargs        - true if the last parameter is a varargs parameter. Arity is
 *                  not a reliable filter for varargs members, so this is carried
 *                  to disable arity-based narrowing (conservative).
 * parameterTypes - the declared parameter type names (as written, normalised to
 *                  their simple form), in declaration order. Empty when they are
 *                  unknown (an older caller, or a member built without them).
 *                  This is M1's type information: it separates two overloads
 *                  that share a name AND an arity but differ in parameter type
 *                  (e.g. f(int) vs f(String)).
 */
public class MethodInfo {

    private final String className;
    private final String name;
    private final String bodyText;
    private final String declaration;
    private final int arity;
    private final boolean varargs;
    private final List<String> parameterTypes;

    public MethodInfo(String name, String bodyText) {
        this("", name, bodyText, "", -1, false, Collections.emptyList());
    }

    public MethodInfo(String name, String bodyText, String declaration) {
        this("", name, bodyText, declaration, -1, false, Collections.emptyList());
    }

    public MethodInfo(String className, String name, String bodyText,
                      String declaration, int arity, boolean varargs) {
        this(className, name, bodyText, declaration, arity, varargs, Collections.emptyList());
    }

    public MethodInfo(String className, String name, String bodyText,
                      String declaration, int arity, boolean varargs,
                      List<String> parameterTypes) {
        this.className = className;
        this.name = name;
        this.bodyText = bodyText;
        this.declaration = declaration;
        this.arity = arity;
        this.varargs = varargs;
        this.parameterTypes = parameterTypes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(parameterTypes));
    }

    public String getClassName() { return className; }
    public String getName() { return name; }
    public String getBodyText() { return bodyText; }
    public String getDeclaration() { return declaration; }
    public int getArity() { return arity; }
    public boolean isVarargs() { return varargs; }
    public List<String> getParameterTypes() { return parameterTypes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodInfo)) return false;
        MethodInfo that = (MethodInfo) o;
        return Objects.equals(name, that.name)
                && Objects.equals(bodyText, that.bodyText)
                && Objects.equals(declaration, that.declaration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, bodyText, declaration);
    }

    @Override
    public String toString() {
        return "MethodInfo{name='" + name + "'}";
    }
}
