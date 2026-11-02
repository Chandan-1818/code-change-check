package com.analysis.tool.impact;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Classifies changed repository files so the analysis can decide whether a
 * change can be analysed at member level.
 *
 * Member-level analysis is only meaningful for the two Java source trees
 * ({@code src/main/java} and {@code src/test/java}). Everything else - a Maven
 * or Gradle build file, a resource under {@code src/main/resources}, or any
 * other non-Java file - can change application behaviour in ways the member
 * graph cannot see (a dependency upgrade, a changed property, a new resource).
 * Such a change is therefore reported as an uncertainty trigger so the caller
 * can fall back to running every discovered test.
 *
 * This class is deliberately path-based: it never reads file content and never
 * touches Git, so it is trivially unit-testable.
 */
public class ChangedFileClassifier {

    /** Why a changed file could not be analysed at member level. */
    public enum Category {
        /** A Java file under src/main/java. */
        JAVA_SOURCE,
        /** A Java file under src/test/java. */
        JAVA_TEST,
        /** A build/configuration file such as pom.xml or build.gradle. */
        BUILD,
        /** A file under src/main/resources or src/test/resources. */
        RESOURCE,
        /** Anything else (not Java, not a recognised resource or build file). */
        OTHER
    }

    private static final String MAIN_JAVA = "src/main/java/";
    private static final String TEST_JAVA = "src/test/java/";
    private static final String MAIN_RESOURCES = "src/main/resources/";
    private static final String TEST_RESOURCES = "src/test/resources/";
    private static final String JAVA_SUFFIX = ".java";

    /** Exact file names that, wherever they appear, configure a build. */
    private static final Set<String> BUILD_FILE_NAMES = Set.of(
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            "gradle.properties",
            "mvnw",
            "mvnw.cmd");

    public Category classify(String path) {
        if (path == null || path.trim().isEmpty()) {
            return Category.OTHER;
        }
        String normalised = path.replace('\\', '/');

        if (isBuildFile(normalised)) {
            return Category.BUILD;
        }
        if (normalised.startsWith(MAIN_JAVA) && normalised.endsWith(JAVA_SUFFIX)) {
            return Category.JAVA_SOURCE;
        }
        if (normalised.startsWith(TEST_JAVA) && normalised.endsWith(JAVA_SUFFIX)) {
            return Category.JAVA_TEST;
        }
        if (isResourceFile(normalised)) {
            return Category.RESOURCE;
        }
        return Category.OTHER;
    }

    /** True for Maven/Gradle build and wrapper files, e.g. pom.xml, .mvn/wrapper/maven-wrapper.properties. */
    public boolean isBuildFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        String normalised = path.replace('\\', '/');
        int slash = normalised.lastIndexOf('/');
        String name = slash < 0 ? normalised : normalised.substring(slash + 1);
        return BUILD_FILE_NAMES.contains(name)
                || normalised.startsWith(".mvn/")
                || normalised.contains("/.mvn/");
    }

    /** True for anything under src/main/resources or src/test/resources. */
    public boolean isResourceFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        String normalised = path.replace('\\', '/');
        return normalised.startsWith(MAIN_RESOURCES)
                || normalised.startsWith(TEST_RESOURCES);
    }

    /**
     * True when the file cannot be analysed at member level, so its change is an
     * uncertainty trigger: build/resource files and any other non-Java file.
     */
    public boolean isUnanalysableChange(String path) {
        Category category = classify(path);
        return category != Category.JAVA_SOURCE && category != Category.JAVA_TEST;
    }

    /**
     * Returns the subset of the given changed files that cannot be analysed at
     * member level, preserving encounter order and dropping duplicates.
     */
    public Set<String> unanalysableChanges(List<String> changedFiles) {
        Set<String> result = new LinkedHashSet<>();
        if (changedFiles == null) {
            return result;
        }
        for (String path : changedFiles) {
            if (isUnanalysableChange(path)) {
                result.add(path);
            }
        }
        return result;
    }
}
