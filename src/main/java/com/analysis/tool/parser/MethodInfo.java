package com.analysis.tool.parser;

import java.util.Objects;

/**
 * Represents a single method declaration extracted from parsed Java source.
 *
 * Current scope: name and body text only, sufficient for detecting
 * whether a method's implementation changed between two versions.
 *
 * Not yet included: parameter types, return type, modifiers, annotations.
 * These can be added later if impact analysis requires signature-level
 * comparison in addition to body comparison.
 */
public class MethodInfo {

    private final String name;
    private final String bodyText;

    public MethodInfo(String name, String bodyText) {
        this.name = name;
        this.bodyText = bodyText;
    }

    public String getName() {
        return name;
    }

    public String getBodyText() {
        return bodyText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodInfo)) return false;
        MethodInfo that = (MethodInfo) o;
        return Objects.equals(name, that.name) && Objects.equals(bodyText, that.bodyText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, bodyText);
    }

    @Override
    public String toString() {
        return "MethodInfo{name='" + name + "'}";
    }
}
