package com.analysis.tool.experiment;

import com.analysis.tool.git.GitAnalyzer;
import com.analysis.tool.metrics.StaticCoverage;
import com.analysis.tool.testgen.GeneratedTestWriter;
import com.analysis.tool.testgen.TestEmitter;
import com.analysis.tool.testgen.TestGenerator;
import com.analysis.tool.testsupport.SamplePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mandatory "project with no tests" case on the real sample at V16 = 7059f4c.
 * Generated tests are run in a clone whose own src/test was deleted, so only
 * generated tests exist. Coverage is STATIC REACHABILITY, not runtime coverage.
 * Prints the figures; asserts only invariants. Slow (one Maven run).
 */
public class NoExistingTestsProofTest {

    private static final String V16 = "7059f4c";

    @TempDir
    Path tempDir;

    @Test
    public void generatedTestsWorkAndRaiseCoverageWhenTheProjectHasNoTests() throws Exception {
        List<String> production = new ArrayList<>();
        List<String> manual = new ArrayList<>();
        GitAnalyzer git = new GitAnalyzer(SamplePaths.SAMPLE_PROJECT_PATH);
        try {
            for (String path : git.listFilesAtRevision(V16)) {
                if (!path.endsWith(".java")) {
                    continue;
                }
                if (path.startsWith("src/main/java/")) {
                    production.add(git.getFileContentAtRevision(V16, path));
                } else if (path.startsWith("src/test/java/")) {
                    manual.add(git.getFileContentAtRevision(V16, path));
                }
            }
        } finally {
            git.close();
        }

        TestEmitter.Result generated = new TestGenerator().generate(production, "sample-project", V16);
        List<String> generatedSources = new ArrayList<>(generated.getFiles().values());

        StaticCoverage coverage = new StaticCoverage();
        StaticCoverage.Result none = coverage.compute(production, List.of());
        StaticCoverage.Result handWritten = coverage.compute(production, manual);
        StaticCoverage.Result generatedOnly = coverage.compute(production, generatedSources);
        List<String> both = new ArrayList<>(manual);
        both.addAll(generatedSources);
        StaticCoverage.Result combined = coverage.compute(production, both);

        System.out.println("Static reachability coverage at V16 (production methods: "
                + none.getProduction().size() + ")");
        System.out.println("  no tests            : " + none.getCovered().size());
        System.out.println("  hand-written only   : " + handWritten.getCovered().size()
                + "  uncovered=" + handWritten.getUncovered());
        System.out.println("  generated only      : " + generatedOnly.getCovered().size()
                + "  uncovered=" + generatedOnly.getUncovered());
        System.out.println("  hand-written + gen. : " + combined.getCovered().size()
                + "  uncovered=" + combined.getUncovered());
        System.out.println("  generated tests: " + generated.getGeneratedCount()
                + "; skipped methods: " + generated.getSkippedCount());

        assertEquals(0, none.getCovered().size());
        assertTrue(combined.getCovered().containsAll(handWritten.getCovered()));
        assertTrue(combined.getCovered().size() >= handWritten.getCovered().size());
        assertTrue(generatedOnly.getCovered().size() > 0, "generated tests cover nothing");

        Path dir = new GeneratedTestWriter().write(tempDir.resolve("generated-tests"),
                "sample-project", V16, generated);
        GeneratedSuiteRunner.Result run = new GeneratedSuiteRunner()
                .run(SamplePaths.SAMPLE_PROJECT_PATH, V16, dir, true);
        System.out.println("  stripped run: " + run.getRun());

        Set<String> failing = run.getFailingTests().stream().collect(Collectors.toSet());
        assertTrue(run.getRun().isBuildSucceeded(), "build failed; failing: " + failing);
        assertEquals(Set.of(), failing);
        assertEquals(generated.getGeneratedCount(), run.getRun().getTotalTests(),
                "with src/test removed, exactly the generated tests must have run");
        assertTrue(Files.exists(dir.resolve("manifest.json")));
    }
}
