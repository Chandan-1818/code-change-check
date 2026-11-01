package com.analysis.tool.experiment;

import com.analysis.tool.execution.*;
import com.analysis.tool.impact.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;

import org.w3c.dom.*;

import javax.xml.parsers.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Safety check by fault injection.
 *
 * For each fault, the repository is cloned into a temporary directory, the
 * base revision is checked out, ONE method body is deliberately broken by an
 * exact single-line text replacement, and the change is committed as a
 * "mutant" commit. The tool's own analysis is then run between the base
 * revision and the mutant, and the FULL suite is run on the mutant. The names
 * of the failing tests are read from Surefire's XML reports.
 *
 * Outcomes:
 *   CAUGHT                - at least one test fails and every failing test
 *                           was selected by the tool.
 *   MISSED                - some failing test was NOT selected (a safety gap).
 *   NOT_DETECTED_BY_SUITE - no test fails at all, so the fault is invisible to
 *                           the project's own suite (a test-coverage gap, not
 *                           a selection error).
 *   SKIPPED               - the fault could not be injected at the base
 *                           revision (its file is absent, or its find-text does
 *                           not occur exactly once). It is recorded rather than
 *                           thrown so that one stale fault cannot abort the
 *                           whole experiment, and it never enters a
 *                           precision/recall denominator.
 *
 * Precision here is a conservative proxy: the share of selected tests that
 * actually fail under the fault. A selected test that still passes may be
 * relevant, so this understates true precision. Recall is the share of
 * failing tests that were selected.
 */
public class FaultInjectionRunner {

    public enum Outcome { CAUGHT, MISSED, NOT_DETECTED_BY_SUITE, SKIPPED }

    public static class Fault {
        private final String method;
        private final String mutation;
        private final String file;
        private final String find;
        private final String replace;

        public Fault(String method, String mutation, String file, String find, String replace) {
            this.method = method;
            this.mutation = mutation;
            this.file = file;
            this.find = find;
            this.replace = replace;
        }

        public String getMethod() { return method; }
        public String getMutation() { return mutation; }
        public String getFile() { return file; }
        public String getFind() { return find; }
        public String getReplace() { return replace; }
    }

    public static class FaultResult {
        private final Fault fault;
        private final String mutantCommit;
        private final Set<String> selectedTests;
        private final Set<String> failingTests;
        private final Outcome outcome;
        private final String detail;

        public FaultResult(Fault fault, String mutantCommit, Set<String> selectedTests, Set<String> failingTests) {
            this.fault = fault;
            this.mutantCommit = mutantCommit;
            this.selectedTests = new TreeSet<>(selectedTests);
            this.failingTests = new TreeSet<>(failingTests);
            this.outcome = classify(this.selectedTests, this.failingTests);
            this.detail = null;
        }

        private FaultResult(Fault fault, String detail) {
            this.fault = fault;
            this.mutantCommit = null;
            this.selectedTests = new TreeSet<>();
            this.failingTests = new TreeSet<>();
            this.outcome = Outcome.SKIPPED;
            this.detail = detail;
        }

        /**
         * Records a fault that could not be injected at the checked-out
         * revision, instead of failing the whole run.
         */
        public static FaultResult skipped(Fault fault, String reason) {
            return new FaultResult(fault, reason);
        }

        public Fault getFault() { return fault; }
        public String getMutantCommit() { return mutantCommit; }
        public Set<String> getSelectedTests() { return selectedTests; }
        public Set<String> getFailingTests() { return failingTests; }
        public Outcome getOutcome() { return outcome; }
        /** The reason a SKIPPED fault was not injected; null for every other outcome. */
        public String getDetail() { return detail; }

        public int getCaughtCount() {
            int caught = 0;
            for (String test : failingTests) {
                if (selectedTests.contains(test)) {
                    caught++;
                }
            }
            return caught;
        }
    }

    public static Outcome classify(Set<String> selectedTests, Set<String> failingTests) {
        if (failingTests.isEmpty()) {
            return Outcome.NOT_DETECTED_BY_SUITE;
        }
        return selectedTests.containsAll(failingTests) ? Outcome.CAUGHT : Outcome.MISSED;
    }

