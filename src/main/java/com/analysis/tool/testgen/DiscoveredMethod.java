package com.analysis.tool.testgen;

import java.util.Objects;

/**
 * One public method of the target project, classified by the test generator
 * as SUPPORTED (a test can be generated) or SKIPPED (with a human-readable
 * reason, so that nothing is silently dropped).
 *
 * The signature includes parameter types (e.g. "add(int,int)") so that the
 * generator can tell overloads apart even though the rest of the tool still
 * matches methods by name only.
 */
public final class DiscoveredMethod {

    public enum Status { SUPPORTED, SKIPPED }

    private final String packageName;
    private final String className;
    private final String methodName;
    private final String signature;
    private final boolean staticMethod;
    private final Status status;
    private final String reason;

    private DiscoveredMethod(String packageName, String className, String methodName, String signature,
                             boolean staticMethod, Status status, String reason) {
        this.packageName = packageName;
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.staticMethod = staticMethod;
        this.status = status;
        this.reason = reason;
    }

    public static DiscoveredMethod supported(String packageName, String className, String methodName,
                                             String signature, boolean staticMethod) {
        return new DiscoveredMethod(packageName, className, methodName, signature, staticMethod,
                Status.SUPPORTED, "");
    }

    public static DiscoveredMethod skipped(String packageName, String className, String methodName,
                                           String signature, boolean staticMethod, String reason) {
        return new DiscoveredMethod(packageName, className, methodName, signature, staticMethod,
                Status.SKIPPED, reason);
    }

    public String getPackageName() { return packageName; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getSignature() { return signature; }
    public boolean isStatic() { return staticMethod; }
    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public boolean isSupported() { return status == Status.SUPPORTED; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveredMethod)) return false;
        DiscoveredMethod that = (DiscoveredMethod) o;
        return staticMethod == that.staticMethod
                && Objects.equals(packageName, that.packageName)
                && Objects.equals(className, that.className)
                && Objects.equals(signature, that.signature)
                && status == that.status
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, className, signature, staticMethod, status, reason);
    }

    @Override
    public String toString() {
        return className + "." + signature + " -> " + status + (reason.isEmpty() ? "" : " (" + reason + ")");
    }
}
