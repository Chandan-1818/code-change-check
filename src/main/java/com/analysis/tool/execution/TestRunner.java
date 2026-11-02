package com.analysis.tool.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs Maven test invocations in a target repository via ProcessBuilder.
 *
 * On Windows, "mvn" is a .cmd script rather than a native executable, so the
 * command is invoked through "cmd /c" rather than run directly.
 *
 * H8 hardens this class in three ways:
 * <ul>
 *   <li><b>Timeout (GAP-15).</b> Every run is bounded by a configurable timeout
 *       ({@link #DEFAULT_TIMEOUT_MILLIS} by default). A run that exceeds it is
 *       recorded as {@code timedOut} and its whole process tree is destroyed,
 *       so no Maven/JVM child is left behind. A timeout is reported distinctly
 *       from an ordinary test failure.</li>
 *   <li><b>Per-test results (GAP-16).</b> The Surefire XML reports are parsed
 *       after the run, so the result names the individual passed, failed,
 *       errored and skipped tests and distinguishes assertion failures from
 *       execution errors. When the reports are missing or malformed, the tool
 *       falls back to the console summary and claims no per-test detail.</li>
 *   <li><b>Compilation failures.</b> A build that fails to compile is reported
 *       as {@code compilationFailed} with zero tests, never as a run in which
 *       every test failed.</li>
 * </ul>
 *
 * The raw Maven output is always written to {@code target/maven-execution.log}
 * inside the executed tree, so the log survives even when the run fails.
 */
public class TestRunner {

    /** Default execution timeout: five minutes. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;

    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    private static final String COMPILATION_ERROR_MARKER = "COMPILATION ERROR";

    private final long timeoutMillis;

    public TestRunner() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    public TestRunner(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive, was " + timeoutMillis);
        }
        this.timeoutMillis = timeoutMillis;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public TestRunResult runFullSuite(String repoPath) throws IOException, InterruptedException {
        return runMaven(repoPath, null);
    }

    public TestRunResult runSelectedTests(String repoPath, Set<String> testIdentifiers)
            throws IOException, InterruptedException {
        return runMaven(repoPath, String.join(",", testIdentifiers));
    }

    private TestRunResult runMaven(String repoPath, String testFilter)
            throws IOException, InterruptedException {

        Path surefireReports = Paths.get(repoPath).resolve("target").resolve("surefire-reports");
        deleteReports(surefireReports);

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> command = new ArrayList<>();
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
        Thread pump = new Thread(() -> drain(process, output), "maven-output-pump");
        pump.setDaemon(true);
        pump.start();

        boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        long executionTimeMillis = System.currentTimeMillis() - startTime;

        if (!finished) {
            killProcessTree(process);
            pump.join(2000);
            String text = snapshot(output);
            writeLog(repoPath, text, true);
            return parseResult(surefireReports, text, executionTimeMillis, false, true);
        }

        pump.join(2000);
        int exitCode = process.exitValue();
        String text = snapshot(output);
        writeLog(repoPath, text, false);
        return parseResult(surefireReports, text, executionTimeMillis, exitCode == 0, false);
    }

    private static void drain(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.append(line).append(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            // The stream is closed when the process is killed; nothing to do.
        }
    }

    private static String snapshot(StringBuilder output) {
        synchronized (output) {
            return output.toString();
        }
    }

    /**
     * Builds the result from the Surefire XML reports when they exist, and
     * falls back to the console summary line otherwise. A compilation failure
     * short-circuits to a zero-test result so it is never mistaken for failed
     * tests.
     */
    TestRunResult parseResult(Path surefireReports, String output, long executionTimeMillis,
                              boolean buildSucceeded, boolean timedOut) {

        boolean compilationFailed = output.contains(COMPILATION_ERROR_MARKER);
        List<TestResult> perTest = compilationFailed
                ? Collections.emptyList()
                : SurefireReportParser.parse(surefireReports);

        int totalTests;
        int passed;
        int failed;
        int errors;
        int skipped;

        if (!perTest.isEmpty()) {
            totalTests = perTest.size();
            passed = count(perTest, TestResult.Status.PASSED);
            failed = count(perTest, TestResult.Status.FAILED);
            errors = count(perTest, TestResult.Status.ERROR);
            skipped = count(perTest, TestResult.Status.SKIPPED);
        } else {
            Matcher matcher = SUMMARY_PATTERN.matcher(output);
            boolean found = false;
            int t = 0;
            int f = 0;
            int e = 0;
            int s = 0;
            while (matcher.find()) {
                t = Integer.parseInt(matcher.group(1));
                f = Integer.parseInt(matcher.group(2));
                e = Integer.parseInt(matcher.group(3));
                s = Integer.parseInt(matcher.group(4));
                found = true;
            }
            if (found && !compilationFailed) {
                totalTests = t;
                failed = f;
                errors = e;
                skipped = s;
                passed = t - f - e - s;
            } else {
                totalTests = 0;
                passed = 0;
                failed = 0;
                errors = 0;
                skipped = 0;
            }
        }

        if (compilationFailed) {
            buildSucceeded = false;
        }

        return new TestRunResult(totalTests, passed, failed, errors, skipped,
                executionTimeMillis, buildSucceeded, timedOut, compilationFailed, perTest);
    }

    private static int count(List<TestResult> results, TestResult.Status status) {
        int total = 0;
        for (TestResult result : results) {
            if (result.getStatus() == status) {
                total++;
            }
        }
        return total;
    }

    /**
     * Terminates the process and its descendants, so a timed-out run does not
     * leave a Maven or forked JVM behind.
     */
    private static void killProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroy);
        handle.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void writeLog(String repoPath, String output, boolean timedOut) {
        try {
            Path target = Paths.get(repoPath).resolve("target");
            Files.createDirectories(target);
            StringBuilder log = new StringBuilder();
            if (timedOut) {
                log.append("# Execution timed out; the process tree was terminated.")
                        .append(System.lineSeparator());
            }
            log.append(output);
            Files.writeString(target.resolve("maven-execution.log"), log.toString(),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Capturing the log is best effort.
        }
    }

    private static void deleteReports(Path reportsDir) {
        if (!Files.isDirectory(reportsDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(reportsDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // Best effort: stale reports would only add noise to the XML parse.
        }
    }
}
