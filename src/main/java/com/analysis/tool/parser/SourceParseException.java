package com.analysis.tool.parser;

/**
 * Thrown when a Java source cannot be parsed by JavaParser.
 *
 * This is a typed replacement for the unchecked ParseProblemException that
 * JavaParser throws directly, so callers can distinguish "the source did not
 * parse" from other failures and fall back conservatively instead of
 * aborting the whole analysis (GAP-12).
 */
public class SourceParseException extends RuntimeException {

    public SourceParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
