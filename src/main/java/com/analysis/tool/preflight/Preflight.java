package com.analysis.tool.preflight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * H9 preflight validation (GAP-19).
 *
 * Inspects a target repository, before any change analysis, test selection or
 * execution, and reports whether the project can be analysed and how much
 * trust the result deserves. The checks, taken from the roadmap's H9 entry:
 *
 * <ul>
 *   <li><b>Refuse</b> a repository that is not a Git repository, or whose
 *       {@code .git} is a file rather than a directory (a linked worktree or a
 *       submodule gitdir pointer) - GAP-19. A standard {@code .git} directory
 *       is required.</li>
 *   <li><b>Refuse</b> a multi-module Maven project: the analysis and test
 *       discovery assume a single {@code src/} root.</li>
 *   <li><b>Refuse</b> JUnit 4: only JUnit 5 (junit-jupiter) tests are
 *       discovered.</li>
 *   <li><b>REDUCED</b> confidence for Spring, Mockito, Lombok or reflection
 *       usage, which can hide call edges from static analysis without making
 *       the analysis impossible.</li>
 * </ul>
 *
 * The check is filesystem-based and deliberately independent of the analysis
 * pipeline, so it never parses sources the way the analyser does and cannot
 * mask an unsupported configuration. A missing {@code pom.xml} is not itself a
 * rejection: a plain Git repository of Java sources is still analysable.
 */
public final class Preflight {

    private static final Pattern MODULES = Pattern.compile("<modules\\b");
    private static final Pattern JUNIT4_DEPENDENCY =
            Pattern.compile("<artifactId>\\s*junit\\s*</artifactId>");
    private static final Pattern JUNIT4_VINTAGE = Pattern.compile("junit-vintage");
    private static final Pattern JUNIT4_SOURCE = Pattern.compile(
            "org\\.junit\\.(Test|Before|After|BeforeClass|AfterClass|Ignore|RunWith|runner|Assert)\\b");

    private static final Pattern SPRING = Pattern.compile("org\\.springframework");
    private static final Pattern MOCKITO = Pattern.compile("org\\.mockito");
    private static final Pattern LOMBOK = Pattern.compile("org\\.projectlombok|import\\s+lombok\\.");
    private static final Pattern REFLECTION = Pattern.compile(
            "java\\.lang\\.reflect|Class\\.forName\\(|getDeclaredMethod\\(|getDeclaredField\\("
                    + "|getDeclaredConstructor\\(|\\.getMethod\\(");

    private Preflight() {
    }

    /**
     * @param projectPath the target repository path (never modified)
     * @return a structured result classifying the project as SUPPORTED,
     *         REDUCED or UNSUPPORTED, with actionable reasons
     */
    public static PreflightResult validate(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            return new PreflightResult(List.of("repository path is empty"), List.of());
        }
        return validate(Paths.get(projectPath));
    }

    /**
     * @param projectDir the target repository directory (never modified)
     * @return a structured result classifying the project
     */
    public static PreflightResult validate(Path projectDir) {
        List<String> rejections = new ArrayList<>();
        List<String> reductions = new ArrayList<>();

        if (projectDir == null) {
            return new PreflightResult(List.of("repository path is empty"), List.of());
        }
        if (!Files.exists(projectDir)) {
            return new PreflightResult(
                    List.of("repository path does not exist: " + projectDir), List.of());
        }
        if (!Files.isDirectory(projectDir)) {
            return new PreflightResult(
                    List.of("repository path is not a directory: " + projectDir), List.of());
        }

        // GAP-19: Git metadata must be a standard .git directory.
        Path gitPath = projectDir.resolve(".git");
        if (Files.isRegularFile(gitPath)) {
            return new PreflightResult(List.of(
                    "unsupported Git layout: " + gitPath + " is a file (linked worktree or "
                            + "submodule gitdir pointer); only a standard .git directory is supported"),
                    List.of());
        }
        if (!Files.isDirectory(gitPath)) {
            return new PreflightResult(
                    List.of("not a Git repository: no .git directory under " + projectDir),
                    List.of());
        }

        // Maven layout. The pom is optional: a plain Git repo of Java sources is
        // still analysable, so a missing pom is not a rejection.
        Path pom = projectDir.resolve("pom.xml");
        String pomText = readIfExists(pom);
        if (pomText != null) {
            if (MODULES.matcher(pomText).find()) {
                rejections.add("multi-module Maven project: " + pom
                        + " declares <modules>; only single-module projects are supported");
            }
            if (JUNIT4_DEPENDENCY.matcher(pomText).find()
                    || JUNIT4_VINTAGE.matcher(pomText).find()) {
                rejections.add("JUnit 4 detected: " + pom
                        + " declares a JUnit 4 dependency; only JUnit 5 (junit-jupiter) is supported");
            }
        }

        List<SourceFile> sources = readSources(projectDir);
        for (SourceFile source : sources) {
            if (JUNIT4_SOURCE.matcher(source.text).find()) {
                rejections.add("JUnit 4 detected: " + source.relativePath
                        + " uses org.junit.* annotations; only JUnit 5 (junit-jupiter) is supported");
                break;
            }
        }

        // Reliability reductions, in a fixed order so the report is deterministic.
        addReduction(reductions, "Spring indicators", SPRING, pomText, pom, sources,
                "dependency injection and runtime wiring can hide call edges");
        addReduction(reductions, "Mockito indicators", MOCKITO, pomText, pom, sources,
                "mocked collaborators hide the real call edges");
        addReduction(reductions, "Lombok indicators", LOMBOK, pomText, pom, sources,
                "generated members are invisible to static analysis");
        addReduction(reductions, "reflection indicators", REFLECTION, pomText, pom, sources,
                "dynamic dispatch cannot be resolved statically");

        return new PreflightResult(rejections, reductions);
    }

    private static void addReduction(List<String> reductions, String label, Pattern pattern,
                                     String pomText, Path pom, List<SourceFile> sources, String why) {
        String where = locate(pattern, pomText, pom, sources);
        if (where != null) {
            reductions.add(label + " present (" + where + ") - " + why);
        }
    }

    /** First place (pom.xml or a source file) where a pattern appears, or null. */
    private static String locate(Pattern pattern, String pomText, Path pom, List<SourceFile> sources) {
        if (pomText != null && pattern.matcher(pomText).find()) {
            return pom.toString();
        }
        for (SourceFile source : sources) {
            if (pattern.matcher(source.text).find()) {
                return source.relativePath;
            }
        }
        return null;
    }

    /** Reads every .java under src/main/java and src/test/java, once. */
    private static List<SourceFile> readSources(Path projectDir) {
        List<SourceFile> sources = new ArrayList<>();
        for (String root : new String[] {"src/main/java", "src/test/java"}) {
            Path dir = projectDir.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) walk
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .sorted()::iterator) {
                    String text = readIfExists(file);
                    if (text != null) {
                        sources.add(new SourceFile(
                                projectDir.relativize(file).toString().replace('\\', '/'), text));
                    }
                }
            } catch (IOException e) {
                // An unreadable tree is not a reason to refuse; the analyser will
                // report unparsed files itself. Preflight simply reports nothing.
            }
        }
        return sources;
    }

    private static String readIfExists(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static final class SourceFile {
        private final String relativePath;
        private final String text;

        SourceFile(String relativePath, String text) {
            this.relativePath = relativePath;
            this.text = text;
        }
    }
}
