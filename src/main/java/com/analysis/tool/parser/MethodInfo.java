package com.analysis.tool.parser;

import java.util.Objects;

/**
 * A single method declaration extracted from parsed Java source.
 *
 * name        - simple method name (the graph still matches by name).
 * bodyText    - pretty-printed body, comments stripped.
 * declaration - modifiers, return type, parameters, throws clause and
 *               annotations. Lets the change detector see signature-only
 *               changes and tell overloads apart.
 */
public class MethodInfo {

    private final String name;
    private final String bodyText;
    private final String declaration;

    public MethodInfo(String name, String bodyText) {
        this(name, bodyText, "");
    }

    public MethodInfo(String name, String bodyText, String declaration) {
        this.name = name;
        this.bodyText = bodyText;
        this.declaration = declaration;
    }

    public String getName() { return name; }

    public String getBodyText() { return bodyText; }

    public String getDeclaration() { return declaration; }

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