    /**
     * Index of the single occurrence of {@code needle} in {@code text}, or -1
     * when it is absent or occurs more than once. A single exact replacement
     * needs exactly one occurrence, so both failures are reported the same way.
     */
    static int singleOccurrence(String text, String needle) {
        if (needle.isEmpty()) {
            return -1;
        }
        int first = text.indexOf(needle);
        if (first < 0 || text.indexOf(needle, first + 1) >= 0) {
            return -1;
        }
        return first;
    }

    /**
     * Explains why {@code fault} cannot be injected into {@code repoRoot}, or
     * returns null when it can. Shared by every fault-injection harness so a
     * fault that no longer applies to the checked-out revision is reported
     * consistently instead of aborting the run.
     */
    static String injectionProblem(Path repoRoot, Fault fault) {
        Path file = repoRoot.resolve(fault.getFile());
        if (!Files.isRegularFile(file)) {
            return "the fault file is not present at the base revision: " + fault.getFile();
        }
        String original;
        try {
            original = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "the fault file could not be read: " + fault.getFile() + " (" + e + ")";
        }
        return singleOccurrence(original, fault.getFind()) < 0
                ? "mutation text is not present exactly once in " + fault.getFile()
                : null;
    }

    public static List<Fault> sampleProjectFaults() {
        String calc = "src/main/java/com/sample/Calculator.java";
        String mult = "src/main/java/com/sample/Multiplier.java";
        String repo = "src/main/java/com/sample/OrderRepository.java";
        String service = "src/main/java/com/sample/OrderService.java";
        return Arrays.asList(
                new Fault("Calculator.add", "a + b -> a - b", calc,
                        "return a + b;", "return a - b;"),
                new Fault("Calculator.subtract", "a - b -> a + b", calc,
                        "int result = a - b;", "int result = a + b;"),
                new Fault("Calculator.compute", "result + 1", calc,
                        "return multiplier.multiply(a, b);", "return multiplier.multiply(a, b) + 1;"),
                new Fault("Calculator.negate", "-a -> a", calc,
                        "return -a;", "return a;"),
                new Fault("Multiplier.multiply", "a * b -> a + b", mult,
                        "int product = a * b;", "int product = a + b;"),
                new Fault("OrderRepository.save", "stores total + 1", repo,
                        "orderTotals.put(orderId, storedTotal);", "orderTotals.put(orderId, storedTotal + 1);"),
                new Fault("OrderRepository.findTotal", "returns total + 1", repo,
                        "return total == null ? 0 : total;", "return total == null ? 0 : total + 1;"),
                new Fault("OrderService.placeOrder", "saves total + 1", service,
                        "repository.save(orderId, total);", "repository.save(orderId, total + 1);"),
                new Fault("OrderService.getOrderTotal", "returns total + 1", service,
                        "return repository.findTotal(orderId);", "return repository.findTotal(orderId) + 1;"));
    }

    public List<FaultResult> run(String repoPath, String baseRevision, List<Fault> faults) throws Exception {
        Path workDir = Files.createTempDirectory("fault-injection-");
        try (Git git = Git.cloneRepository()
                .setURI(new File(repoPath).getAbsoluteFile().toURI().toString())
                .setDirectory(workDir.toFile())
                .call()) {

            TestRunner runner = new TestRunner();
            PersonIdent ident = new PersonIdent("Fault Injector", "fault@example.com");
            List<FaultResult> results = new ArrayList<>();

            for (Fault fault : faults) {
                git.checkout().setName(baseRevision).setForced(true).call();
                deleteRecursively(workDir.resolve("target"));

                Path file = workDir.resolve(fault.getFile());
                String problem = injectionProblem(workDir, fault);
                if (problem != null) {
                    results.add(FaultResult.skipped(fault, problem));
                    continue;
                }
                String original = Files.readString(file, StandardCharsets.UTF_8);
                Files.writeString(file, original.replace(fault.getFind(), fault.getReplace()),
                        StandardCharsets.UTF_8);

                git.add().addFilepattern(fault.getFile()).call();
                RevCommit mutant = git.commit()
                        .setMessage("Fault: " + fault.getMethod() + " (" + fault.getMutation() + ")")
                        .setAuthor(ident)
                        .setCommitter(ident)
                        .setSign(false)
                        .call();
                String mutantId = mutant.getName();

                List<FileMapping> mappings =
                        ExperimentRunner.discoverMappings(workDir.toString(), baseRevision, mutantId);
                TestSelectionReport report = new ChangeImpactAnalyzer()
                        .analyzeTransitively(workDir.toString(), baseRevision, mutantId, mappings);
                Set<String> selected = new TreeSet<>(report.getSelectedTests());

                TestRunResult full = runner.runFullSuite(workDir.toString());
                if (full.getTotalTests() == 0) {
                    throw new IllegalStateException("The full suite did not run for fault " + fault.getMethod());
                }
                Set<String> failing = readFailingTests(workDir.resolve("target").resolve("surefire-reports"));

                results.add(new FaultResult(fault, mutantId, selected, failing));
            }
            return results;
        } finally {
            deleteRecursively(workDir);
        }
    }

