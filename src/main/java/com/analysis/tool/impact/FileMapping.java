package com.analysis.tool.impact;

import java.util.Objects;

/**
 * Pairs a source file path with its corresponding test file path,
 * both relative to the repository root. Used by ChangeImpactAnalyzer
 * to process multiple changed files in a single call.
 */
public class FileMapping {

    private final String sourceFilePath;
    private final String testFilePath;

    public FileMapping(String sourceFilePath, String testFilePath) {
        this.sourceFilePath = sourceFilePath;
        this.testFilePath = testFilePath;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public String getTestFilePath() {
        return testFilePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileMapping)) return false;
        FileMapping that = (FileMapping) o;
        return Objects.equals(sourceFilePath, that.sourceFilePath)
                && Objects.equals(testFilePath, that.testFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFilePath, testFilePath);
    }
}
