package com.analysis.tool.graph;

/**
 * Whether a member is an ordinary method or a constructor.
 *
 * Constructor members use the sentinel name {@link MemberId#CONSTRUCTOR_NAME}
 * ("&lt;init&gt;"), which no Java method can have, so a constructor can never
 * be confused with a method of the same simple name.
 */
public enum MemberKind {
    METHOD,
    CONSTRUCTOR
}
