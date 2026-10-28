package com.analysis.tool.experiment;

import com.analysis.tool.execution.TestRunResult;
import com.analysis.tool.execution.TestRunner;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Phase 8B / G7 part 1: runs a generated suite against a target revision.
 *
 * The target repository is cloned into a temporary directory, the revision is
 * checked out, the generated test sources are copied into that checkout only,
 * and "mvn test" runs there. Nothing is written to the target repository; the
 * temporary directory is deleted afterwards.
 *
 * Preflight: the target pom.xml must mention junit-jupiter. Otherwise Maven is
 * not started and a clear error is thrown.
 */
public class GeneratedSuiteRunner {

    public static final class Result {
        private final TestRunResult run;
        private final Set<String> failingTests;

        Result(TestRunResult run, Set<String> failingTests) {
            this.run = run;
            this.failingTests = failingTests;
        }

        public TestRunResult getRun() { return run; }
        public Set<String> getFailingTests() { return failingTests; }
    }

    /**
     * @param repoPath       target repository (never modified)
     * @param revision       revision to check out in the temporary clone
     * @param generatedDir   revision directory written by GeneratedTestWriter
     *                       (contains src/test/java/rts/generated/*.java)
     */
    public Result run(String repoPath, String revision, Path generatedDir) throws Exception {
        Path generatedSources = generatedDir.resolve("src/test/java/rts/generated");
        if (!Files.isDirectory(generatedSources)) {
            throw new IllegalArgumentException("No generated tests found at " + generatedSources);
        }

        Path workDir = Files.createTempDirectory("generated-suite-");
        try (Git git = Git.cloneRepository()
                .setURI(new File(repoPath).getAbsoluteFile().toURI().toString())
                .setDirectory(workDir.toFile())
                .call()) {
            git.checkout().setName(revision).setForce(true).call();

            Path pom = workDir.resolve("pom.xml");
            if (!Files.exists(pom)
                    || !Files.readString(pom, StandardCharsets.UTF_8).contains("junit-jupiter")) {
                throw new IllegalStateException("Target pom.xml at " + revision
                        + " does not declare junit-jupiter; generated JUnit 5 tests cannot run");
            }

            Path destination = workDir.resolve("src/test/java/rts/generated");
            Files.createDirectories(destination);
            try (Stream<Path> files = Files.list(generatedSources)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    Files.copy(file, destination.resolve(file.getFileName()));
                }
            }

            TestRunResult run = new TestRunner().runFullSuite(workDir.toString());
            Set<String> failing = FaultInjectionRunner.readFailingTests(
                    workDir.resolve("target").resolve("surefire-reports"));
            return new Result(run, failing);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                path.toFile().setWritable(true);
                path.toFile().delete();
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory
        }
    }
}
