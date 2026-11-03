package com.analysis.tool.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the classpath of a target Maven project by asking Maven itself, so
 * that the generated oracle harness can be compiled and executed against
 * production code whose sources reference third-party libraries (Spring,
 * Hibernate, ...).
 *
 * The resolution runs in an {@link IsolatedRevisionCheckout} of the requested
 * revision: the pom.xml that is read is the one the analysed sources were taken
 * from, and the user's own working tree (and the analysed repository) is never
 * modified. Maven performs the resolution with the project's own configuration,
 * including Spring Boot dependency management, imported BOMs and transitive
 * dependencies; no library path or version is hardcoded here. Only the local
 * Maven repository and the project's configured repositories are used - nothing
 * is downloaded through an external service.
 *
 * A revision without a pom.xml is not a Maven project: the resolved classpath
 * is empty and the caller keeps working with the JDK classpath alone, exactly
 * as before this class existed.
 *
 * When Maven cannot be run or the dependency tree cannot be resolved, an
 * {@link IOException} is thrown whose message names the real cause (Maven's own
 * output) instead of leaving the generation compiler to report misleading
 * "package ... does not exist" errors.
 */
public class MavenClasspathResolver {

    /** Default resolution timeout: five minutes, like the test runner. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;

    private static final String CLASSPATH_FILE = "rts-dependency-classpath.txt";
    private static final String LOG_FILE = "rts-dependency-classpath.log";
    private static final int DIAGNOSTIC_LINES = 25;

    private final long timeoutMillis;

    public MavenClasspathResolver() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    public MavenClasspathResolver(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive, was " + timeoutMillis);
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Returns the resolved classpath of the target project at the given
     * revision, using the platform path separator. The result is empty for a
     * revision that has no pom.xml or whose pom.xml declares no dependency.
     *
     * @throws IOException if Maven is unavailable, the build fails or the
     *                     resolution times out; the message carries Maven's own
     *                     diagnostic.
     */
    public String resolve(String repoPath, String revision) throws IOException, InterruptedException {
        try (IsolatedRevisionCheckout checkout = IsolatedRevisionCheckout.open(repoPath, revision)) {
            Path projectDir = checkout.getDirectory();
            if (!Files.exists(projectDir.resolve("pom.xml"))) {
                return "";
            }
            Path classpathFile = projectDir.resolve(CLASSPATH_FILE);
            Path logFile = projectDir.resolve(LOG_FILE);
            Files.deleteIfExists(classpathFile);
            Files.deleteIfExists(logFile);

            MavenRun run = runMaven(projectDir, logFile);
            if (run.timedOut) {
                throw new IOException("Maven dependency resolution timed out after " + timeoutMillis
                        + " ms while resolving the classpath of revision " + revision + " in " + repoPath);
            }
            if (run.exitCode != 0) {
                throw new IOException(diagnostic(revision, repoPath, run));
            }
            if (!Files.exists(classpathFile)) {
                return "";
            }
            return Files.readString(classpathFile, StandardCharsets.UTF_8).trim();
        }
    }

    private static final class MavenRun {
        final int exitCode;
        final boolean timedOut;
        final String output;

        MavenRun(int exitCode, boolean timedOut, String output) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.output = output;
        }
    }

    /**
     * Runs {@code mvn dependency:build-classpath} in the given project. Maven
     * writes the resolved classpath to {@link #CLASSPATH_FILE} relative to the
     * project directory, and its console output is captured in a log file so it
     * can be quoted verbatim when the resolution fails.
     */
    private MavenRun runMaven(Path projectDir, Path logFile) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> command = new ArrayList<>();
        if (isWindows) {
            // "mvn" is a .cmd script on Windows, so it needs a shell.
            command.add("cmd");
            command.add("/c");
            command.add("mvn");
        } else {
            command.add("mvn");
        }
        command.add("-B");
        command.add("dependency:build-classpath");
        command.add("-Dmdep.outputFile=" + CLASSPATH_FILE);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectDir.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());

        Process process = processBuilder.start();
        process.getOutputStream().close();
        boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!finished) {
            killProcessTree(process);
            return new MavenRun(-1, true, readLog(logFile));
        }
        return new MavenRun(process.exitValue(), false, readLog(logFile));
    }

    private static String readLog(Path logFile) {
        try {
            return Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String diagnostic(String revision, String repoPath, MavenRun run) {
        StringBuilder message = new StringBuilder();
        message.append("Maven could not resolve the dependency classpath of the target project at revision ")
               .append(revision).append(" (").append(repoPath).append("), so the target sources cannot be ")
               .append("compiled and automatic test generation is not possible. ")
               .append("Maven exit code: ").append(run.exitCode).append('.');
        String tail = tail(run.output, DIAGNOSTIC_LINES);
        if (!tail.isEmpty()) {
            message.append(System.lineSeparator()).append("Maven output (last lines):")
                   .append(System.lineSeparator()).append(tail);
        }
        return message.toString();
    }

    /** Last {@code maxLines} non-blank lines, so a long Maven log stays readable. */
    private static String tail(String text, int maxLines) {
        String[] lines = text.split("\\R");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                kept.add(line);
            }
        }
        if (kept.size() > maxLines) {
            kept = kept.subList(kept.size() - maxLines, kept.size());
        }
        return String.join(System.lineSeparator(), kept);
    }

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
}
