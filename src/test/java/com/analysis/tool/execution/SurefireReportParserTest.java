package com.analysis.tool.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the H8 Surefire XML reader (GAP-16).
 *
 * These are deliberately fast: they write report files to a temporary
 * directory and parse them, so no Maven build is launched. The robustness
 * requirements (missing directory, malformed report, non-Surefire files)
 * are all covered without touching the real sample project.
 */
public class SurefireReportParserTest {

    private static final String MIXED_REPORT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<testsuite name=\"com.sample.CalculatorTest\" tests=\"4\">\n"
                    + "  <testcase name=\"testAdd\" classname=\"com.sample.CalculatorTest\" time=\"0.01\"/>\n"
                    + "  <testcase name=\"testSubtract\" classname=\"com.sample.CalculatorTest\" time=\"0.02\">\n"
                    + "    <failure message=\"expected: &lt;1&gt; but was: &lt;2&gt;\">trace</failure>\n"
                    + "  </testcase>\n"
                    + "  <testcase name=\"testDivide\" classname=\"com.sample.CalculatorTest\" time=\"0.0\">\n"
                    + "    <error message=\"boom\">trace</error>\n"
                    + "  </testcase>\n"
                    + "  <testcase name=\"testSkipped\" classname=\"com.sample.CalculatorTest\" time=\"0.0\">\n"
                    + "    <skipped/>\n"
                    + "  </testcase>\n"
                    + "</testsuite>\n";

    @TempDir
    Path tempDir;

    @Test
    public void missingDirectoryYieldsNoResults() {
        assertTrue(SurefireReportParser.parse(tempDir.resolve("does-not-exist")).isEmpty(),
                "A missing reports directory must yield an empty list, not an error");
    }

    @Test
    public void nullDirectoryYieldsNoResults() {
        assertTrue(SurefireReportParser.parse(null).isEmpty(),
                "A null reports directory must yield an empty list, not an exception");
    }

    @Test
    public void parsesPassedFailedErroredAndSkippedDistinctly() throws IOException {
        Path reports = Files.createDirectories(tempDir.resolve("surefire-reports"));
        Files.writeString(reports.resolve("TEST-com.sample.CalculatorTest.xml"), MIXED_REPORT,
                StandardCharsets.UTF_8);

        List<TestResult> results = SurefireReportParser.parse(reports);

        assertEquals(4, results.size(), "All four test cases must be parsed");
        assertEquals(TestResult.Status.PASSED, statusOf(results, "testAdd"));
        assertEquals(TestResult.Status.FAILED, statusOf(results, "testSubtract"),
                "A <failure> element is an assertion failure");
        assertEquals(TestResult.Status.ERROR, statusOf(results, "testDivide"),
                "An <error> element is a distinct execution error");
        assertEquals(TestResult.Status.SKIPPED, statusOf(results, "testSkipped"));

        TestResult add = find(results, "testAdd");
        assertEquals("com.sample.CalculatorTest", add.getClassName());
        assertEquals("com.sample.CalculatorTest#testAdd", add.getDisplayName());
        assertEquals(0.01, add.getTimeSeconds(), 0.0001);
    }

    @Test
    public void malformedReportIsSkippedWithoutCrashing() throws IOException {
        Path reports = Files.createDirectories(tempDir.resolve("surefire-reports"));
        Files.writeString(reports.resolve("TEST-broken.xml"), "<testsuite><testcase", // truncated
                StandardCharsets.UTF_8);
        Files.writeString(reports.resolve("TEST-com.sample.AppTest.xml"),
                "<?xml version=\"1.0\"?><testsuite><testcase name=\"shouldAnswerWithTrue\""
                        + " classname=\"com.sample.AppTest\" time=\"0.0\"/></testsuite>",
                StandardCharsets.UTF_8);

        List<TestResult> results = SurefireReportParser.parse(reports);

        assertEquals(1, results.size(), "The malformed report is skipped; the valid one is still read");
        assertEquals("shouldAnswerWithTrue", results.get(0).getMethodName());
    }

    @Test
    public void ignoresFilesThatAreNotSurefireReports() throws IOException {
        Path reports = Files.createDirectories(tempDir.resolve("surefire-reports"));
        Files.writeString(reports.resolve("notes.xml"), MIXED_REPORT, StandardCharsets.UTF_8);

        assertTrue(SurefireReportParser.parse(reports).isEmpty(),
                "Only TEST-*.xml files are Surefire reports");
    }

    @Test
    public void stripsParameterisedArgumentsFromMethodName() throws IOException {
        Path reports = Files.createDirectories(tempDir.resolve("surefire-reports"));
        Files.writeString(reports.resolve("TEST-com.sample.ParamTest.xml"),
                "<?xml version=\"1.0\"?><testsuite>"
                        + "<testcase name=\"testCompute(int, int)[1]\" classname=\"com.sample.ParamTest\" time=\"0.1\"/>"
                        + "</testsuite>",
                StandardCharsets.UTF_8);

        List<TestResult> results = SurefireReportParser.parse(reports);

        assertEquals(1, results.size());
        assertEquals("testCompute", results.get(0).getMethodName(),
                "Parameterised display names must map back to the bare method name");
        assertEquals("com.sample.ParamTest#testCompute", results.get(0).getDisplayName());
    }

    @Test
    public void derivesClassNameFromNameWhenClassnameAttributeIsAbsent() throws IOException {
        Path reports = Files.createDirectories(tempDir.resolve("surefire-reports"));
        Files.writeString(reports.resolve("TEST-legacy.xml"),
                "<?xml version=\"1.0\"?><testsuite>"
                        + "<testcase name=\"check(com.sample.MultiplierTest)\" time=\"0.0\"/>"
                        + "</testsuite>",
                StandardCharsets.UTF_8);

        List<TestResult> results = SurefireReportParser.parse(reports);

        assertEquals(1, results.size());
        assertEquals("com.sample.MultiplierTest", results.get(0).getClassName());
        assertFalse(results.get(0).getClassName().isEmpty());
    }

    private static TestResult find(List<TestResult> results, String method) {
        for (TestResult result : results) {
            if (result.getMethodName().equals(method)) {
                return result;
            }
        }
        throw new AssertionError("No test result for method " + method);
    }

    private static TestResult.Status statusOf(List<TestResult> results, String method) {
        return find(results, method).getStatus();
    }
}
