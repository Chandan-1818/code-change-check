package com.analysis.tool.experiment;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.testgen.GeneratedTestWriter;
import com.analysis.tool.testgen.TestEmitter;
import com.analysis.tool.testgen.TestGenerator;
import com.analysis.tool.testsupport.SamplePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance criterion (1) on the real sample at V16 = 7059f4c: tests generated
 * for its production classes must compile and pass at that revision.
 * Launches a real Maven build (slow).
 */
public class GeneratedSuiteRunnerIntegrationTest {

    private static final String V16 = "7059f4c";

    @TempDir
    Path tempDir;

    @Test
    public void generatedSuitePassesAtItsBaselineRevision() throws Exception {
        List<String> sources = new ArrayList<>();
        GitAnalyzer git = new GitAnalyzer(SamplePaths.SAMPLE_PROJECT_PATH);
        try {
            for (String path : git.listFilesAtRevision(V16)) {
                if (path.startsWith("src/main/java/") && path.endsWith(".java")) {
                    sources.add(git.getFileContentAtRevision(V16, path));
                }
            }
        } finally {
            git.close();
        }
        assertTrue(!sources.isEmpty(), "no production sources found at " + V16);

        TestEmitter.Result generated = new TestGenerator().generate(sources, "sample-project", V16);
        assertTrue(generated.getGeneratedCount() > 0, "nothing was generated:\n" + generated.getManifest());

        Path dir = new GeneratedTestWriter().write(tempDir.resolve("generated-tests"), "sample-project", V16, generated);

        GeneratedSuiteRunner.Result result =
                new GeneratedSuiteRunner().run(SamplePaths.SAMPLE_PROJECT_PATH, V16, dir);

        List<String> failingGenerated = result.getFailingTests().stream()
                .filter(n -> n.startsWith("gen_")).collect(Collectors.toList());
        System.out.println("Generated tests: " + generated.getGeneratedCount()
                + "; skipped: " + generated.getSkippedCount()
                + "; suite run: " + result.getRun());

        assertTrue(result.getRun().isBuildSucceeded(),
                "build failed; failing: " + result.getFailingTests() + "\nrun: " + result.getRun());
        assertEquals(List.of(), failingGenerated, "generated tests failed at their own baseline");
        assertTrue(result.getRun().getTotalTests() >= generated.getGeneratedCount(),
                "fewer tests ran than were generated: " + result.getRun());
    }
}
