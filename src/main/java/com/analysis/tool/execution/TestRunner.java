package com.analysis.tool.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs Maven test invocations in a target repository via ProcessBuilder,
 * and parses Surefire's console summary line for the result.
 *
 * On Windows, "mvn" is a .cmd script rather than a native executable, so
 * the command is invoked through "cmd /c" rather than run directly.
 *
 * Surefire prints one "Tests run: ..." line per test class plus one final
 * aggregate line; the LAST match in the output is used, since it is the
 * run-wide total.
 *
 * Failures and Errors from Surefire's summary are merged into a single
 * "failed" count on TestRunResult (a simplification versus tracking them
 * separately).
 */
public class TestRunner {

    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    public TestRunResult runFullSuite(String repoPath) throws IOException, InterruptedException {
        return runMaven(repoPath, null);
    }

    public TestRunResult runSelectedTests(String repoPath, Set<String> testIdentifiers)
            throws IOException, InterruptedException {
        String filter = String.join(",", testIdentifiers);
        return runMaven(repoPath, filter);
    }

    private TestRunResult runMaven(String repoPath, String testFilter) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        java.util.List<String> command = new java.util.ArrayList<>();
        if (isWindows) {
            command.add("cmd");
            command.add("/c");
            command.add("mvn");
        } else {
            command.add("mvn");
        }
        command.add("test");
        if (testFilter != null) {
            command.add("-Dtest=" + testFilter);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(repoPath));
        processBuilder.redirectErrorStream(true);

        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        long executionTimeMillis = System.currentTimeMillis() - startTime;

        return parseResult(output.toString(), executionTimeMillis, exitCode == 0);
    }

    private TestRunResult parseResult(String output, long executionTimeMillis, boolean buildSucceeded) {
        Matcher matcher = SUMMARY_PATTERN.matcher(output);

        int totalTests = 0;
        int failed = 0;
        int errors = 0;
        int skipped = 0;
        boolean found = false;

        while (matcher.find()) {
            totalTests = Integer.parseInt(matcher.group(1));
            failed = Integer.parseInt(matcher.group(2));
            errors = Integer.parseInt(matcher.group(3));
            skipped = Integer.parseInt(matcher.group(4));
            found = true;
        }

        if (!found) {
            return new TestRunResult(0, 0, 0, 0, executionTimeMillis, buildSucceeded);
        }

        int totalFailedOrError = failed + errors;
        int passed = totalTests - totalFailedOrError - skipped;

        return new TestRunResult(totalTests, passed, totalFailedOrError, skipped, executionTimeMillis, buildSucceeded);
    }
}
