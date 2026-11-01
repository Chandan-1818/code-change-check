package com.analysis.tool.cli;

import com.analysis.tool.experiment.GeneratedSuiteRunner;
import com.analysis.tool.testsupport.SamplePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8B acceptance criterion (3) on the real sample history: initial suite at
 * V13, then incremental updates V13 -> V14 (calculateTax modified), V14 -> V15
 * (quote added), V15 -> V16 (compute removed). The V14 suite is then executed,
 * where only the modified method's tests may fail. Slow (starts Maven once).
 *   V13 = d85a3b7, V14 = efc30d0, V15 = 3337a0b, V16 = 7059f4c
 */
class GenerateTestsCliRealHistoryTest {

    private static final String V13 = "d85a3b7";
    private static final String V14 = "efc30d0";
    private static final String V15 = "3337a0b";
    private static final String V16 = "7059f4c";

    @TempDir
    Path outDir;

    private String cli(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = GenerateTestsCli.run(args,
                new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        String text = o.toString(StandardCharsets.UTF_8);
        System.out.println(text);
        assertEquals(0, code, "stderr: " + e.toString(StandardCharsets.UTF_8) + "\nstdout:\n" + text);
        return text;
    }

    @Test
    void incrementalUpdatesFollowTheRealCommits() throws Exception {
        String repo = SamplePaths.SAMPLE_PROJECT_PATH;
        String out = "--out=" + outDir;
        String project = "--project=sample";

        cli(repo, V13, project, out);

        String v14 = cli(repo, V14, "--previous=" + V13, project, out);
        assertTrue(v14.contains("Modified methods"), v14);
        assertTrue(v14.contains("TaxCalculator.calculateTax"), v14);

        String v15 = cli(repo, V15, "--previous=" + V14, project, out);
        assertTrue(v15.contains("Added methods"), v15);
        assertTrue(v15.contains("CheckoutService.quote"), v15);

        String v16 = cli(repo, V16, "--previous=" + V15, project, out);
        assertTrue(v16.contains("Removed methods"), v16);
        assertTrue(v16.contains("Calculator.compute"), v16);

        // Executing the V14 suite at V14: only calculateTax tests may fail.
        GeneratedSuiteRunner.Result run =
                new GeneratedSuiteRunner().run(repo, V14, outDir.resolve("sample").resolve(V14));
        Set<String> failingGenerated = run.getFailingTests().stream()
                .filter(n -> n.startsWith("gen_")).collect(Collectors.toSet());
        System.out.println("V14 run: " + run.getRun() + "; failing generated tests: " + failingGenerated);
        assertTrue(failingGenerated.stream().allMatch(n -> n.startsWith("gen_TaxCalculator_calculateTax_")),
                "unexpected failing generated tests: " + failingGenerated);
    }
}
