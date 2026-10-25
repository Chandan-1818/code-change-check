package com.analysis.tool.testgen;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Phase 8B / task G4: records what the target code actually does for each
 * OracleCase. Target sources plus a generated class (RtsOracleCases) are
 * compiled in a temporary directory with the JDK compiler and run in a
 * separate JVM, TWICE, with a process timeout. A case whose two runs differ is
 * NONDETERMINISTIC. Nothing is written outside the temporary directory, which
 * is deleted afterwards.
 *
 * Version-1 limits: the target's main sources must compile with the JDK alone
 * (no third-party dependencies); every target package is wildcard-imported, so
 * a target class named like a JDK or tool class can clash; a compile error in
 * any source fails the whole capture (the message names the error).
 * The target code is executed locally and is assumed to be trusted.
 */
public class OracleCapture {

    static final String HARNESS_CLASS = "RtsOracleCases";
    static final int CASES_PER_PART = 200;
    private static final String RUNTIME = "com.analysis.tool.testgen.OracleRuntime";

    private final long caseTimeoutMs;
    private final long runTimeoutSeconds;

    public OracleCapture() {
        this(OracleRuntime.DEFAULT_CASE_TIMEOUT_MS, 120);
    }

    public OracleCapture(long caseTimeoutMs, long runTimeoutSeconds) {
        this.caseTimeoutMs = caseTimeoutMs;
        this.runTimeoutSeconds = runTimeoutSeconds;
    }

    /** One outcome per case, in the same order as the cases. */
    public List<OracleOutcome> capture(List<String> targetSources, List<OracleCase> cases)
            throws IOException, InterruptedException {
        if (cases.isEmpty()) {
            return new ArrayList<>();
        }
        Path work = Files.createTempDirectory("oracle-capture-");
        try {
            Path sourceDir = Files.createDirectories(work.resolve("src"));
            Path classesDir = Files.createDirectories(work.resolve("classes"));

            TreeSet<String> packages = new TreeSet<>();
            List<File> files = writeSources(targetSources, sourceDir, packages);
            Path harness = sourceDir.resolve(HARNESS_CLASS + ".java");
            Files.writeString(harness, harnessSource(packages, cases), StandardCharsets.UTF_8);
            files.add(harness.toFile());

            compile(files, classesDir);

            List<OracleOutcome> first = runOnce(classesDir, work, "run1", cases.size());
            List<OracleOutcome> second = runOnce(classesDir, work, "run2", cases.size());
            return merge(first, second);
        } finally {
            deleteRecursively(work);
        }
    }

