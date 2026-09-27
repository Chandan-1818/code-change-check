package com.analysis.tool.graph;

import java.util.Objects;

/**
 * Represents a single method as a node in the dependency graph.
 *
 * Identified by class name + method name only (not full signature),
 * consistent with this project's existing name-based matching approach
 * (see MethodChangeDetector, TestImpactMapper) and its documented
 * limitation: no overload disambiguation.
 */
public class MethodNode {

    private final String className;
    private final String methodName;

    public MethodNode(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodNode)) return false;
        MethodNode that = (MethodNode) o;
        return Objects.equals(className, that.className)
                && Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName);
    }

    @Override
    public String toString() {
        return className + "." + methodName + "()";
    }
}
