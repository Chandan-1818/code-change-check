package com.analysis.tool.preflight;

import java.util.List;

/**
 * Structured outcome of a preflight check (H9).
 *
 * A preflight run inspects a target repository before any analysis, test
 * selection or execution happens, and classifies it into one of three states:
 *
 * <ul>
 *   <li>{@link Status#SUPPORTED} - nothing in the project threatens the
 *       analysis; reduce nothing.</li>
 *   <li>{@link Status#REDUCED} - the project can be analysed, but one or more
 *       detected features (Spring, Mockito, Lombok, reflection) can hide call
 *       edges, so the selection must be reported at REDUCED confidence. The
 *       reasons are available from {@link #getReductions()}.</li>
 *   <li>{@link Status#UNSUPPORTED} - the project cannot be analysed reliably
 *       at all (not a Git repository, multi-module Maven, JUnit 4). It is
 *       refused, with an actionable reason from {@link #getRejections()}.</li>
 * </ul>
 *
 * The state is derived from the two lists rather than passed in, so a caller
 * cannot report SUPPORTED while carrying a rejection: any rejection makes the
 * result UNSUPPORTED.
 */
public final class PreflightResult {

    public enum Status {
        SUPPORTED,
        REDUCED,
        UNSUPPORTED
    }

    private final List<String> rejections;
    private final List<String> reductions;

    public PreflightResult(List<String> rejections, List<String> reductions) {
        this.rejections = List.copyOf(rejections);
        this.reductions = List.copyOf(reductions);
    }

    public Status getStatus() {
        if (!rejections.isEmpty()) {
            return Status.UNSUPPORTED;
        }
        if (!reductions.isEmpty()) {
            return Status.REDUCED;
        }
        return Status.SUPPORTED;
    }

    /** True when the project must be refused (analysis cannot proceed). */
    public boolean isUnsupported() {
        return getStatus() == Status.UNSUPPORTED;
    }

    /** True when analysis may proceed but not at HIGH confidence. */
    public boolean isReduced() {
        return getStatus() == Status.REDUCED;
    }

    /** Reasons the project is refused; empty unless {@link #isUnsupported()}. */
    public List<String> getRejections() {
        return rejections;
    }

    /** Reasons confidence is reduced; empty unless {@link #isReduced()}. */
    public List<String> getReductions() {
        return reductions;
    }

    /** One-line summary, e.g. "REDUCED (2 reason(s))". */
    public String getSummary() {
        Status status = getStatus();
        switch (status) {
            case UNSUPPORTED:
                return "UNSUPPORTED (" + rejections.size() + " reason(s))";
            case REDUCED:
                return "REDUCED (" + reductions.size() + " reason(s))";
            default:
                return "SUPPORTED";
        }
    }

    @Override
    public String toString() {
        return "PreflightResult{status=" + getStatus()
                + ", rejections=" + rejections
                + ", reductions=" + reductions + "}";
    }
}