    static String harnessSource(TreeSet<String> packages, List<OracleCase> cases) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : packages) {
            sb.append("import ").append(pkg).append(".*;\n");
        }
        int parts = (cases.size() + CASES_PER_PART - 1) / CASES_PER_PART;
        sb.append("public class ").append(HARNESS_CLASS).append(" {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        ").append(RUNTIME).append(".init();\n");
        for (int part = 0; part < parts; part++) {
            sb.append("        part").append(part).append("();\n");
        }
        sb.append("        ").append(RUNTIME).append(".finish();\n");
        sb.append("    }\n");
        for (int part = 0; part < parts; part++) {
            sb.append("    private static void part").append(part).append("() {\n");
            int end = Math.min(cases.size(), (part + 1) * CASES_PER_PART);
            for (int i = part * CASES_PER_PART; i < end; i++) {
                OracleCase c = cases.get(i);
                if (c.isStatic()) {
                    sb.append("        ").append(RUNTIME).append(".staticCall(").append(i)
                      .append(", () -> ").append(c.getClassName()).append('.').append(c.getMethodName())
                      .append('(').append(c.renderArguments()).append("));\n");
                } else {
                    sb.append("        ").append(RUNTIME).append(".instance(").append(i)
                      .append(", () -> ").append(c.getReceiverExpression())
                      .append(", r -> r.").append(c.getMethodName())
                      .append('(').append(c.renderArguments()).append("));\n");
                }
            }
            sb.append("    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static List<File> writeSources(List<String> sources, Path sourceDir, TreeSet<String> packages)
            throws IOException {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            CompilationUnit unit = StaticJavaParser.parse(sources.get(i));
            String packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            String fileBase = "Source" + i;
            for (TypeDeclaration<?> type : unit.getTypes()) {
                if (type.isPublic()) {
                    fileBase = type.getNameAsString();
                    break;
                }
            }
            Path directory = packageName.isEmpty()
                    ? sourceDir : sourceDir.resolve(packageName.replace('.', File.separatorChar));
            Files.createDirectories(directory);
            Path file = directory.resolve(fileBase + ".java");
            Files.writeString(file, sources.get(i), StandardCharsets.UTF_8);
            files.add(file.toFile());
            if (!packageName.isEmpty()) {
                packages.add(packageName);
            }
        }
        return files;
    }

    private static void compile(List<File> files, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Oracle capture needs a JDK: no system Java compiler is available");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(files);
            List<String> options = Arrays.asList(
                    "-d", classesDir.toString(),
                    "-classpath", runtimeLocation().toString(),
                    "-proc:none", "-nowarn");
            boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!ok) {
                StringBuilder message = new StringBuilder("Compilation failed:");
                int shown = 0;
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR && shown++ < 10) {
                        String where = d.getSource() == null ? "?" : d.getSource().getName();
                        message.append("\n  ").append(where).append(':').append(d.getLineNumber())
                               .append(": ").append(d.getMessage(Locale.ROOT));
                    }
                }
                throw new IllegalStateException(message.toString());
            }
        }
    }

    private List<OracleOutcome> runOnce(Path classesDir, Path work, String name, int caseCount)
            throws IOException, InterruptedException {
        Path output = work.resolve(name + ".out");
        List<String> command = List.of(
                javaExecutable(),
                "-Xmx256m",
                "-D" + OracleRuntime.TIMEOUT_PROPERTY + "=" + caseTimeoutMs,
                "-cp", classesDir + File.pathSeparator + runtimeLocation(),
                HARNESS_CLASS);
        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectOutput(output.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        if (!process.waitFor(runTimeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
        }
        return parse(Files.readAllLines(output, StandardCharsets.ISO_8859_1), caseCount);
    }

    static List<OracleOutcome> parse(List<String> lines, int caseCount) {
        OracleOutcome[] outcomes = new OracleOutcome[caseCount];
        Arrays.fill(outcomes, new OracleOutcome(OracleOutcome.Status.NOT_RUN, "no result recorded"));
        for (String line : lines) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 2) {
                continue;
            }
            try {
                int index = Integer.parseInt(parts[0].trim());
                OracleOutcome.Status status = OracleOutcome.Status.valueOf(parts[1].trim());
                if (index >= 0 && index < caseCount) {
                    outcomes[index] = new OracleOutcome(status, parts.length == 3 ? parts[2] : "");
                }
            } catch (IllegalArgumentException ignored) {
                // not a protocol line
            }
        }
        return new ArrayList<>(Arrays.asList(outcomes));
    }

    static List<OracleOutcome> merge(List<OracleOutcome> first, List<OracleOutcome> second) {
        List<OracleOutcome> merged = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            OracleOutcome a = first.get(i);
            OracleOutcome b = second.get(i);
            merged.add(a.equals(b) ? a : new OracleOutcome(OracleOutcome.Status.NONDETERMINISTIC,
                    "run 1: " + a + " | run 2: " + b));
        }
        return merged;
    }

    private static String javaExecutable() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path exe = bin.resolve("java.exe");
        return Files.exists(exe) ? exe.toString() : bin.resolve("java").toString();
    }

    /** Directory or jar holding OracleRuntime; put on the compile and run classpath. */
    private static Path runtimeLocation() {
        try {
            return Path.of(OracleRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate OracleRuntime", e);
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
