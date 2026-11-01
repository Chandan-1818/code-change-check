package com.analysis.tool.testgen;

import java.util.Objects;

/**
 * What happened when one generated case was executed against the target.
 * detail is a Java source literal for RETURNED, a fully qualified exception
 * class name for THREW / CONSTRUCTION_FAILED / UNSUPPORTED_VALUE, and a short
 * explanation for NONDETERMINISTIC.
 */
public final class OracleOutcome {

    public enum Status {
        RETURNED, THREW, TIMED_OUT, CONSTRUCTION_FAILED, UNSUPPORTED_VALUE, NONDETERMINISTIC, NOT_RUN
    }

    private final Status status;
    private final String detail;

    public OracleOutcome(Status status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public Status getStatus() { return status; }

    public String getDetail() { return detail; }

    /** Only RETURNED and THREW can become assertions. */
    public boolean isUsable() {
        return status == Status.RETURNED || status == Status.THREW;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OracleOutcome)) return false;
        OracleOutcome that = (OracleOutcome) o;
        return status == that.status && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, detail);
    }

    @Override
    public String toString() {
        return status + (detail == null || detail.isEmpty() ? "" : " " + detail);
    }
}
