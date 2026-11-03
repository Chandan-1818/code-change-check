package com.analysis.tool.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * An ordered, validated collection of {@link GroundTruthCase}s, plus a small
 * dependency-free loader for a text dataset file.
 *
 * <p>Dataset file format (one case per line; blank lines and lines starting
 * with {@code #} are ignored):
 *
 * <pre>
 *   id | baselineRevision | changedRevision | method;method | file;file | requiredTest;requiredTest | note
 * </pre>
 *
 * The {@code note} field is optional. {@code id}, both revisions, at least one
 * changed method and at least one changed file are required; the required-test
 * list may be empty (meaning no test detects the change). Invalid or
 * incomplete data is rejected with the offending line number.
 */
public final class GroundTruthDataset {

    private final List<GroundTruthCase> cases;

    public GroundTruthDataset(List<GroundTruthCase> cases) {
        if (cases == null) {
            throw new IllegalArgumentException("cases must not be null");
        }
        Set<String> ids = new HashSet<>();
        for (GroundTruthCase testCase : cases) {
            if (testCase == null) {
                throw new IllegalArgumentException("cases must not contain null entries");
            }
            if (!ids.add(testCase.getId())) {
                throw new IllegalArgumentException("duplicate case id: '" + testCase.getId() + "'");
            }
        }
        this.cases = List.copyOf(cases);
    }

    public static GroundTruthDataset load(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("ground-truth dataset file not found: " + file);
        }
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static GroundTruthDataset parse(List<String> lines) {
        if (lines == null) {
            throw new IllegalArgumentException("lines must not be null");
        }
        List<GroundTruthCase> parsed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int lineNumber = i + 1;
            try {
                parsed.add(parseLine(trimmed, lineNumber));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        return new GroundTruthDataset(parsed);
    }

    private static GroundTruthCase parseLine(String line, int lineNumber) {
        String[] fields = line.split("\\|", -1);
        if (fields.length < 6) {
            throw new IllegalArgumentException("expected at least 6 fields separated by '|', found "
                    + fields.length);
        }
        String id = trimField(fields[0], "id", lineNumber);
        String baseline = trimField(fields[1], "baselineRevision", lineNumber);
        String changed = trimField(fields[2], "changedRevision", lineNumber);
        List<String> methods = splitList(fields[3]);
        List<String> files = splitList(fields[4]);
        Set<String> required = new TreeSet<>(splitList(fields[5]));
        String note = fields.length > 6 ? fields[6].trim() : "";
        return new GroundTruthCase(id, baseline, changed, files, methods, required, note);
    }

    private static String trimField(String value, String field, int lineNumber) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static List<String> splitList(String value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        for (String part : value.split(";", -1)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    public List<GroundTruthCase> getCases() {
        return cases;
    }

    public int size() {
        return cases.size();
    }

    public boolean isEmpty() {
        return cases.isEmpty();
    }

    public List<GroundTruthCase> asList() {
        return Collections.unmodifiableList(cases);
    }
}
