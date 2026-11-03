package com.analysis.tool.impact;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * <p>It also classifies a changed file into the eight reporting categories
 * (A-H) used by the multi-language repository report: Java production, Java
 * test, build, configuration, other programming languages, documentation,
 * static assets and binary/unknown. That classification is purely for display
 * and for deciding which changes are in the Java testing scope; it never makes
 * the analyser parse a non-Java file as Java (the member-level entry point is
 * still {@link #classify(String)}, whose behaviour is unchanged).
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

    /**
     * Reporting bucket for one changed file (PHASE 3, categories A-H).
     *
     * <p>Only {@link #JAVA_PRODUCTION} and {@link #JAVA_TEST} are in the Java
     * testing scope; every other bucket is reported as detected but outside the
     * scope of this Java-focused system.
     */
    public enum FileCategory {
        /** A. Java production source (.java, not a test root). */
        JAVA_PRODUCTION,
        /** B. Java test source (.java under src/test/java). */
        JAVA_TEST,
        /** C. Build files (pom.xml, Gradle files, Maven wrapper). */
        BUILD,
        /** D. Configuration (.xml, .properties, .yml, .yaml, .json). */
        CONFIGURATION,
        /** E. Other programming languages (.py, .js, .ts, .go, .c, ...). */
        OTHER_LANGUAGE,
        /** F. Documentation (.md, .txt, .rst, LICENSE, ...). */
        DOCUMENTATION,
        /** G. Static assets (.html, .css, images, fonts, ...). */
        STATIC_ASSET,
        /** H. Binary or unknown files. */
        BINARY_OR_UNKNOWN
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

    /** D. Configuration file extensions (without the dot). */
    private static final Set<String> CONFIGURATION_EXTENSIONS =
            Set.of("xml", "properties", "yml", "yaml", "json", "toml", "ini", "cfg");

    /** E. Other programming-language extensions (without the dot). */
    private static final Set<String> OTHER_LANGUAGE_EXTENSIONS = Set.of(
            "py", "js", "jsx", "mjs", "cjs", "ts", "tsx", "go", "cpp", "cc", "cxx", "c", "h", "hpp",
            "cs", "php", "rb", "rs", "kt", "kts", "swift", "scala", "sh", "bash", "ps1", "pl", "lua",
            "dart", "ex", "exs", "clj", "groovy", "sql", "r", "m", "vb", "fs", "hs", "jl", "vue", "svelte");

    /** F. Documentation extensions (without the dot). */
    private static final Set<String> DOCUMENTATION_EXTENSIONS =
            Set.of("md", "markdown", "txt", "rst", "adoc", "asciidoc", "textile", "org");

    /** G. Static-asset extensions (without the dot). */
    private static final Set<String> STATIC_ASSET_EXTENSIONS = Set.of(
            "html", "htm", "css", "scss", "sass", "less", "styl",
            "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "bmp", "tif", "tiff", "avif",
            "woff", "woff2", "ttf", "otf", "eot",
            "mp3", "mp4", "wav", "ogg", "webm", "mov", "avi");

    /** H. Binary extensions (without the dot); anything unrecognised is also binary/unknown. */
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "jar", "war", "ear", "class", "exe", "dll", "so", "dylib", "bin", "o", "a", "lib",
            "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar.sig",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods",
            "pyc", "pyo", "whl", "wasm", "dat", "db", "sqlite", "pdb", "keystore", "jks", "p12");

    /** Documentation-ish files that carry no extension. */
    private static final Set<String> DOCUMENTATION_FILE_NAMES = Set.of(
            "readme", "license", "licence", "notice", "changelog", "changes", "authors",
            "contributors", "copying", "todo", ".gitignore", ".gitattributes", ".editorconfig",
            ".gitkeep", "codeowners");

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

    /**
     * Classifies a changed file into the reporting category it belongs to
     * (PHASE 3, A-H). Purely path/extension based, so it never reads content
     * and never treats a non-Java file as Java.
     *
     * <p>A {@code .java} file outside both standard Maven source roots is
     * reported as Java production (it is Java source) even though the
     * member-level analyser only parses {@code src/main/java} and
     * {@code src/test/java}; see {@link #classify(String)} for that decision.
     */
    public FileCategory categorize(String path) {
        if (path == null || path.trim().isEmpty()) {
            return FileCategory.BINARY_OR_UNKNOWN;
        }
        String normalised = path.replace('\\', '/');

        if (isBuildFile(normalised)) {
            return FileCategory.BUILD;
        }
        if (normalised.endsWith(JAVA_SUFFIX)) {
            return normalised.startsWith(TEST_JAVA)
                    ? FileCategory.JAVA_TEST
                    : FileCategory.JAVA_PRODUCTION;
        }

        String name = fileName(normalised).toLowerCase(Locale.ROOT);
        String extension = extensionOf(name);
        if (extension != null) {
            if (CONFIGURATION_EXTENSIONS.contains(extension)) {
                return FileCategory.CONFIGURATION;
            }
            if (OTHER_LANGUAGE_EXTENSIONS.contains(extension)) {
                return FileCategory.OTHER_LANGUAGE;
            }
            if (DOCUMENTATION_EXTENSIONS.contains(extension)) {
                return FileCategory.DOCUMENTATION;
            }
            if (STATIC_ASSET_EXTENSIONS.contains(extension)) {
                return FileCategory.STATIC_ASSET;
            }
            if (BINARY_EXTENSIONS.contains(extension)) {
                return FileCategory.BINARY_OR_UNKNOWN;
            }
        }
        if (DOCUMENTATION_FILE_NAMES.contains(name)) {
            return FileCategory.DOCUMENTATION;
        }
        return FileCategory.BINARY_OR_UNKNOWN;
    }

    /** True when the change is in the Java testing scope (production or test Java). */
    public boolean isJavaChange(String path) {
        FileCategory category = categorize(path);
        return category == FileCategory.JAVA_PRODUCTION || category == FileCategory.JAVA_TEST;
    }

    /**
     * Counts the given changed files per reporting category. Every category is
     * present in the result (zero when unused), so a report can print a stable
     * list of rows.
     */
    public Map<FileCategory, Integer> countByCategory(Iterable<String> paths) {
        Map<FileCategory, Integer> counts = new EnumMap<>(FileCategory.class);
        for (FileCategory category : FileCategory.values()) {
            counts.put(category, 0);
        }
        if (paths != null) {
            for (String path : paths) {
                FileCategory category = categorize(path);
                counts.put(category, counts.get(category) + 1);
            }
        }
        return counts;
    }

    /** Human-readable label for a reporting category, e.g. "Java production files". */
    public static String displayName(FileCategory category) {
        switch (category) {
            case JAVA_PRODUCTION:
                return "Java production files";
            case JAVA_TEST:
                return "Java test files";
            case BUILD:
                return "Build files";
            case CONFIGURATION:
                return "Configuration files";
            case OTHER_LANGUAGE:
                return "Other language files";
            case DOCUMENTATION:
                return "Documentation files";
            case STATIC_ASSET:
                return "Static assets";
            default:
                return "Binary/unknown files";
        }
    }

    private static String fileName(String normalisedPath) {
        int slash = normalisedPath.lastIndexOf('/');
        return slash < 0 ? normalisedPath : normalisedPath.substring(slash + 1);
    }

    /** The extension without the dot, or null when the name has none. */
    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1);
    }
}