    static Set<String> readFailingTests(Path reportsDir) throws Exception {
        Set<String> failing = new TreeSet<>();
        if (!Files.isDirectory(reportsDir)) {
            return failing;
        }
        List<Path> xmlFiles;
        try (Stream<Path> files = Files.list(reportsDir)) {
            xmlFiles = files
                    .filter(p -> p.getFileName().toString().startsWith("TEST-")
                            && p.getFileName().toString().endsWith(".xml"))
                    .collect(Collectors.toList());
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        for (Path xml : xmlFiles) {
            Document doc = factory.newDocumentBuilder().parse(xml.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element testCase = (Element) cases.item(i);
                boolean bad = testCase.getElementsByTagName("failure").getLength() > 0
                        || testCase.getElementsByTagName("error").getLength() > 0;
                if (bad) {
                    String name = testCase.getAttribute("name");
                    int paren = name.indexOf('(');
                    failing.add(paren > 0 ? name.substring(0, paren) : name);
                }
            }
        }
        return failing;
    }

    public static String toMarkdown(List<FaultResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Fault | Mutation | Selected tests | Failing tests (full suite) | Outcome | Precision | Recall |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (FaultResult r : results) {
            sb.append("| ").append(r.getFault().getMethod())
              .append(" | ").append(r.getFault().getMutation())
              .append(" | ").append(join(r.getSelectedTests()))
              .append(" | ").append(join(r.getFailingTests()))
              .append(" | ").append(outcomeText(r))
              .append(" | ").append(percent(r.getCaughtCount(), r.getSelectedTests().size()))
              .append(" | ").append(percent(r.getCaughtCount(), r.getFailingTests().size()))
              .append(" |\n");
        }
        return sb.toString();
    }

    /** SKIPPED rows carry their reason, so the table alone explains the skip. */
    private static String outcomeText(FaultResult result) {
        return result.getDetail() == null
                ? result.getOutcome().name()
                : result.getOutcome().name() + " (" + result.getDetail() + ")";
    }

    public static String summary(List<FaultResult> results) {
        int caught = 0;
        int missed = 0;
        int notDetected = 0;
        int skipped = 0;
        for (FaultResult r : results) {
            switch (r.getOutcome()) {
                case CAUGHT -> caught++;
                case MISSED -> missed++;
                case SKIPPED -> skipped++;
                default -> notDetected++;
            }
        }
        StringBuilder sb = new StringBuilder("Faults injected: " + results.size()
                + "; caught: " + caught + "; missed: " + missed
                + "; not detected by the suite: " + notDetected);
        if (skipped > 0) {
            sb.append("; skipped (not injectable at this revision): ").append(skipped);
        }
        return sb.toString();
    }

    private static String percent(int numerator, int denominator) {
        return denominator == 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", 100.0 * numerator / denominator);
    }

    private static String join(Set<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
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

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: FaultInjectionRunner <repoPath> <baseRevision> [grown]");
            System.exit(1);
        }
        List<FaultResult> results = new FaultInjectionRunner().run(args[0], args[1],
                args.length == 3 && "grown".equals(args[2]) ? GrownSampleFaults.faults() : sampleProjectFaults());
        System.out.print(toMarkdown(results));
        System.out.println();
        System.out.println(summary(results));
        boolean anyMissed = results.stream().anyMatch(r -> r.getOutcome() == Outcome.MISSED);
        System.exit(anyMissed ? 2 : 0);
    }
}
