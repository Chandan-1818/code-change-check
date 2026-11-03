package com.analysis.tool.testgen;

import java.util.List;
import java.util.Objects;

/**
 * One call to make against the target: an optional receiver expression
 * (null for static methods) and one Java source expression per argument.
 * Parameter types are kept so that a null argument can be cast and
 * overloads stay unambiguous.
 */
public final class OracleCase {

    private final String className;
    private final String methodName;
    private final boolean staticMethod;
    private final String receiverExpression;
    private final List<String> parameterTypes;
    private final List<String> argumentExpressions;

    public OracleCase(String className, String methodName, boolean staticMethod, String receiverExpression,
                      List<String> parameterTypes, List<String> argumentExpressions) {
        if (parameterTypes.size() != argumentExpressions.size()) {
            throw new IllegalArgumentException("Parameter types and arguments differ in length for "
                    + className + "." + methodName);
        }
        if (!staticMethod && receiverExpression == null) {
            throw new IllegalArgumentException("Instance method needs a receiver: "
                    + className + "." + methodName);
        }
        this.className = className;
        this.methodName = methodName;
        this.staticMethod = staticMethod;
        this.receiverExpression = receiverExpression;
        this.parameterTypes = List.copyOf(parameterTypes);
        this.argumentExpressions = List.copyOf(argumentExpressions);
    }

    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public boolean isStatic() { return staticMethod; }
    public String getReceiverExpression() { return receiverExpression; }
    public List<String> getParameterTypes() { return parameterTypes; }
    public List<String> getArgumentExpressions() { return argumentExpressions; }

    /** The arguments as they must appear in source: a null gets a cast to the parameter type. */
    public String renderArguments() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argumentExpressions.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String argument = argumentExpressions.get(i);
            sb.append("null".equals(argument) ? "(" + parameterTypes.get(i) + ") null" : argument);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OracleCase)) return false;
        OracleCase that = (OracleCase) o;
        return staticMethod == that.staticMethod
                && Objects.equals(className, that.className)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(receiverExpression, that.receiverExpression)
                && Objects.equals(parameterTypes, that.parameterTypes)
                && Objects.equals(argumentExpressions, that.argumentExpressions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, staticMethod, receiverExpression,
                parameterTypes, argumentExpressions);
    }

    @Override
    public String toString() {
        return className + "." + methodName + "(" + String.join(", ", argumentExpressions) + ")";
    }
}
